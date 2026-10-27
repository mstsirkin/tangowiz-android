package org.tangowiz.app

import android.app.Instrumentation
import android.content.Intent
import android.os.*
import org.json.JSONObject
import java.io.File
import java.nio.*
import java.util.concurrent.LinkedBlockingDeque

/** Synthetic watch-input test: real engine/session reset, not physical Wear transport. */
object ManualResetValidation {
 fun run(i:Instrumentation){val ctx=i.targetContext;val store=Store(ctx);val report=JSONObject();val oldSource=store.microphone;val oldAudio=store.audio;val oldTiming=store.timing
  try{
   check(!LiveState.current.listening){"Pause before test"};check(ResourceStore(ctx).current()!=null){"Resources required"}
   val log=File(store.root,"lifecycle.jsonl");val before=if(log.exists())log.readLines().size else 0
   fun events()=if(log.exists())log.readLines().drop(before).map{JSONObject(it)} else emptyList()
   fun await(condition:()->Boolean){val deadline=SystemClock.elapsedRealtime()+120000;while(!condition()){check(SystemClock.elapsedRealtime()<deadline){"Timed out: ${events()}"};Thread.sleep(100)}}
   val activity=i.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
   store.microphone="watch";store.audio=false;store.timing=false
   ctx.startForegroundService(Intent(ctx,RecognitionService::class.java).setAction(RecognitionService.RESUME))
   await{PhoneWatch.engineReady}
   val bytes=File("/data/local/tmp/event.pcm").readBytes();val floats=FloatArray(bytes.size/4);ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats)
   @Suppress("UNCHECKED_CAST") val queue=WatchAudioInput::class.java.getDeclaredField("queue").apply{isAccessible=true}.get(WatchAudioInput) as LinkedBlockingDeque<WatchAudioInput.Block>
   val epoch=WatchAudioInput::class.java.getDeclaredField("generation").apply{isAccessible=true}.getLong(WatchAudioInput)
   var sequence=0L
   fun feed(){for(offset in 0 until minOf(80000,floats.size) step 4000){val pcm=ShortArray(4000){(floats[offset+it]*32768).toInt().coerceIn(-32768,32767).toShort()};check(queue.offer(WatchAudioInput.Block("manual-reset-test",sequence++,pcm,epoch),15,java.util.concurrent.TimeUnit.SECONDS));Thread.sleep(100)}}
   feed();await{LiveState.current.best!=null};Thread.sleep(1000)
   await{store.history.isFile};val history=store.history.readBytes()
   i.runOnMainSync{activity.findViewById<android.widget.Button>(103).performClick()}
   await{events().count{it.optString("event")=="manual_reset_seed"}>=2}
   val retained=events().last{it.optString("event")=="manual_reset_seed"}.getInt("retained_samples");check(retained==16000){"Expected two seconds; got $retained"}
   check(store.history.readBytes().take(history.size)==history.toList()){"Saved history changed"}
   // Exact command retries must not discard the fresh evidence a second time.
   val request=JSONObject().put("kind","reset").put("requestId","manual-reset-test-${System.nanoTime()}").toString().toByteArray()
   PhoneWatch.command(ctx,"synthetic-test-node",request);await{events().count{it.optString("event")=="manual_reset_requested"}==2}
   PhoneWatch.command(ctx,"synthetic-test-node",request);Thread.sleep(1000);check(events().count{it.optString("event")=="manual_reset_requested"}==2)
   feed();await{LiveState.current.best!=null}
   check(events().count{it.optString("event")=="model_loaded"}==1);check(events().count{it.optString("event")=="index_loaded"}==1)
   check(events().none{it.optString("event")=="session_error"});check(LiveState.current.listening)
   report.put("status","passed").put("lookback_samples",retained).put("phone_reset_button",true).put("watch_command_retry_deduplicated",true).put("model_index_loaded_once",true).put("history_preserved",true).put("recognized_after_reset",LiveState.current.best!!.display("orchestra")).put("physical_wear_transport_tested",false)
   ctx.startService(Intent(ctx,RecognitionService::class.java).setAction(RecognitionService.PAUSE));await{!LiveState.current.listening};i.runOnMainSync{activity.finish()}
   File(ctx.filesDir,"manual-reset-validation.json").writeText(report.toString(2));i.finish(0,Bundle().apply{putString("report",report.toString())})
  }catch(e:Throwable){report.put("status","failed").put("error",e.stackTraceToString());File(ctx.filesDir,"manual-reset-validation.json").writeText(report.toString(2));i.finish(1,Bundle().apply{putString("error",e.stackTraceToString())})}finally{ctx.startService(Intent(ctx,RecognitionService::class.java).setAction(RecognitionService.PAUSE));store.microphone=oldSource;store.audio=oldAudio;store.timing=oldTiming}
 }
}
