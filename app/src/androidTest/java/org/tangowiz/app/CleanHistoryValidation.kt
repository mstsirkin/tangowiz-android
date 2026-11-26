package org.tangowiz.app

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

object CleanHistoryValidation {
 private fun views(view:View):Sequence<View> = sequence {
  yield(view)
  if(view is ViewGroup) for(i in 0 until view.childCount)yieldAll(views(view.getChildAt(i)))
 }
 private fun await(message:String,predicate:()->Boolean) {
  val deadline=SystemClock.elapsedRealtime()+7000
  while(!predicate()){
   check(SystemClock.elapsedRealtime()<deadline){message}
   SystemClock.sleep(80)
  }
 }
 private fun clickMenu(i:Instrumentation):Boolean {
  fun click(node:AccessibilityNodeInfo?):Boolean {
   if(node==null)return false
   if(node.text?.toString()=="Clean History"){
    val bounds=Rect()
    node.getBoundsInScreen(bounds)
    val time=SystemClock.uptimeMillis()
    for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){
     val event=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0)
     try{check(i.uiAutomation.injectInputEvent(event,true))}finally{event.recycle()}
    }
    return true
   }
   return (0 until node.childCount).any{click(node.getChild(it))}
  }
  val automation=i.uiAutomation
  val info=automation.serviceInfo
  info.flags=info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
  automation.serviceInfo=info
  return click(automation.rootInActiveWindow)||automation.windows.any{click(it.root)}
 }

 fun run(i:Instrumentation){
  val context=i.targetContext
  val store=Store(context)
  val original=store.prefs.all["clean_history_enabled"] as? Boolean
  val backup=if(store.history.isFile)store.history.readBytes() else null
  var activity:MainActivity?=null
  var failure:Throwable?=null
  try {
   check(ResourceStore(context).current()!=null){"Install the test resource bundle first"}
   synchronized(SessionFiles.lock){store.history.delete()}
   store.prefs.edit().remove("clean_history_enabled").commit()
   check(Store(context).cleanHistoryEnabled)
   fun log(id:String,title:String,seconds:Double,confirmed:Boolean=false) {
    val hit=JSONObject().put("id",id).put("groupId","acoustic:$id").put("title",title)
     .put("orchestra","Example Orchestra").put("singer","Example Singer")
     .put("score",.91).put("referenceSeconds",seconds)
    val raw=JSONObject().put("queryOffsetSeconds",seconds+1).put("frames",3)
     .put("candidates",JSONArray().put(hit))
    store.result(Candidate.fromJson(hit).copy(confidence=91),null,seconds,"clean-history-emulator",raw,confirmed)
   }
   log("kept","Sustained tango",0.0)
   log("guess","Brief guess",2.0)
   log("kept","Sustained tango",5.0)
   log("kept","Sustained tango",10.0)
   log("confirmed","Confirmed tango",25.0,true)
   check(store.displayHistory().map{it.getString("title")}==listOf("Sustained tango","Confirmed tango"))
   val originalLog=store.history.readText()
   val screen=i.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
   activity=screen
   fun visible(title:String):Boolean {
    var shown=false
    i.runOnMainSync{shown=views(screen.window.decorView).filterIsInstance<TextView>().any{it.text.toString().contains(title)}}
    return shown
   }
   fun toggle(expected:Boolean){
    i.runOnMainSync{views(screen.window.decorView).first{it.contentDescription=="More options"}.performClick()}
    await("Clean History menu action was unavailable"){clickMenu(i)}
    await("Clean History preference did not change"){Store(context).cleanHistoryEnabled==expected}
   }
   await("Clean rows did not appear"){visible("Confirmed tango")&&visible("Sustained tango")}
   check(!visible("Brief guess"))
   toggle(false)
   await("Disabling cleanup did not restore the brief guess"){visible("Brief guess")}
   toggle(true)
   await("Re-enabling cleanup did not remove the brief guess"){!visible("Brief guess")&&visible("Confirmed tango")}
   check(store.history.readText()==originalLog){"Switch modified the recorded history"}
  }catch(error:Throwable){
   failure=error
  }finally{
   i.runOnMainSync{activity?.finishAndRemoveTask()}
   synchronized(SessionFiles.lock){
    if(backup==null)store.history.delete() else store.history.writeBytes(backup)
    java.io.File(store.root,"clean-history-emulator").deleteRecursively()
   }
   if(original==null)store.prefs.edit().remove("clean_history_enabled").commit()
   else store.cleanHistoryEnabled=original
   LiveState.historyVersion.incrementAndGet()
  }
  if(failure==null)i.finish(0,Bundle().apply{putString("summary","Clean History defaults on, toggles in the menu, and preserves raw history")})
  else i.finish(1,Bundle().apply{putString("error",failure.stackTraceToString())})
 }
}
