package org.tangowiz.app
import android.app.*
import android.content.Intent
import android.os.*
import android.view.View
import android.widget.TextView
import org.json.*
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
object SetupProbe {val created=CopyOnWriteArrayList<Activity>();@Volatile var resumed:Activity?=null}
object SetupFlowValidation {
 fun run(i:Instrumentation){val ctx=i.targetContext;val store=ResourceStore(ctx);val active=File(store.root,"active.json");val backup=File(store.root,"setup-flow-active.json");var held:Activity?=null;val checks=JSONArray();val originalAuto=store.automaticChecks
  fun await(test:()->Boolean){val end=SystemClock.elapsedRealtime()+45000;while(!test()){check(SystemClock.elapsedRealtime()<end){"Activity flow timed out; state=${ResourceProgress.current}, resumed=${SetupProbe.resumed}"};SystemClock.sleep(50)};i.waitForIdleSync()}
  fun launch(type:Class<out Activity>,update:Boolean=false){SetupProbe.resumed=null;ctx.startActivity(Intent(ctx,type).putExtra(SetupActivity.UPDATE,update).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))}
  fun close(){held?.let{a->i.runOnMainSync{a.finishAndRemoveTask()}};held=null;i.waitForIdleSync()}
  try{check(store.current()!=null);check(!backup.exists());store.automaticChecks=false;ctx.getSharedPreferences("settings",0).edit().putLong("last_resource_check",0).commit();ResourceProgress.publish(ResourceState())
   launch(SetupActivity::class.java);await{SetupProbe.resumed is MainActivity};held=SetupProbe.resumed;check(!LiveState.current.listening);check(ctx.getSharedPreferences("settings",0).getLong("last_resource_check",0)==0L);checks.put("valid launcher routes Main offline without starting microphone or update check");close()
   check(active.renameTo(backup));ResourceProgress.publish(ResourceState());val beforeMain=SetupProbe.created.count{it is MainActivity};launch(MainActivity::class.java);await{SetupProbe.resumed is SetupActivity};held=SetupProbe.resumed;val guarded=SetupProbe.created.filterIsInstance<MainActivity>().last();check(SetupProbe.created.count{it is MainActivity}>beforeMain&&guarded.isFinishing);check(MainActivity::class.java.getDeclaredField("listen").apply{isAccessible=true}.get(guarded)==null);check(held!!.findViewById<View>(100)==null);checks.put("direct Main entry without resources redirects before constructing recognition controls")
   await{ResourceProgress.current.kind=="available"};i.runOnMainSync{ResourceProgress.publish(ResourceState("verifying","",37,100))};val previous=held;i.runOnMainSync{held!!.recreate()};await{SetupProbe.resumed is SetupActivity&&SetupProbe.resumed!==previous};held=SetupProbe.resumed;val line=SetupActivity::class.java.getDeclaredField("resourceLine").apply{isAccessible=true}.get(held) as TextView;check(line.text.toString()=="Verifying 37%");checks.put("setup recreation retains in-progress UI")
   i.runOnMainSync{ResourceProgress.publish(ResourceState("retry","Network interrupted"))};check(SetupProbe.resumed is SetupActivity);i.runOnMainSync{ResourceProgress.publish(ResourceState("ready","Resources ready"))};check(SetupProbe.resumed is SetupActivity);checks.put("retry and unverified ready status cannot unlock recognition")
   val mainCount=SetupProbe.created.count{it is MainActivity};i.runOnMainSync{held!!.moveTaskToBack(true)};SystemClock.sleep(1200);check(backup.renameTo(active));ResourceProgress.publish(ResourceState("ready","Resources ready"));SystemClock.sleep(500);check(SetupProbe.created.count{it is MainActivity}==mainCount);checks.put("background completion does not foreground Main")
   android.os.ParcelFileDescriptor.AutoCloseInputStream(i.uiAutomation.executeShellCommand("am start -n org.tangowiz.app/.SetupActivity")).use{it.readBytes()};await{SetupProbe.resumed is MainActivity};held=SetupProbe.resumed;check(!LiveState.current.listening);checks.put("reopening after verified activation enters Main without microphone")
   val main=held;main!!.runOnUiThread{main.startActivity(Intent(ctx,SetupActivity::class.java).putExtra(SetupActivity.UPDATE,true))};await{SetupProbe.resumed is SetupActivity};held=SetupProbe.resumed;val oldHash=store.current()!!.archiveHash;i.runOnMainSync{(held as SetupActivity).onBackPressed()};await{SetupProbe.resumed is MainActivity};held=SetupProbe.resumed;check(store.current()!!.archiveHash==oldHash);checks.put("explicit update Back preserves usable Main and installed data")
   val service=ResourceDownloadService();val attach=android.content.ContextWrapper::class.java.getDeclaredMethod("attachBaseContext",android.content.Context::class.java).apply{isAccessible=true};attach.invoke(service,ctx);val make=ResourceDownloadService::class.java.getDeclaredMethod("notification",ResourceState::class.java).apply{isAccessible=true};val notification=make.invoke(service,ResourceState("downloading","Downloading",37,100)) as Notification
   notification.contentIntent.send();await{SetupProbe.resumed is SetupActivity};held=SetupProbe.resumed;check(store.current()!=null);checks.put("resource notification tap opens dedicated setup/progress even with working resources")
   i.runOnMainSync{(held as SetupActivity).onBackPressed()};await{SetupProbe.resumed is MainActivity};held=SetupProbe.resumed;close()
   val report=JSONObject().put("status","passed").put("checks",checks).put("count",checks.length());File(ctx.filesDir,"setup-flow-validation.json").writeText(report.toString(2));store.automaticChecks=originalAuto;i.finish(0,Bundle().apply{putString("summary",report.toString())})
  }catch(e:Throwable){runCatching{close()};if(backup.exists())backup.renameTo(active);store.automaticChecks=originalAuto;val r=JSONObject().put("status","failed").put("checks",checks).put("error",e.stackTraceToString());File(ctx.filesDir,"setup-flow-validation.json").writeText(r.toString(2));i.finish(1,Bundle().apply{putString("error",e.stackTraceToString())})}
 }
}
