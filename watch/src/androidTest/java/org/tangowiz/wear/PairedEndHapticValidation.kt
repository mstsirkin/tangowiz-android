package org.tangowiz.wear
import android.app.*
import android.content.Intent
import android.os.*
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.json.JSONArray
import java.io.File

/** The production listener receives real phone-generated signals over paired GMS. */
object PairedEndHapticValidation {
 fun run(i:Instrumentation){
  val ctx=i.targetContext;val report=JSONObject();var activity:AmbientDisplayActivity?=null;var failure:Throwable?=null
  fun stage(s:String){report.put("stage",s);File(ctx.filesDir,"paired-end-stage.json").writeText(report.toString())}
  fun await(label:String,timeout:Long=45000,p:()->Boolean){val end=SystemClock.elapsedRealtime()+timeout;while(!p()){check(SystemClock.elapsedRealtime()<end){"Timeout: $label"};Thread.sleep(100)}}
  fun ambient()=AmbientDisplayActivity::class.java.getDeclaredField("ambient").apply{isAccessible=true}.getBoolean(activity)
  val log=File(ctx.filesDir,"vibration-delivery.jsonl");val before=if(log.exists())log.readLines().size else 0
  fun received()=if(!log.exists())emptyList() else log.readLines().drop(before).mapNotNull{runCatching{JSONObject(it)}.getOrNull()}
  try{
   i.runOnMainSync{WatchCache.init(ctx)};i.waitForIdleSync()
   check(Tasks.await(Wearable.getNodeClient(ctx).connectedNodes,30,TimeUnit.SECONDS).any{it.isNearby}){"No paired phone"}
   activity=i.startActivitySync(Intent(ctx,AmbientDisplayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AmbientDisplayActivity;i.waitForIdleSync()
   await("real phone settings"){WatchCache.state.optBoolean("watchVibration")}
   stage("waiting_for_sleep");await("OS ambient entry"){ambient()};stage("ambient_ready")
   await("real recording ending delivered to watch",180000){received().any{it.optInt("pulses")==2&&it.optBoolean("requested")}}
   Thread.sleep(1000)
   check(ambient()&&WatchHapticSession.visible()){"Signal woke or hid the ambient app"}
   check(ctx.getSystemService(NotificationManager::class.java).activeNotifications.none{it.id in 301..302})
   val rows=received();check(rows.any{it.optInt("pulses")==1&&it.optBoolean("requested")})
   report.put("status","passed").put("actual_paired_data_layer",true).put("production_listener",true).put("real_phone_identification_and_ending_received",true).put("ambient_preserved",true).put("notification_absent",true).put("receipts",JSONArray(rows))
  }catch(t:Throwable){failure=t;report.put("status","failed").put("error",t.stackTraceToString())}
  finally{
   i.runOnMainSync{DisplaySession.close(ctx);activity?.finish()};i.waitForIdleSync()
   File(ctx.filesDir,"paired-end-validation.json").writeText(report.toString(2))
   i.finish(if(failure==null)0 else 1,Bundle().apply{putString("paired_ending",report.toString())})
  }
 }
}
