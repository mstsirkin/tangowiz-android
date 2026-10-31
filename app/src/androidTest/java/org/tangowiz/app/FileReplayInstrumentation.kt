package org.tangowiz.app

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

/** Test-only file input; executes the application's unmodified recognition service. */
class FileReplayInstrumentation:Instrumentation(){
 private lateinit var args:Bundle
 override fun onCreate(arguments:Bundle?){super.onCreate(arguments);args=arguments?:Bundle();start()}
 override fun onStart(){
  if(args.getString("ui_gate")=="true"){
   try{ConfidenceUiValidation.run(this);finish(0,Bundle().apply{putString("ui_gate","passed")})}catch(t:Throwable){finish(1,Bundle().apply{putString("ui_gate","failed");putString("error",t.stackTraceToString())})}
   return
  }
  val ctx=targetContext;val store=Store(ctx);val report=JSONObject()
  val oldMode=store.adaptiveRecognition;val oldInterval=store.adaptiveInterval;args.getString("mode")?.let{store.adaptiveRecognition=it!="baseline"};if(store.adaptiveRecognition)store.adaptiveInterval=args.getString("interval","2").toDouble()
  val oldMic=store.microphone;val oldAudio=store.audio;val oldTiming=store.timing
  var activity:android.app.Activity?=null
  val samples=JSONArray();var failure:Throwable?=null
  fun await(label:String,timeout:Long,condition:()->Boolean){val end=SystemClock.elapsedRealtime()+timeout;while(!condition()){check(SystemClock.elapsedRealtime()<end){"Timeout: $label"};Thread.sleep(100)}}
  try{
   check(!LiveState.current.listening){"Replay requires an idle isolated application"}
   val bundle=ResourceStore(ctx).current()?:error("Recognition resources unavailable")
   val bytes=File(args.getString("pcm")?:"/data/local/tmp/replay.pcm16").readBytes()
   check(bytes.size>=32000&&bytes.size%8000==0){"Expected complete half-second PCM16 mono8k frames"}
   val pcm=ShortArray(bytes.size/2);ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
   ResourceStore(ctx).automaticChecks=false
   store.microphone="watch";store.audio=false;store.timing=true
   WatchAudioInput.close()
   val before=store.root.listFiles().orEmpty().filter{it.isDirectory}.map{it.name}.toSet()
   activity=startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
   ctx.startForegroundService(Intent(ctx,RecognitionService::class.java).setAction(RecognitionService.RESUME))
   await("resident recognition engine",120000){PhoneWatch.engineReady&&LiveState.current.listening}
   @Suppress("UNCHECKED_CAST")
   val queue=WatchAudioInput::class.java.getDeclaredField("queue").apply{isAccessible=true}.get(WatchAudioInput) as LinkedBlockingDeque<WatchAudioInput.Block>
   val epoch=WatchAudioInput::class.java.getDeclaredField("generation").apply{isAccessible=true}.getLong(WatchAudioInput)
   val frames=pcm.size/4000;val began=SystemClock.elapsedRealtime();var maxLate=0L;var maxQueue=0
   for(frame in 0 until frames){
    val due=began+(frame+1)*500L;val wait=due-SystemClock.elapsedRealtime();if(wait>0)Thread.sleep(wait)
    maxLate=maxOf(maxLate,SystemClock.elapsedRealtime()-due)
    check(LiveState.current.error.isBlank()){"Recognition service: ${LiveState.current.error}"}
    check(queue.offerLast(WatchAudioInput.Block("file-replay",frame.toLong(),pcm.copyOfRange(frame*4000,(frame+1)*4000),epoch))){"Input backlog overflow: cannot sustain playback rate"}
    maxQueue=maxOf(maxQueue,queue.size)
    val live=LiveState.current;val best=live.best;val elapsed=(SystemClock.elapsedRealtime()-began)/1000.0
    val row=JSONObject().put("fed_audio_seconds",(frame+1)*.5).put("wall_seconds",elapsed).put("processed_audio_seconds",live.seconds).put("queued_input_blocks",queue.size).put("listening",live.listening)
    if(best!=null)row.put("title",best.title).put("orchestra",best.orchestra).put("singer",best.singer).put("year",best.year).put("score",best.score).put("speed",best.speed).put("display_percent",DisplayConfidence.percent(best.score,DisplayConfidence.DEFAULT_A,DisplayConfidence.DEFAULT_B))
    samples.put(row)
    if(frame%10==0||frame==frames-1){
     sendStatus(1,Bundle().apply{putString("replay_progress",JSONObject().put("completed_units",(frame+1)*.5).put("total_units",frames*.5).put("backlog_seconds",maxOf(0.0,(frame+1)*.5-live.seconds)).toString())})
     File(ctx.filesDir,"file-replay-live.json").writeText(row.toString())
    }
   }
   val feedWallSeconds=(SystemClock.elapsedRealtime()-began)/1000.0
   val feedFinished=SystemClock.elapsedRealtime()
   // Wait for already captured frames, never append synthetic silence to change policy.
   val end=SystemClock.elapsedRealtime()+45000
   while(LiveState.current.seconds<frames*.5-.6&&SystemClock.elapsedRealtime()<end){check(LiveState.current.error.isBlank());Thread.sleep(100)}
   val final=LiveState.current
   check(final.seconds>=frames*.5-.6){"Audio did not drain within timeout"}
   report.put("status","passed").put("input_audio_seconds",frames*.5).put("processed_audio_seconds",final.seconds).put("feed_wall_seconds",feedWallSeconds).put("post_feed_drain_seconds",(SystemClock.elapsedRealtime()-feedFinished)/1000.0).put("fully_drained",final.seconds>=frames*.5-.6).put("maximum_feed_lateness_ms",maxLate).put("maximum_input_queue_blocks",maxQueue).put("queue_overflow",false).put("replay_samples",samples).put("bundle_version",bundle.version).put("actual_production_service",true).put("adaptive_enabled",store.adaptiveRecognition).put("interval_seconds",if(store.adaptiveRecognition)store.adaptiveInterval else 30.0).put("actual_production_fallback",!store.adaptiveRecognition).put("synthetic_watch_input",true).put("physical_microphone_or_wear_transport",false).put("hardware_performance_claim","physical phone controlled replay")
   if(final.best!=null)report.put("final_title",final.best!!.title).put("final_orchestra",final.best!!.orchestra).put("final_score",final.best!!.score).put("final_speed",final.best!!.speed)
   ctx.startService(Intent(ctx,RecognitionService::class.java).setAction(RecognitionService.PAUSE))
   await("pause",45000){!LiveState.current.listening}
   Thread.sleep(1500)
   val dirs=store.root.listFiles().orEmpty().filter{it.isDirectory&&it.name !in before&&File(it,"session.json").isFile}
   report.put("sessions",JSONArray(dirs.map{it.name}))
   check(dirs.size==1){"Expected one continuous replay session"}
  }catch(t:Throwable){failure=t;report.put("status","failed").put("error",t.stackTraceToString()).put("replay_samples",samples)}
  finally{
   runCatching{ctx.startService(Intent(ctx,RecognitionService::class.java).setAction(RecognitionService.EXIT));await("engine exit",45000){LiveState.current.exited}}
   store.adaptiveRecognition=oldMode;store.adaptiveInterval=oldInterval;store.microphone=oldMic;store.audio=oldAudio;store.timing=oldTiming
   runCatching{activity?.let{runOnMainSync{it.finish()}}}
   File(ctx.filesDir,"file-replay-report.json").writeText(report.toString(2))
   finish(if(failure==null)0 else 1,Bundle().apply{putString("summary",JSONObject().put("status",report.optString("status")).put("input_audio_seconds",report.optDouble("input_audio_seconds")).toString())})
  }
 }
}
