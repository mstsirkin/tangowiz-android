package org.tangowiz.app
import android.app.Instrumentation
import android.content.Intent
import android.os.*
import android.widget.*
import org.json.*
import java.io.File
import java.util.Locale

/** Exercises the actual visible Activity listener, rather than only downloader callbacks. */
object DownloadUiValidation {
 fun run(i:Instrumentation){val ctx=i.targetContext;val resources=ResourceStore(ctx);val active=File(resources.root,"active.json");val hidden=File(resources.root,"ui-test-active.json");val offer=File(resources.root,"offer.json");val hiddenOffer=File(resources.root,"ui-test-offer.json");var activity:MainActivity?=null;val rows=JSONArray();val prefs=ctx.getSharedPreferences("settings",0);val auto=resources.automaticChecks;val originalLocale=Locale.getDefault()
  try{
   check(!hidden.exists()){"Prior UI test backup needs recovery"};if(active.exists())check(active.renameTo(hidden));if(offer.exists())check(offer.renameTo(hiddenOffer));resources.automaticChecks=false;ResourceProgress.publish(ResourceState())
   activity=i.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
   val deadline=SystemClock.elapsedRealtime()+45000;while(ResourceProgress.current.kind!="available"){check(SystemClock.elapsedRealtime()<deadline){"Fresh bootstrap failed: ${ResourceProgress.current}"};SystemClock.sleep(100)}
   i.waitForIdleSync();val line=MainActivity::class.java.getDeclaredField("resourceLine").apply{isAccessible=true}.get(activity) as TextView;val button=MainActivity::class.java.getDeclaredField("resourceButton").apply{isAccessible=true}.get(activity) as Button
   i.runOnMainSync{check(activity!!.window.decorView.isShown);check(line.text.toString().startsWith("Download ")&&line.text.toString().contains("GB"));check(button.isShown&&button.isEnabled)}
   val total=1219075307L
   for(locale in listOf(Locale.US,Locale.forLanguageTag("he-IL"))){Locale.setDefault(locale);for(kind in listOf("downloading","verifying"))for(percent in listOf(0,37,100)){
    var thrown:Throwable?=null;var label="";i.runOnMainSync{try{ResourceProgress.publish(ResourceState(kind,"",(total*percent+99)/100,total));label=line.text.toString();check(activity!!.window.decorView.isShown&&!activity!!.isFinishing);check(label.contains("$percent%"));check(!button.isEnabled)}catch(e:Throwable){thrown=e}}
    thrown?.let{throw it};rows.put(JSONObject().put("locale",locale.toLanguageTag()).put("kind",kind).put("percent",percent).put("visible_label",label))
   }
   };Locale.setDefault(originalLocale)
   i.runOnMainSync{activity!!.finishAndRemoveTask()};i.waitForIdleSync()
   if(hidden.exists())check(hidden.renameTo(active));if(hiddenOffer.exists()){offer.delete();check(hiddenOffer.renameTo(offer))};resources.automaticChecks=auto;ResourceProgress.publish(ResourceState())
   val report=JSONObject().put("status","passed").put("fresh_no_resources_bootstrap_ui",true).put("actual_visible_activity_progress",true).put("rows",rows).put("large_archive_downloaded",false).put("microphone_granted",ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)==android.content.pm.PackageManager.PERMISSION_GRANTED).put("notifications_granted",ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)
   File(ctx.filesDir,"download-ui-validation.json").writeText(report.toString(2));i.finish(0,Bundle().apply{putString("summary",report.toString())})
  }catch(e:Throwable){Locale.setDefault(originalLocale);runCatching{i.runOnMainSync{activity?.finishAndRemoveTask()}};if(hidden.exists())hidden.renameTo(active);if(hiddenOffer.exists()){offer.delete();hiddenOffer.renameTo(offer)};resources.automaticChecks=auto;ResourceProgress.publish(ResourceState());val report=JSONObject().put("status","failed").put("rows",rows).put("error",e.stackTraceToString());File(ctx.filesDir,"download-ui-validation.json").writeText(report.toString(2));i.finish(1,Bundle().apply{putString("error",e.stackTraceToString())})}
 }
}
