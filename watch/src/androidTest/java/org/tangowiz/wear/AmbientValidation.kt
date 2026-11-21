package org.tangowiz.wear

import android.app.*
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.wear.ongoing.OngoingActivity
import org.json.JSONObject
import java.io.File

/** Real OS sleep/wake callbacks; incoming names are a marked, restored local UI fixture. */
object AmbientValidation {
 fun run(i:Instrumentation){
  val ctx=i.targetContext;WatchCache.init(ctx)
  val old=JSONObject(WatchCache.state.toString());val connected=WatchCache.connected
  val report=JSONObject().put("real_os_ambient_callbacks",true).put("synthetic_ui_labels",true)
  var activity:AmbientDisplayActivity?=null;var created=false;var failure:Throwable?=null
  fun ui(block:()->Unit){var thrown:Throwable?=null;i.runOnMainSync{try{block()}catch(e:Throwable){thrown=e}};thrown?.let{throw it}}
  fun save(stage:String){report.put("stage",stage);File(ctx.filesDir,"ambient-validation.json").writeText(report.toString(2))}
  fun await(label:String,timeout:Long,predicate:()->Boolean){val end=SystemClock.elapsedRealtime()+timeout;while(!predicate()){check(SystemClock.elapsedRealtime()<end){"Timed out: $label"};SystemClock.sleep(100)}}
  fun field(name:String)=AmbientDisplayActivity::class.java.getDeclaredField(name).apply{isAccessible=true}
  fun ambient()=field("ambient").getBoolean(activity)
  fun name()=(field("orchestra").get(activity) as TextView).text.toString()
  try{
   check(!DisplaySession.enabled(ctx)){"Refusing to replace an already active display session"}
   val fixture=JSONObject(old.toString()).put("revision",old.optLong("revision")+1000).put("updatedAt",System.currentTimeMillis()).put("listening",true).put("microphone","phone").put("error","").put("best",JSONObject().put("orchestra","Ambient test A").put("title","First title").put("percent",75))
   ui{WatchCache.connected=true;WatchCache.accept(fixture)}
   activity=i.startActivitySync(Intent(ctx,AmbientDisplayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AmbientDisplayActivity;created=true;i.waitForIdleSync()
   await("Ongoing display notification",10000){OngoingActivity.recoverOngoingActivity(ctx)!=null}
   check(name()=="Ambient test A")
   save("waiting_for_sleep")
   await("OS ambient entry",45000){ambient()}
   ui{
    check((field("footer").get(activity) as View).visibility==View.INVISIBLE){"Interactive controls visible in ambient"}
    check((field("clock").get(activity) as TextView).text.isNotBlank())
    val before=name()
    check(before.removePrefix("? ")=="Ambient test A"){"Unexpected initial ambient text: $before"}
    val next=JSONObject(fixture.toString()).put("revision",fixture.getLong("revision")+1).put("updatedAt",System.currentTimeMillis()).put("best",JSONObject().put("orchestra","Ambient test B").put("title","Latest title").put("percent",90))
    WatchCache.accept(next)
    check(name()==before){"Push repainted the low-power screen outside an ambient tick"}
   }
   save("ambient_holding")
   SystemClock.sleep(75000)
   check(field("started").getBoolean(activity)&&!activity!!.isFinishing){"System returned away from display during ambient"}
   check(ambient()){"Ambient state ended unexpectedly"}
   report.put("ambient_retention_seconds",75).put("ongoing_activity_metadata",true)
   save("waiting_for_wake")
   await("OS ambient exit",45000){!ambient()}
   i.waitForIdleSync()
   await("Visible latest pushed name",10000){name().removePrefix("? ")=="Ambient test B"&&activity!!.hasWindowFocus()}
   check((field("footer").get(activity) as View).visibility==View.VISIBLE)
   report.put("latest_cache_rendered_on_wake",true)
   save("awake_screenshot")
   SystemClock.sleep(5000)
   ctx.startService(Intent(ctx,WatchDisplayService::class.java).setAction(DisplaySession.STOP))
   await("Close display ends activity and notification",10000){activity!!.isFinishing&&ctx.getSystemService(NotificationManager::class.java).activeNotifications.none{it.id==DisplaySession.NOTIFICATION}}
   check(!DisplaySession.enabled(ctx));check(WatchCache.state.getBoolean("listening")){"Closing display changed recognition state"}
   report.put("explicit_close_stops_display_only",true).put("status","passed")
  }catch(e:Throwable){failure=e;report.put("status","failed").put("error",e.stackTraceToString())}
  finally{
   if(created)runCatching{ui{DisplaySession.close(ctx);activity?.finish()};i.waitForIdleSync()}
   WatchCache::class.java.getDeclaredField("state").apply{isAccessible=true}.set(null,old)
   ctx.getSharedPreferences("watch-cache",0).edit().putString("state",old.toString()).commit()
   WatchCache.connected=connected;WatchCache.changed()
   save("finished")
   i.finish(if(failure==null)0 else 1,Bundle().apply{putString("report",report.toString())})
  }
 }
}
