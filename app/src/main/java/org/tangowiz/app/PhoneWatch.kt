package org.tangowiz.app

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONArray
import org.json.JSONObject
import org.tangowiz.link.WearMessages
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** The phone owns effective settings. Commands are explicit, revisioned and retry-safe. */
object PhoneWatch {
 private lateinit var context:Context
 private val worker=Executors.newSingleThreadExecutor()
 private val network=Executors.newSingleThreadExecutor()
 private val stateQueued=java.util.concurrent.atomic.AtomicBoolean(false)
 private val stateDirty=java.util.concurrent.atomic.AtomicBoolean(false)
 @Volatile private var captureRequested=false
 private val main=Handler(Looper.getMainLooper())
 @Volatile private var initialized=false
 @Volatile var engineReady=false
 @Volatile private var pendingListening:Boolean?=null
 private var lastSent=0L
 private var lastKey=""
 private var lastScheduled=0L
 private var lastStatus=""
 fun init(ctx:Context){synchronized(this){if(initialized)return;context=ctx.applicationContext;initialized=true;LiveState.listeners.add{scheduleState()}}}
 private fun prefs()=context.getSharedPreferences("watch-link",0)
 private fun revision()=prefs().getLong("revision",0)
 private fun candidate(c:Candidate?):Any = c?.let{JSONObject().put("id",it.id).put("orchestra",it.display("orchestra").take(500)).put("title",it.display("title").take(1000)).put("singer",it.display("singer").take(300)).put("year",it.display("year").take(100)).put("score",it.score).put("percent",(it.confidence*2).coerceIn(0,100))}?:JSONObject.NULL
 @Synchronized fun state():JSONObject {
  val store=Store(context);val live=LiveState.current
  val signature="${pendingListening?:live.listening}|${store.microphone}|${store.audio}|${store.timing}|${store.watchUpdateInterval}"
  if(prefs().getString("control_signature","")!=signature)prefs().edit().putString("control_signature",signature).putLong("revision",revision()+1).commit()
  return JSONObject().put("protocol",1).put("revision",revision()).put("listening",pendingListening?:live.listening)
   .put("microphone",store.microphone).put("audio",store.audio).put("timing",store.timing).put("updateIntervalMs",store.watchUpdateInterval)
   .put("phase",if(pendingListening==true&&!live.listening)"Starting phone…" else if(live.listening&&store.microphone=="watch"&&!WatchAudioInput.connected)"Waiting for watch audio" else live.phase)
   .put("ready",engineReady&&live.listening).put("error",live.error).put("best",candidate(live.best)).put("second",candidate(live.second)).put("updatedAt",System.currentTimeMillis())
 }
 fun peerConnected(ctx:Context){init(ctx);captureRequested=false;changed()}
 fun changed(){if(initialized)scheduleState(true)}
 private fun scheduleState(force:Boolean=false){
  if(!initialized)return
  val live=LiveState.current
  if(pendingListening==live.listening||live.exited)pendingListening=null
  val key="${live.listening}|${live.error}|${live.resident}|$engineReady"
  synchronized(this){val now=SystemClock.elapsedRealtime();if(!force&&key==lastKey&&now-lastScheduled<Store(context).watchUpdateInterval)return;lastKey=key;lastScheduled=now}
  stateDirty.set(true)
  if(stateQueued.compareAndSet(false,true))network.execute{try{do{stateDirty.set(false);publishState()}while(stateDirty.get())}finally{stateQueued.set(false);if(stateDirty.get())scheduleState(true)}}
 }
 private fun send(node:String,path:String,value:JSONObject){val bytes=value.toString().toByteArray();require(bytes.size<=90000){"Watch message too large"};Wearable.getMessageClient(context).sendMessage(node,path,bytes)}
 private fun publishState(){
  runCatching{
   val snapshot=state();val now=SystemClock.elapsedRealtime()
   val nodes=Tasks.await(Wearable.getNodeClient(context).connectedNodes,10,TimeUnit.SECONDS)
   for(node in nodes)runCatching{send(node.id,WearMessages.STATE_PATH,snapshot)}
   if(snapshot.optBoolean("ready")&&snapshot.optBoolean("listening")&&snapshot.optString("microphone")=="watch"&&!captureRequested&&nodes.any{it.isNearby}){captureRequested=true;requestCapture(true)}
   // Persist the latest result so a sleeping/reconnecting companion can catch up.
   if(now-lastSent>=Store(context).watchUpdateInterval){lastSent=now;val request=PutDataMapRequest.create(WearMessages.STATE_PATH);request.dataMap.putString("json",snapshot.toString());Wearable.getDataClient(context).putDataItem(request.asPutDataRequest())}
  }
 }
 fun command(ctx:Context,node:String,raw:ByteArray){init(ctx);if(raw.size>16000)return;worker.execute{
  runCatching{
   val request=JSONObject(String(raw,Charsets.UTF_8));val id=request.optString("requestId");require(id.length in 1..100){"Invalid request ID"}
   val kind=request.optString("kind");val signature=java.security.MessageDigest.getInstance("SHA-256").digest(raw).joinToString(""){"%02x".format(it)}
   val cache=JSONObject(prefs().getString("handled","{}")?:"{}");val cached=cache.optJSONObject(id)
   if(cached!=null){require(cached.optString("signature")==signature){"Request ID reused with different command"};send(node,WearMessages.ACK_PATH,cached.getJSONObject("ack"));return@execute}
   var ok=true;var error=""
   try{when(kind){
    "state"->Unit
    "history"->{val rows=RecordingHistory.consecutive(Store(context).history()).takeLast(40);val safe=JSONArray();for(row in rows){safe.put(JSONObject().put("time",row.optString("time")).put("orchestra",Candidate.fromJson(row).display("orchestra").take(500)).put("title",Candidate.fromJson(row).display("title").take(500)).put("singer",Candidate.fromJson(row).display("singer").take(300)).put("year",Candidate.fromJson(row).display("year")).put("confidence_score",row.optInt("confidence_score")))};send(node,WearMessages.HISTORY_PATH,JSONObject().put("rows",safe))}
    "set"->{state();check(request.has("expectedRevision")&&request.getLong("expectedRevision")==revision()){"Settings changed; refresh and try again"};applyValues(request)}
    else->kotlin.error("Unknown watch command")
   }}catch(e:Exception){ok=false;error=e.message?:"Command failed"}
   val ack=JSONObject().put("requestId",id).put("ok",ok).put("state",state());if(error.isNotEmpty())ack.put("error",error)
   if(kind=="set"){cache.put(id,JSONObject().put("signature",signature).put("ack",ack));while(cache.length()>64)cache.remove(cache.keys().next());prefs().edit().putString("handled",cache.toString()).commit()}
   send(node,WearMessages.ACK_PATH,ack);scheduleState(true)
  }.onFailure{e->runCatching{val id=JSONObject(String(raw,Charsets.UTF_8)).optString("requestId");send(node,WearMessages.ACK_PATH,JSONObject().put("requestId",id).put("ok",false).put("error",e.message?:"Command failed").put("state",state()))}}
 }}
 fun setLocal(ctx:Context,values:JSONObject){init(ctx);worker.execute{runCatching{applyValues(values)}.onFailure{e->LiveState.publish(LiveState.current.copy(error=e.message?:"Watch control failed"))};scheduleState(true)}}
 @Synchronized private fun applyValues(values:JSONObject){
  val store=Store(context);val oldSource=store.microphone
  val source=values.optString("microphone",oldSource);require(source in setOf("phone","watch")){"Unknown microphone"}
  val interval=values.optLong("updateIntervalMs",store.watchUpdateInterval);require(interval in 2000L..60000L){"Update interval must be 2–60 seconds"}
  val listening=if(values.has("listening"))values.getBoolean("listening") else pendingListening?:LiveState.current.listening
  val audio=if(values.has("audio"))values.getBoolean("audio") else store.audio
  val timing=if(values.has("timing"))values.getBoolean("timing") else store.timing
  store.microphone=source;store.watchUpdateInterval=interval;store.audio=audio;store.timing=timing
  pendingListening=listening
  prefs().edit().putLong("revision",revision()+1).putString("control_signature","$listening|$source|$audio|$timing|$interval").commit()
  if(!listening||source=="phone"){WatchAudioInput.close();captureRequested=false;network.execute{requestCapture(false)}}
  else if(oldSource!=source)captureRequested=false
  pendingListening=listening
  main.post{
   if(!listening){if(LiveState.current.resident||LiveState.current.listening)context.startService(Intent(context,RecognitionService::class.java).setAction(RecognitionService.PAUSE));else{pendingListening=null;LiveState.publish(LiveState.current.copy(listening=false))}}
   else if(LiveState.current.listening&&!(oldSource!=source&&source=="phone")){if(oldSource!=source)context.startService(Intent(context,RecognitionService::class.java).setAction(RecognitionService.RESTART));else changed()}
   else{
    runCatching{context.startActivity(Intent(context,MainActivity::class.java).setAction("WATCH_START").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))}
     .onFailure{pendingListening=null;LiveState.publish(LiveState.current.copy(error="Open TangoWiz on the phone to start listening"))}
    main.postDelayed({if(pendingListening==true&&!LiveState.current.listening){pendingListening=null;LiveState.publish(LiveState.current.copy(error="Open TangoWiz on the phone to start listening"));offerPhoneStart()}},20000)
   }
   changed()
  }
 }
 private fun requestCapture(capture:Boolean){if(capture!=(Store(context).microphone=="watch"&&(pendingListening?:LiveState.current.listening)))return;runCatching{val nodes=Tasks.await(Wearable.getNodeClient(context).connectedNodes,10,TimeUnit.SECONDS).filter{it.isNearby};if(capture&&nodes.isEmpty()){LiveState.publish(LiveState.current.copy(error="Watch not connected nearby"));return};for(node in nodes)runCatching{send(node.id,WearMessages.WATCH_REQUEST_PATH,JSONObject().put("capture",capture))}}}
 fun watchStatus(ctx:Context,node:String,raw:ByteArray){init(ctx);if(raw.size>8000)return;runCatching{val value=JSONObject(String(raw,Charsets.UTF_8));lastStatus=value.toString();if(value.optString("error").isNotEmpty()&&Store(context).microphone=="watch")LiveState.publish(LiveState.current.copy(error=value.optString("error")));changed()}}
 private fun offerPhoneStart(){
  val manager=context.getSystemService(android.app.NotificationManager::class.java)
  manager.createNotificationChannel(android.app.NotificationChannel("watch-start","Watch controls",android.app.NotificationManager.IMPORTANCE_DEFAULT))
  val intent=Intent(context,MainActivity::class.java).setAction("WATCH_START").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
  val open=android.app.PendingIntent.getActivity(context,7,intent,android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
  runCatching{manager.notify(7,android.app.Notification.Builder(context,"watch-start").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Start TangoWiz").setContentText("Tap to start recognition requested from your watch").setContentIntent(open).setAutoCancel(true).build())}
 }
 fun stopRemoteCapture(){if(initialized){captureRequested=false;network.execute{requestCapture(false)}}}
 fun inputError(message:String){if(initialized)main.post{if(Store(context).microphone=="watch"&&LiveState.current.listening)context.startService(Intent(context,RecognitionService::class.java).setAction(RecognitionService.RESTART).putExtra("automatic_input_restart",true).putExtra("input_error",message))}}
 fun restartInput(){if(initialized)main.post{if(Store(context).microphone=="watch"&&(pendingListening?:LiveState.current.listening))context.startService(Intent(context,RecognitionService::class.java).setAction(RecognitionService.RESTART).putExtra("automatic_input_restart",true))}}
}
