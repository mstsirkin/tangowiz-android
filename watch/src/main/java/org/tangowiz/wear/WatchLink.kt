package org.tangowiz.wear

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import org.json.JSONArray
import org.json.JSONObject
import org.tangowiz.link.WearMessages
import org.tangowiz.link.WearProtocol
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.CopyOnWriteArrayList

class WatchApp:Application(){override fun onCreate(){super.onCreate();WatchCache.init(this)}}
object WatchCache {
 lateinit var context:Context;private set
 @Volatile var state=JSONObject();private set
 @Volatile var protocolCompatible=true;private set
 @Volatile var history=JSONArray();private set
 @Volatile var pending="";@Volatile var error="";@Volatile var connected=false;@Volatile var capturing=false
 val listeners=CopyOnWriteArrayList<()->Unit>();private var faceText="";private var faceRequestedAt=0L
 fun init(ctx:Context){if(::context.isInitialized)return;context=ctx.applicationContext;val prefs=context.getSharedPreferences("watch-cache",0);state=runCatching{JSONObject(prefs.getString("state","{}")!!)}.getOrDefault(JSONObject());history=runCatching{JSONArray(prefs.getString("history","[]")!!)}.getOrDefault(JSONArray())}
 @Synchronized fun accept(value:JSONObject){if(value.optLong("revision",-1)<state.optLong("revision",-1)||(value.optLong("revision",-1)==state.optLong("revision",-1)&&value.optLong("updatedAt",0)<state.optLong("updatedAt",0)))return;val versions=value.optJSONArray("supportedProtocols")?.let{a->if(a.length() !in 1..16)intArrayOf() else IntArray(a.length()){a.optInt(it,-1)}};if(!WearProtocol.compatible(value.optInt("protocol",1),versions)){protocolCompatible=false;error="Update watch or phone: incompatible communication protocol";context.stopService(Intent(context,WatchMicService::class.java));changed();return};if(!protocolCompatible&&error.startsWith("Update watch or phone:"))error="";protocolCompatible=true;state=JSONObject(value.toString());context.getSharedPreferences("watch-cache",0).edit().putString("state",state.toString()).apply();changed();if(!state.optBoolean("listening")||state.optString("microphone")!="watch"){if(error.startsWith("Watch audio channel"))error="";context.stopService(Intent(context,WatchMicService::class.java))}}
 var pendingCapture:Boolean get()=context.getSharedPreferences("watch-cache",0).getBoolean("pending_capture",false);set(value){context.getSharedPreferences("watch-cache",0).edit().putBoolean("pending_capture",value).apply()}
 fun captureNotice(){val manager=context.getSystemService(NotificationManager::class.java);manager.createNotificationChannel(NotificationChannel("watch-actions","Watch actions",NotificationManager.IMPORTANCE_DEFAULT));val open=PendingIntent.getActivity(context,11,Intent(context,WatchActivity::class.java).putExtra("requestCapture",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT);manager.notify(11,Notification.Builder(context,"watch-actions").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("TangoWiz watch microphone").setContentText("Tap to allow and start watch capture").setContentIntent(open).setAutoCancel(true).build())}
 fun stale()=!protocolCompatible||!connected||System.currentTimeMillis()-state.optLong("updatedAt",0)>maxOf(30000,state.optLong("updateIntervalMs",5000)*3)
 fun keyword():OrchestraKeywords.Hint?{if(!protocolCompatible)return null;val caps=state.optJSONArray("capabilities")?:return null;if(!(0 until caps.length()).any{caps.optString(it)==WearProtocol.ORCHESTRA_KEYWORD})return null;return OrchestraKeywords.fromMessage(state.optJSONObject("best")?.optJSONObject("orchestraKeyword"))}
 fun orchestra()=state.optJSONObject("best")?.optString("orchestra")?.ifBlank{"?"}?:"—"
 fun song()=state.optJSONObject("best")?.optString("title")?:""
 @Synchronized fun changed(){listeners.forEach{it()};runCatching{TileService.getUpdater(context).requestUpdate(TangoTileService::class.java)};val next="${orchestra()}\n${song()}\n${stale()}";val now=System.currentTimeMillis();if(next!=faceText||now-faceRequestedAt>=300000){faceText=next;faceRequestedAt=now;runCatching{ComplicationDataSourceUpdateRequester.create(context,ComponentName(context,TangoComplicationService::class.java)).requestUpdateAll()}}}
 fun acceptHistory(value:JSONArray){history=value;context.getSharedPreferences("watch-cache",0).edit().putString("history",value.toString()).apply();changed()}
}
object WatchLink {
 private val executor=ScheduledThreadPoolExecutor(2);private val outstanding=ConcurrentHashMap<String,Pending>();private val generation=java.util.concurrent.atomic.AtomicLong();private val desired=ConcurrentHashMap<String,Any>()
 private data class Pending(val json:JSONObject,val callback:(JSONObject)->Unit,var attempts:Int=0)
 fun nearbyNode():Node?=Tasks.await(Wearable.getNodeClient(WatchCache.context).connectedNodes,8,TimeUnit.SECONDS).firstOrNull{it.isNearby}
 fun refresh(done:(Boolean)->Unit={}){send(JSONObject().put("kind","state")){ack->done(ack.optBoolean("ok"))}}
 fun reset(){if(!WatchCache.state.optBoolean("listening"))return;WatchCache.pending="Resetting…";WatchCache.error="";WatchCache.changed();send(JSONObject().put("kind","reset")){ack->WatchCache.pending="";WatchCache.error=if(ack.optBoolean("ok"))"" else ack.optString("error","Reset failed");WatchCache.changed()}}
 fun history(){send(JSONObject().put("kind","history")) {}}
 fun set(changes:JSONObject){changes.keys().forEach{key->desired[key]=changes.get(key)};val token=generation.incrementAndGet();outstanding.entries.removeIf{it.value.json.optString("kind")=="set"};WatchCache.pending="Waiting for phone…";WatchCache.error="";WatchCache.changed();refresh{ok->if(token!=generation.get())return@refresh;if(!ok){desired.clear();WatchCache.pending="";WatchCache.changed();return@refresh};val request=JSONObject().also{j->desired.forEach{(key,value)->j.put(key,value)}}.put("kind","set").put("expectedRevision",WatchCache.state.optLong("revision"));send(request){ack->if(token==generation.get()){desired.clear();WatchCache.pending="";WatchCache.error=if(ack.optBoolean("ok"))"" else ack.optString("error","Phone rejected the change");WatchCache.changed()}}}}
 private fun send(json:JSONObject,callback:(JSONObject)->Unit){if(!WatchCache.protocolCompatible&&json.optString("kind")!="state"){callback(JSONObject().put("ok",false).put("error",WatchCache.error));return};json.put("protocol",WearProtocol.VERSION).put("supportedProtocols",JSONArray(listOf(WearProtocol.VERSION))).put("capabilities",JSONArray(listOf(WearProtocol.ORCHESTRA_KEYWORD)));val id=UUID.randomUUID().toString();json.put("requestId",id);val request=Pending(json,callback);outstanding[id]=request;executor.execute{attempt(id,request)}}
 private fun attempt(id:String,p:Pending){if(outstanding[id]!==p)return;if(p.attempts++>=3){outstanding.remove(id);WatchCache.error="Phone did not acknowledge. Reconnect and retry.";WatchCache.connected=false;WatchCache.changed();p.callback(JSONObject().put("ok",false));return};try{val node=nearbyNode()?:throw IllegalStateException("No nearby phone");WatchCache.connected=true;Tasks.await(Wearable.getMessageClient(WatchCache.context).sendMessage(node.id,WearMessages.COMMAND_PATH,p.json.toString().toByteArray()),8,TimeUnit.SECONDS)}catch(e:Exception){WatchCache.error=e.message?:"Phone connection failed";WatchCache.connected=false;WatchCache.changed()};executor.schedule({attempt(id,p)},4,TimeUnit.SECONDS)}
 fun receive(event:MessageEvent){WatchCache.init(WatchCache.context);val value=runCatching{JSONObject(String(event.data,Charsets.UTF_8))}.getOrNull()?:return;WatchCache.connected=true
  when(event.path){WearMessages.ACK_PATH->{if(value.optBoolean("ok"))WatchCache.error="";value.optJSONObject("state")?.let(WatchCache::accept);outstanding.remove(value.optString("requestId"))?.callback?.invoke(value)};WearMessages.STATE_PATH->WatchCache.accept(value);WearMessages.HISTORY_PATH->WatchCache.acceptHistory(value.optJSONArray("rows")?:JSONArray());WearMessages.WATCH_REQUEST_PATH->{if(!value.optBoolean("capture")){WatchCache.pendingCapture=false;WatchCache.context.getSystemService(NotificationManager::class.java).cancel(11);WatchCache.context.stopService(Intent(WatchCache.context,WatchMicService::class.java))}else{WatchCache.pendingCapture=true;runCatching{WatchCache.captureNotice()};runCatching{WatchCache.context.startActivity(Intent(WatchCache.context,WatchActivity::class.java).putExtra("requestCapture",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))}.onFailure{WatchCache.error="Open TangoWiz on the watch to start capture"}}}};WatchCache.changed()
 }
 fun status(capturing:Boolean,error:String="",extra:JSONObject=JSONObject(),valid:()->Boolean={true}){executor.execute{runCatching{if(!valid())return@runCatching;val node=nearbyNode()?:return@runCatching;if(!valid())return@runCatching;val payload=extra.put("capturing",capturing).put("error",error);Tasks.await(Wearable.getMessageClient(WatchCache.context).sendMessage(node.id,WearMessages.WATCH_STATUS_PATH,payload.toString().toByteArray()),8,TimeUnit.SECONDS)}}}
}
class WatchListener:WearableListenerService(){override fun onCreate(){super.onCreate();WatchCache.init(this)};override fun onMessageReceived(event:MessageEvent){WatchLink.receive(event)};override fun onDataChanged(events:DataEventBuffer){for(event in events)if(event.type==DataEvent.TYPE_CHANGED&&event.dataItem.uri.path==WearMessages.STATE_PATH){runCatching{val json=DataMapItem.fromDataItem(event.dataItem).dataMap.getString("json")?:return@runCatching;WatchCache.connected=true;WatchCache.accept(JSONObject(json))}}};override fun onPeerConnected(peer:Node){WatchCache.connected=true;WatchLink.refresh()};override fun onPeerDisconnected(peer:Node){WatchCache.connected=false;WatchCache.error="Phone disconnected";stopService(Intent(this,WatchMicService::class.java));WatchCache.changed()}}
