package org.tangowiz.app
import android.app.Instrumentation
import android.content.Intent
import android.os.*
import org.json.JSONObject
import java.io.File
/** Two invocations verify pending setup survives a real target-process restart. */
object SetupResumeValidation {
 fun run(i:Instrumentation,prepare:Boolean){val ctx=i.targetContext;val store=ResourceStore(ctx);val active=File(store.root,"active.json");val activeBackup=File(store.root,"resume-test-active.json");val pending=File(store.root,"pending.json");val pendingBackup=File(store.root,"resume-test-pending.json");val marker=File(ctx.filesDir,"setup-resume-marker.json")
  fun restore(){if(marker.exists()){val m=JSONObject(marker.readText());pending.delete();if(pendingBackup.exists())pendingBackup.renameTo(pending);if(activeBackup.exists())activeBackup.renameTo(active);store.automaticChecks=m.getBoolean("auto");marker.delete()}}
  try{if(prepare){check(store.current()!=null&&!marker.exists()&&!activeBackup.exists());marker.writeText(JSONObject().put("auto",store.automaticChecks).toString());store.automaticChecks=false;check(active.renameTo(activeBackup));if(pending.exists())check(pending.renameTo(pendingBackup));pending.writeText(JSONObject(activeBackup.readText()).put("formatVersion",2).toString());i.finish(0,Bundle().apply{putString("summary","prepared persisted pending setup")});return}
   check(marker.exists()&&activeBackup.exists()&&pending.exists());ResourceProgress.publish(ResourceState());SetupProbe.resumed=null;ctx.startActivity(Intent(ctx,SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK));val until=SystemClock.elapsedRealtime()+45000
   while(SetupProbe.resumed !is SetupActivity||ResourceProgress.current.kind!="update_app"){check(SystemClock.elapsedRealtime()<until){"Persisted request was not resumed: ${ResourceProgress.current}"};SystemClock.sleep(50)}
   check(SetupProbe.created.none{it is MainActivity});val activity=SetupProbe.resumed!!;i.runOnMainSync{activity.finishAndRemoveTask()};i.waitForIdleSync();restore();ResourceProgress.publish(ResourceState());val report=JSONObject().put("status","passed").put("real_process_restart_pending_request_resumed",true).put("unsupported_format_stays_in_setup",true).put("recognition_screen_never_opened",true);File(ctx.filesDir,"setup-resume-validation.json").writeText(report.toString(2));i.finish(0,Bundle().apply{putString("summary",report.toString())})
  }catch(e:Throwable){restore();File(ctx.filesDir,"setup-resume-validation.json").writeText(JSONObject().put("status","failed").put("error",e.stackTraceToString()).toString(2));i.finish(1,Bundle().apply{putString("error",e.stackTraceToString())})}
 }
}
