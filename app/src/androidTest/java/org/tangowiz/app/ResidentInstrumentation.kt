package org.tangowiz.app
import android.app.*
import android.content.Intent
import android.os.*
import java.io.File
import org.json.*
object ResidentValidation {
 fun run(instrumentation:Instrumentation){val report=JSONObject();try{
  val ctx=instrumentation.targetContext;val log=File(Store(ctx).root,"lifecycle.jsonl");val before=if(log.exists())log.readLines().size else 0
  val activity=instrumentation.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  Store(ctx).audio=true;Store(ctx).timing=true
  fun events()=if(log.exists())log.readLines().drop(before).map{JSONObject(it)} else emptyList()
  fun await(name:String,count:Int=1){val deadline=SystemClock.elapsedRealtime()+90000;while(events().count{it.optString("event")==name}<count){check(SystemClock.elapsedRealtime()<deadline){"Timed out: $name"};SystemClock.sleep(100)}}
  fun notification()=ctx.getSystemService(NotificationManager::class.java).activeNotifications.single{it.id==1}.notification
  fun action(name:String){val a=notification().actions.single{it.title.toString()==name};a.actionIntent.send()}
  ctx.startForegroundService(Intent(ctx,RecognitionService::class.java).setAction(RecognitionService.RESUME));await("index_loaded");await("capture_started");SystemClock.sleep(3500)
  check(notification().flags and Notification.FLAG_ONGOING_EVENT!=0);action("Pause");await("pause_complete")
  check(!LiveState.current.listening&&LiveState.current.resident);check(LiveState.current.error.isEmpty());check(Store(ctx).root.walkTopDown().any{it.extension=="wav"&&it.length()>44}){"No microphone audio captured"}
  val files=Store(ctx).root.walkTopDown().filter{it.isFile&&it.name!="lifecycle.jsonl"}.associate{it.absolutePath to it.length()};SystemClock.sleep(1800)
  check(files.all{(p,n)->File(p).length()==n}){"Files grew while paused"}
  check(events().count{it.optString("event")=="model_loaded"}==1);check(events().count{it.optString("event")=="index_loaded"}==1)
  action("Resume");await("capture_started",2);SystemClock.sleep(2500);action("Pause");await("pause_complete",2)
  check(events().count{it.optString("event")=="model_loaded"}==1);check(events().count{it.optString("event")=="index_loaded"}==1)
  // Quickly queue Resume/Pause/Resume, proving old sessions drain before new ownership.
  action("Resume");ctx.startService(Intent(ctx,RecognitionService::class.java).setAction(RecognitionService.PAUSE));ctx.startForegroundService(Intent(ctx,RecognitionService::class.java).setAction(RecognitionService.RESUME));SystemClock.sleep(3000)
  ctx.startService(Intent(ctx,RecognitionService::class.java).setAction(RecognitionService.PAUSE));await("pause_complete",3)
  check(events().none{it.optString("event")=="session_error"});check(Store(ctx).root.walkTopDown().count{it.extension=="wav"&&it.length()>44}>=2){"Resume did not capture a second session"}
  instrumentation.runOnMainSync{activity.moveTaskToBack(true)};action("Exit");await("engine_closed");SystemClock.sleep(1200)
  check(ctx.getSystemService(NotificationManager::class.java).activeNotifications.none{it.id==1})
  check(LiveState.current.exited);check(activity.isFinishing||activity.isDestroyed)
  report.put("status","passed").put("persistent_notification_actions",true).put("pause_no_file_growth",true).put("model_loads",events().count{it.optString("event")=="model_loaded"}).put("index_loads",events().count{it.optString("event")=="index_loaded"}).put("rapid_action_race",true).put("background_exit_finishes_activity",true).put("events",JSONArray(events()))
  File(ctx.filesDir,"resident-validation.json").writeText(report.toString(2));instrumentation.finish(0,Bundle().apply{putString("report",report.toString())})
 }catch(e:Throwable){report.put("status","failed").put("error",e.stackTraceToString());File(instrumentation.targetContext.filesDir,"resident-validation.json").writeText(report.toString(2));instrumentation.finish(1,Bundle().apply{putString("error",e.stackTraceToString())})}}
}
