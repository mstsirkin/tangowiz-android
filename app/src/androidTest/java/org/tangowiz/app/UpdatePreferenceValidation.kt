package org.tangowiz.app
import android.app.Instrumentation
import android.content.Intent
import android.os.*
import org.json.JSONObject
import java.io.File
object UpdatePreferenceValidation {
 fun run(i:Instrumentation){val ctx=i.targetContext;val prefs=ctx.getSharedPreferences("settings",0)
  try{check(ResourceStore(ctx).current()!=null);prefs.edit().putBoolean("automatic_update_checks",false).putLong("last_resource_check",0).commit();ResourceProgress.publish(ResourceState())
   val activity=i.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));SystemClock.sleep(2000);check(prefs.getLong("last_resource_check",0)==0L&&ResourceProgress.current.kind=="idle")
   val opened=MainActivity::class.java.getDeclaredField("openedResources").apply{isAccessible=true};check(opened.getBoolean(activity));val before=ResourceStore(ctx).current()!!.archiveHash
   prefs.edit().putBoolean("automatic_update_checks",true).commit();i.runOnMainSync{activity.moveTaskToBack(true)};SystemClock.sleep(1500);i.waitForIdleSync();ctx.startActivity(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
   val until=SystemClock.elapsedRealtime()+45000;while(prefs.getLong("last_resource_check",0)==0L){check(SystemClock.elapsedRealtime()<until){"Same-Activity opted-in check did not run: ${ResourceProgress.current}"};SystemClock.sleep(100)}
   check(!activity.isFinishing&&ResourceStore(ctx).current()!!.archiveHash==before);prefs.edit().putBoolean("automatic_update_checks",false).commit();i.runOnMainSync{activity.finishAndRemoveTask()}
   val report=JSONObject().put("status","passed").put("automatic_off_no_polling",true).put("same_resident_activity_opted_in_due_check",true).put("check_only_no_archive_download",true).put("check_timestamp",prefs.getLong("last_resource_check",0));File(ctx.filesDir,"update-preference-validation.json").writeText(report.toString(2));i.finish(0,Bundle().apply{putString("summary",report.toString())})
  }catch(e:Throwable){prefs.edit().putBoolean("automatic_update_checks",false).commit();File(ctx.filesDir,"update-preference-validation.json").writeText(JSONObject().put("status","failed").put("error",e.stackTraceToString()).toString(2));i.finish(1,Bundle().apply{putString("error",e.stackTraceToString())})}
 }
}
