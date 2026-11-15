package org.tangowiz.wear
import android.app.*
import android.content.Intent
import android.os.*
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Real Activity visibility and OS ambient entry; fixtures do not use paired transport. */
object SoftHapticValidation {
 fun run(i:Instrumentation){
  val ctx=i.targetContext;i.runOnMainSync{WatchCache.init(ctx)};i.waitForIdleSync()
  val original=JSONObject(WatchCache.state.toString());var revision=original.optLong("revision")+1000
  val report=JSONObject();var activity:AmbientDisplayActivity?=null;var failure:Throwable?=null
  fun ui(block:()->Unit){var error:Throwable?=null;i.runOnMainSync{try{block()}catch(t:Throwable){error=t}};error?.let{throw it}}
  fun stage(s:String){report.put("stage",s);File(ctx.filesDir,"soft-haptic-stage.json").writeText(report.toString())}
  fun await(label:String,p:()->Boolean){val end=SystemClock.elapsedRealtime()+45000;while(!p()){check(SystemClock.elapsedRealtime()<end){"Timeout: $label"};Thread.sleep(50)}}
  fun state(enabled:Boolean){ui{WatchCache.accept(JSONObject().put("revision",++revision).put("updatedAt",System.currentTimeMillis()).put("watchVibration",enabled).put("listening",true).put("microphone","phone").put("best",JSONObject().put("orchestra","Haptic test").put("title","Quiet tick").put("percent",95)))}}
  fun event(p:Int)=JSONObject().put("id",UUID.randomUUID().toString()).put("pulses",p).put("sentAt",System.currentTimeMillis())
  fun noSignalNotifications(){check(ctx.getSystemService(NotificationManager::class.java).activeNotifications.none{it.id in 301..302}){"Haptic posted a notification"}}
  fun ambient()=AmbientDisplayActivity::class.java.getDeclaredField("ambient").apply{isAccessible=true}.getBoolean(activity)
  try{
   state(true);check(!WatchHapticSession.visible());check(!WatchVibration.receive(event(1)))
   activity=i.startActivitySync(Intent(ctx,AmbientDisplayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AmbientDisplayActivity;i.waitForIdleSync()
   await("visible application"){WatchHapticSession.visible()}
   state(false);check(!WatchVibration.receive(event(1)));state(true)
   val first=event(1);check(WatchVibration.receive(first));check(!WatchVibration.receive(first));Thread.sleep(300);noSignalNotifications()
   stage("waiting_for_sleep");await("real OS ambient entry"){ambient()}
   check(WatchHapticSession.visible()){"Ambient entry hid the application"}
   val second=event(2);check(WatchVibration.receive(second));Thread.sleep(1000)
   check(ambient()){"Haptic woke the screen"};noSignalNotifications()
   check(!WatchVibration.receive(event(2).put("sentAt",System.currentTimeMillis()-10000)))
   stage("waiting_for_home");await("watch face visibility"){!WatchHapticSession.visible()}
   check(!WatchVibration.receive(event(1)))
   report.put("status","passed").put("real_os_ambient",true).put("awake_single_and_ambient_double",true).put("notification_absent",true).put("ambient_not_woken",true).put("watch_face_suppressed",true)
  }catch(t:Throwable){failure=t;report.put("status","failed").put("error",t.stackTraceToString())}
  finally{
   runCatching{ui{DisplaySession.close(ctx);activity?.finish()};i.waitForIdleSync()}
   ui{WatchCache.accept(JSONObject(original.toString()).put("revision",++revision).put("updatedAt",System.currentTimeMillis()))}
   File(ctx.filesDir,"soft-haptic-validation.json").writeText(report.toString(2))
   i.finish(if(failure==null)0 else 1,Bundle().apply{putString("soft_haptics",report.toString())})
  }
 }
}
