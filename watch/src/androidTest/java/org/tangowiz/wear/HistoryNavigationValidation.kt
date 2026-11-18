package org.tangowiz.wear

import android.app.*
import android.content.Intent
import android.graphics.Rect
import android.os.*
import android.view.*
import android.widget.*
import org.json.*
import java.io.File

/** Real round-screen gestures and activity back stack; no microphone or phone needed. */
object HistoryNavigationValidation {
 fun run(i:Instrumentation){
  val ctx=i.targetContext;WatchCache.init(ctx)
  val old=JSONObject(WatchCache.state.toString());val oldHistory=WatchCache.history;val connected=WatchCache.connected
  val report=JSONObject();var parent:Activity?=null;var child:Activity?=null;var dialog:AlertDialog?=null;var failure:Throwable?=null
  fun ui(f:()->Unit){var error:Throwable?=null;i.runOnMainSync{try{f()}catch(t:Throwable){error=t}};error?.let{throw it}}
  fun await(name:String,f:()->Boolean){val end=SystemClock.elapsedRealtime()+30000;while(!f()){check(SystemClock.elapsedRealtime()<end){"Timed out: $name"};SystemClock.sleep(100)};i.waitForIdleSync()}
  fun views(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))} else emptyList())
  fun button(a:Activity,description:String)=views(a.window.decorView).filterIsInstance<Button>().first{it.contentDescription?.toString()==description}
  fun swipe(a:Activity,right:Boolean,diagonal:Boolean=false){
   val bounds=Rect();i.uiAutomation.rootInActiveWindow?.getBoundsInScreen(bounds)
   if(bounds.isEmpty)bounds.set(0,0,a.resources.displayMetrics.widthPixels,a.resources.displayMetrics.heightPixels)
   val y=bounds.exactCenterY();val left=bounds.left+8f;val end=bounds.right-15f
   val from=if(right)left else end;val to=if(right)end else bounds.left+20f;val endY=if(diagonal)y+minOf(75f,bounds.height()*.18f) else y;val start=SystemClock.uptimeMillis()
   i.sendPointerSync(MotionEvent.obtain(start,start,MotionEvent.ACTION_DOWN,from,y,0))
   for(n in 1..12){SystemClock.sleep(25);i.sendPointerSync(MotionEvent.obtain(start,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,from+(to-from)*n/12,y+(endY-y)*n/12,0))}
   i.sendPointerSync(MotionEvent.obtain(start,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,to,endY,0));i.waitForIdleSync();SystemClock.sleep(500)
  }
  fun openBySwipe(a:Activity):Activity{
   val monitor=i.addMonitor(WatchHistoryActivity::class.java.name,null,false)
   try{swipe(a,false,true);return (i.waitForMonitorWithTimeout(monitor,10000)?:error("Left swipe did not open history")).also{child=it;await("history focus"){it.hasWindowFocus()}}}finally{i.removeMonitor(monitor)}
  }
  fun closeHistory(a:Activity,backTo:Activity){ui{button(a,"Close history").performClick()};await("original parent resumed"){backTo.hasWindowFocus()};check(!backTo.isFinishing);child=null}
  try{
   val fixture=JSONObject(old.toString()).put("revision",old.optLong("revision")+1000).put("updatedAt",System.currentTimeMillis()).put("microphone","phone").put("listening",true).put("ready",true).put("error","").put("best",JSONObject().put("orchestra","Navigation Orchestra").put("title","Current song").put("percent",90))
   ui{WatchCache.connected=true;WatchCache.accept(fixture);WatchCache.acceptHistory(JSONArray().apply{repeat(50){put(JSONObject().put("time","2026-11-17T12:00:00Z").put("orchestra","History Orchestra").put("title","Previous song $it"))}})}
   parent=i.startActivitySync(Intent(ctx,WatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("controls focus"){parent!!.hasWindowFocus()}
   val monitor=i.addMonitor(WatchHistoryActivity::class.java.name,null,false)
   ui{button(parent!!,"History").performClick()};child=i.waitForMonitorWithTimeout(monitor,10000)?:error("History button did not open child");i.removeMonitor(monitor)
   await("history button focus"){child!!.hasWindowFocus()}
   check(button(child!!,"Refresh history").text.toString()=="↻")
   val close=button(child!!,"Close history");val rect=Rect();ui{check(close.getGlobalVisibleRect(rect))}
   val scroll=views(child!!.window.decorView).filterIsInstance<ScrollView>().first()
   check(rect.top>=scroll.bottom){"History Close is not outside the scrolling list at the bottom"}
   ui{scroll.fullScroll(View.FOCUS_DOWN)};i.waitForIdleSync();ui{check(close.getGlobalVisibleRect(Rect()))}
   closeHistory(child!!,parent!!);report.put("history_button_and_fixed_bottom_close",true).put("refresh_retained",true)
   child=openBySwipe(parent!!)
   ui{views(child!!.window.decorView).filterIsInstance<TextView>().first{it.text.toString().contains("Previous song")}.performClick()};i.waitForIdleSync()
   await("details dialog"){!child!!.hasWindowFocus()}
   swipe(child!!,true);await("details returns to history"){child!!.hasWindowFocus()};check(!child!!.isFinishing)
   report.put("details_swipe_returns_to_history",true)
   swipe(child!!,true);await("history swipe returns to controls"){parent!!.hasWindowFocus()};check(!parent!!.isFinishing);child=null
   report.put("controls_left_swipe_opens_history_right_swipe_returns",true)
   for(kind in listOf("display","interval","confidence")){
    ui{dialog=when(kind){"display"->DisplayPreferences.show(parent!!);"interval"->WatchUpdatePreferences.showInterval(parent!!){};else->WatchUpdatePreferences.showConfidence(parent!!){} }}
    i.waitForIdleSync();ui{val d=dialog!!;val b=if(kind=="display")d.getButton(AlertDialog.BUTTON_POSITIVE) else d.getButton(AlertDialog.BUTTON_NEGATIVE);check(b.text.toString()=="Close");val bounds=Rect();check(b.getGlobalVisibleRect(bounds));check(bounds.top>=0&&bounds.bottom<=parent!!.resources.displayMetrics.heightPixels){"Settings Close falls outside the watch display"};b.performClick()};await("settings close $kind"){parent!!.hasWindowFocus()}
   }
   report.put("all_settings_dialogs_have_visible_close",true)
   for(kind in listOf("display","interval","confidence")){
    ui{dialog=when(kind){"display"->DisplayPreferences.show(parent!!);"interval"->WatchUpdatePreferences.showInterval(parent!!){};else->WatchUpdatePreferences.showConfidence(parent!!){} }}
    i.waitForIdleSync();swipe(parent!!,true);await("settings swipe $kind"){parent!!.hasWindowFocus()}
   }
   report.put("settings_swipes_return_to_controls",true)
   parent!!.let{a->ui{a.finish()}};i.waitForIdleSync()
   parent=i.startActivitySync(Intent(ctx,AmbientDisplayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));await("maximized focus"){parent!!.hasWindowFocus()}
   child=openBySwipe(parent!!);closeHistory(child!!,parent!!)
   check(DisplaySession.enabled(ctx));report.put("history_close_returns_to_original_maximized_activity",true)
   child=openBySwipe(parent!!);swipe(child!!,true);await("maximized resumed after swipe"){parent!!.hasWindowFocus()};check(DisplaySession.enabled(ctx));child=null
   report.put("history_swipe_returns_to_original_maximized_activity",true)
   ui{button(parent!!,"Close ambient display").performClick()};await("ambient close"){parent!!.isFinishing};check(!DisplaySession.enabled(ctx));check(WatchCache.state.optBoolean("listening"))
   report.put("ambient_close_does_not_stop_recognition",true).put("status","passed")
  }catch(t:Throwable){failure=t;report.put("status","failed").put("error",t.stackTraceToString());runCatching{File(ctx.filesDir,"history-navigation-failure.png").outputStream().use{i.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}}
  finally{
   ui{dialog?.dismiss();child?.finish();parent?.finish();DisplaySession.close(ctx)}
   WatchCache::class.java.getDeclaredField("state").apply{isAccessible=true}.set(null,old)
   ctx.getSharedPreferences("watch-cache",0).edit().putString("state",old.toString()).commit();WatchCache.acceptHistory(oldHistory);WatchCache.connected=connected
   File(ctx.filesDir,"history-navigation-validation.json").writeText(report.toString(2));i.finish(if(failure==null)0 else 1,Bundle().apply{putString("report",report.toString())})
  }
 }
}
