package org.tangowiz.wear

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import org.json.JSONObject
import org.tangowiz.link.AudioWire
import org.tangowiz.link.WearMessages
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Actual paired GMS transport; synthetic music is explicitly injected on the watch. */
class PairedWearValidation:Instrumentation(){
 private var args=Bundle()
 override fun onCreate(arguments:Bundle?){super.onCreate(arguments);args=arguments?:Bundle();start()}
 private fun <T> waitTask(task:Task<T>):T=Tasks.await(task,30,TimeUnit.SECONDS)
 override fun onStart(){
  val ctx=targetContext
  val report=JSONObject().put("actual_paired_data_layer",true).put("physical_microphone_quality_tested",false)
  val queue=LinkedBlockingQueue<Pair<String,JSONObject>>()
  val listener=MessageClient.OnMessageReceivedListener{e->runCatching{queue.offer(e.path to JSONObject(String(e.data,Charsets.UTF_8)))}}
  val client=Wearable.getMessageClient(ctx)
  var channel:ChannelClient.Channel?=null
  var original:JSONObject?=null
  var mutated=false
  var node=""
  var failure:Throwable?=null
  fun save(stage:String){report.put("stage",stage);File(ctx.filesDir,"paired-validation.json").writeText(report.toString(2))}
  fun command(kind:String,values:JSONObject?=null,revision:Long?=null,requestId:String=UUID.randomUUID().toString()):JSONObject{
   val request=JSONObject().put("requestId",requestId).put("kind",kind)
   if(revision!=null)request.put("expectedRevision",revision)
   values?.keys()?.forEach{request.put(it,values.get(it))}
   waitTask(client.sendMessage(node,WearMessages.COMMAND_PATH,request.toString().toByteArray()))
   val deadline=SystemClock.elapsedRealtime()+45000
   while(SystemClock.elapsedRealtime()<deadline){
    val item=queue.poll(1,TimeUnit.SECONDS)?:continue
    if(item.first==WearMessages.ACK_PATH&&item.second.optString("requestId")==requestId)return item.second
   }
   error("No command ACK for $kind")
  }
  fun state():JSONObject{val ack=command("state");check(ack.getBoolean("ok")){ack.toString()};return ack.getJSONObject("state")}
  fun set(values:JSONObject):JSONObject{val ack=command("set",values,state().getLong("revision"));check(ack.getBoolean("ok")){ack.toString()};return ack.getJSONObject("state")}
  try{
   waitTask(client.addListener(listener))
   val nodes=waitTask(Wearable.getNodeClient(ctx).connectedNodes)
   node=nodes.single{it.isNearby}.id
   report.put("nearby_phone_node",node)
   original=state();report.put("initial_state",original)
   check(!original!!.optBoolean("listening")){"Refusing to interrupt an already active test-phone session"}
   save("connected")
   val values=JSONObject().put("listening",false).put("microphone","phone").put("audio",true).put("timing",true).put("updateIntervalMs",2000)
   val before=state().getLong("revision");val id=UUID.randomUUID().toString()
   val first=command("set",values,before,id);check(first.getBoolean("ok"))
   mutated=true
   val retry=command("set",values,before,id);check(retry.toString()==first.toString()){"Retry changed ACK"}
   val stale=command("set",JSONObject().put("timing",false),before-1);check(!stale.getBoolean("ok"))
   report.put("retry_and_stale_revision_checks","passed")
   set(JSONObject().put("microphone","watch").put("listening",true))
   val readyDeadline=SystemClock.elapsedRealtime()+180000
   var current=state()
   while(!(current.optBoolean("ready")&&current.optBoolean("listening")&&current.optString("microphone")=="watch")){
    check(SystemClock.elapsedRealtime()<readyDeadline){"Phone did not become ready: $current"};SystemClock.sleep(1000);current=state()
   }
   save("phone_ready")
   if(args.getString("mode")=="microphone"){
    val deadline=SystemClock.elapsedRealtime()+60000
    while(!WatchCache.capturing){check(SystemClock.elapsedRealtime()<deadline){"Watch microphone did not start: ${WatchCache.state}"};SystemClock.sleep(500)}
    save("microphone_capturing")
    SystemClock.sleep(20000)
    check(WatchCache.capturing){"Watch microphone stopped during capture"}
    report.put("watch_audio_record_capture","passed_20_seconds")
   }else{
    val bytes=File("/data/local/tmp/event.pcm").readBytes();check(bytes.size>=320000)
    val pcm=FloatArray(80000);ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(pcm)
    val wire=Wearable.getChannelClient(ctx)
    channel=waitTask(wire.openChannel(node,WearMessages.AUDIO_PATH))
    val stream=waitTask(wire.getOutputStream(channel!!));val uuid=UUID.randomUUID().toString()
    AudioWire.writeHeader(stream,uuid);stream.flush();report.put("stream_id",uuid)
    var matched=false;var sequence=0L
    while(sequence<50&&!matched){
     val start=(sequence*4000).toInt()
     val block=ShortArray(4000){i->if(start+i<pcm.size)(pcm[start+i]*32768f).roundToInt().coerceIn(-32768,32767).toShort() else 0}
     AudioWire.writeFrame(stream,sequence++,block);stream.flush()
     // Deliberately slow fixture delivery: this checks transport/correctness, not emulator speed.
     SystemClock.sleep(2000)
     current=state()
     matched=current.optJSONObject("best")?.optString("id")=="palazzo-f57cf688973fbd86315e1d87"
    }
    check(matched){"Expected Tanturi reference not returned through real watch channel: $current"}
    report.put("recognized_state",current).put("frames_sent",sequence)
    val cacheDeadline=SystemClock.elapsedRealtime()+30000
    while(!WatchCache.orchestra().contains("Tanturi",true)){check(SystemClock.elapsedRealtime()<cacheDeadline){"Production watch cache did not receive Tanturi push"};SystemClock.sleep(500)}
    report.put("production_watch_cache_orchestra",WatchCache.orchestra()).put("production_watch_cache_song",WatchCache.song())
    save("recognized_hold_for_face")
    // Allow host to capture the real WFF face while the result is still live.
    SystemClock.sleep(20000)
   }
   set(JSONObject().put("listening",false))
   report.put("status","passed")
  }catch(e:Throwable){failure=e;report.put("status","failed").put("error",e.stackTraceToString())}
  finally{
   runCatching{channel?.let{waitTask(Wearable.getChannelClient(ctx).close(it))}}
   if(mutated&&original!=null&&node.isNotBlank())runCatching{
    set(JSONObject().put("listening",false).put("microphone",original!!.getString("microphone")).put("audio",original!!.getBoolean("audio")).put("timing",original!!.getBoolean("timing")).put("updateIntervalMs",original!!.getLong("updateIntervalMs")))
   }.onFailure{report.put("restore_error",it.toString());if(failure==null){failure=it;report.put("status","failed")}}
   runCatching{waitTask(client.removeListener(listener))}
   save("finished")
   finish(if(failure==null)0 else 1,Bundle().apply{putString("report",report.toString())})
  }
 }
}
