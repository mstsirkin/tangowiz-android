package org.tangowiz.wear

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.TextView
import org.json.JSONObject
import java.io.File

/** Unpaired emulator UI fixtures; no claim about Bluetooth delivery latency. */
object WatchUpdateValidation {
 fun run(i:Instrumentation) {
  val ctx=i.targetContext;WatchCache.init(ctx)
  val original=JSONObject(WatchCache.state.toString());val connected=WatchCache.connected
  var activity:Activity?=null;var failure:Throwable?=null
  val report=JSONObject().put("synthetic_watch_state",true)
  fun ui(block:()->Unit){var error:Throwable?=null;i.runOnMainSync{try{block()}catch(e:Throwable){error=e}};error?.let{throw it}}
  var revision=original.optLong("revision")+1000
  fun state(title:String,percent:Int,threshold:Int)=JSONObject().put("revision",++revision).put("updatedAt",System.currentTimeMillis())
   .put("microphone","phone").put("listening",false).put("ready",true).put("updateIntervalMs",5000)
   .put("immediateUpdatePercent",threshold).put("best",JSONObject().put("orchestra",title).put("title",title).put("percent",percent))
  fun field(name:String)=AmbientDisplayActivity::class.java.getDeclaredField(name).apply{isAccessible=true}
  fun views(v:View):List<View> =listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
  try {
   ui{WatchCache.connected=true;WatchCache.accept(state("Initial result",90,70))}
   activity=i.startActivitySync(Intent(ctx,WatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));i.waitForIdleSync()
   val labels=views(activity!!.window.decorView).filterIsInstance<TextView>().map{it.text.toString()}
   check(labels.contains("Updates: 5s")&&labels.contains("Immediate: 70%"))
   var command=JSONObject()
   ui{val dialog=WatchUpdatePreferences.showInterval(activity!!){command=it};check(dialog.listView.checkedItemPosition==1);dialog.listView.performItemClick(dialog.listView.getChildAt(0),0,0)}
   check(command.getLong("updateIntervalMs")==2000L)
   ui{val dialog=WatchUpdatePreferences.showConfidence(activity!!){command=it};check(dialog.listView.checkedItemPosition==3);dialog.listView.performItemClick(dialog.listView.getChildAt(0),0,0)}
   check(command.getInt("immediateUpdatePercent")==0)
   report.put("interval_and_confidence_selectors",true)
   i.waitForIdleSync()
   val focusDeadline=SystemClock.elapsedRealtime()+10000
   while(!activity!!.hasWindowFocus()&&SystemClock.elapsedRealtime()<focusDeadline)SystemClock.sleep(100)
   check(activity!!.hasWindowFocus()){ "Watch controls did not regain focus after closing settings" }
   SystemClock.sleep(300)
   // Inject real touch events into the result view, using the production binding.
   val target=views(activity!!.window.decorView).filterIsInstance<OrchestraNameView>().first()
   var updates=0;var resets=0
   ui{ResultTouchControls.bind(target,{updates++},{resets++})}
   fun touch(hold:Long){
    val location=IntArray(2);ui{target.getLocationOnScreen(location)}
    val x=(location[0]+target.width/2).toFloat();val y=(location[1]+target.height/2).toFloat();val down=SystemClock.uptimeMillis()
    i.sendPointerSync(MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,x,y,0));SystemClock.sleep(hold)
    i.sendPointerSync(MotionEvent.obtain(down,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y,0));i.waitForIdleSync()
   }
   touch(60);check(updates==1&&resets==0)
   touch(ViewConfiguration.getLongPressTimeout().toLong()+250);check(updates==1&&resets==1){"Long press also refreshed or failed to reset"}
   report.put("real_tap_updates_and_hold_resets_without_extra_click",true)
   ui{activity!!.finish()};i.waitForIdleSync()
   activity=i.startActivitySync(Intent(ctx,AmbientDisplayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));i.waitForIdleSync()
   fun name()=(field("orchestra").get(activity) as TextView).text.toString().removePrefix("? ")
   ui{field("ambient").setBoolean(activity,true);WatchCache.accept(state("Held below threshold",50,70))}
   check(name()=="Initial result")
   ui{WatchCache.accept(state("Held below threshold",50,25))}
   check(name()=="Held below threshold")
   ui{WatchCache.accept(state("Any result",5,0))}
   check(name()=="Any result")
   ui{WatchCache.accept(state("Held below ninety",80,90))}
   check(name()=="Any result")
   report.put("ambient_render_uses_selected_threshold",true).put("status","passed")
  }catch(e:Throwable){
   runCatching{val screenshot=i.uiAutomation.takeScreenshot();File(ctx.filesDir,"watch-update-failure.png").outputStream().use{screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
   failure=e;report.put("status","failed").put("error",e.stackTraceToString())}
  finally {
   ui{DisplaySession.close(ctx);activity?.finish()}
   WatchCache::class.java.getDeclaredField("state").apply{isAccessible=true}.set(null,original)
   ctx.getSharedPreferences("watch-cache",0).edit().putString("state",original.toString()).commit()
   WatchCache.connected=connected;WatchCache.changed()
   File(ctx.filesDir,"watch-update-validation.json").writeText(report.toString(2))
   i.finish(if(failure==null)0 else 1,Bundle().apply{putString("report",report.toString())})
  }
 }
}
