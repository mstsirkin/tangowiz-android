package org.tangowiz.wear

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import org.json.JSONArray
import org.json.JSONObject
import org.tangowiz.link.SingerCreditReveal
import java.io.File

/** A real round watch UI: singer taps must leave the live orchestra visible and updating. */
object SingerRevealValidation {
 fun run(i:Instrumentation){
  val ctx=i.targetContext;WatchCache.init(ctx)
  val old=JSONObject(WatchCache.state.toString());val connected=WatchCache.connected
  val prefs=ctx.getSharedPreferences("display-names",0);val saved=prefs.all
  var activity:Activity?=null;var ownsDisplay=false;var failure:Throwable?=null
  val checks=JSONArray()
  fun ui(action:()->Unit){var error:Throwable?=null;i.runOnMainSync{try{action()}catch(e:Throwable){error=e}};error?.let{throw it};i.waitForIdleSync()}
  fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))} else emptyList()
  fun singer()=views(activity!!.window.decorView).filterIsInstance<SingerNameView>().single()
  fun orchestra()=views(activity!!.window.decorView).filterIsInstance<OrchestraNameView>().single()
  try{
   check(ctx.resources.configuration.isScreenRound){"Requires round watch emulator"}
   check(!WatchCache.capturing&&!DisplaySession.enabled(ctx)){"Do not interrupt an active session"}
   prefs.edit().putBoolean("show_singer",true).putBoolean("singer_first_name",false).commit()
   var revision=old.optLong("revision")+1000
   fun fixture(name:String,credit:String)=JSONObject().put("protocol",1).put("revision",++revision).put("updatedAt",System.currentTimeMillis()).put("microphone","phone").put("listening",false).put("best",JSONObject().put("id",name).put("orchestra",name).put("singer",credit).put("title","Synthetic display fixture").put("percent",90))
   ui{WatchCache.connected=true;WatchCache.accept(fixture("Juan D’Arienzo","Alberto Echagüe"))}
   for(fullScreen in listOf(false,true)){
    activity=i.startActivitySync(Intent(ctx,if(fullScreen)AmbientDisplayActivity::class.java else WatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    if(fullScreen)ownsDisplay=true
    i.waitForIdleSync()
    ui{activity!!.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
    val deadline=SystemClock.uptimeMillis()+5000
    while(!activity!!.window.decorView.hasWindowFocus()&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(50)
    ui{check(activity!!.window.decorView.hasWindowFocus()){"Live window did not become focused before singer tap"}}
    ui{singer().performClick()}
    ui{
     check(singer().text.toString()=="Alberto Echagüe"){"Tap opened a separate Singer window instead of revealing the name inline"}
     check(activity!!.window.decorView.hasWindowFocus()){"Singer details blocked the live window"}
     check(orchestra().visibility==View.VISIBLE)
    }
    checks.put("${if(fullScreen)"full-screen" else "controls"}: full credit is inline and live window retains focus")
    ui{WatchCache.accept(fixture("Miguel Caló","Alberto Podestá"))}
    ui{check(orchestra().text.toString().contains("Caló"));check(singer().text.toString()=="Podestá")}
    checks.put("new recognition remains visible after singer tap and changed singer clears reveal")
    ui{singer().performClick()};ui{check(singer().text.toString()=="Alberto Podestá")}
    SystemClock.sleep(SingerCreditReveal.DURATION_MS+500)
    ui{check(singer().text.toString()=="Podestá");check(activity!!.window.decorView.hasWindowFocus())}
    checks.put("full singer returns to surname automatically without Back or restarting watch")
    ui{singer().performClick();singer().performClick();check(singer().text.toString()=="Podestá")}
    ui{activity!!.finishAndRemoveTask();if(fullScreen)DisplaySession.close(ctx)}
    activity=null
    ui{WatchCache.accept(fixture("Juan D’Arienzo","Alberto Echagüe"))}
   }
  }catch(e:Throwable){failure=e}
  finally{
   runCatching{ui{activity?.finishAndRemoveTask();if(ownsDisplay)DisplaySession.close(ctx)}}
   prefs.edit().clear().apply{for((key,value) in saved)if(value is Boolean)putBoolean(key,value)}.commit()
   WatchCache::class.java.getDeclaredField("state").apply{isAccessible=true}.set(null,old)
   ctx.getSharedPreferences("watch-cache",0).edit().putString("state",old.toString()).commit();WatchCache.connected=connected
   val report=JSONObject().put("status",if(failure==null)"passed" else "failed").put("checks",checks).put("round_screen",ctx.resources.configuration.isScreenRound)
   failure?.let{report.put("error",it.stackTraceToString())}
   File(ctx.filesDir,"singer-reveal-validation.json").writeText(report.toString(2))
   i.finish(if(failure==null)0 else 1,Bundle().apply{putString("report",report.toString())})
  }
 }
}
