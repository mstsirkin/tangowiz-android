package org.tangowiz.wear
import android.app.*
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import org.json.*
/** UI-only fixture; actual phone command handling is tested separately. */
object DjWatchValidation { fun run(i:Instrumentation)=with(i){
 val ctx=targetContext;WatchCache.init(ctx);val old=JSONObject(WatchCache.state.toString());val oldHistory=WatchCache.history;var activity:Activity?=null;var code=0;val result=Bundle()
 try{
  var rev=old.optLong("revision",0)+100
  fun state(available:Boolean,on:Boolean)=JSONObject().put("revision",rev++).put("updatedAt",System.currentTimeMillis()).put("microphone","phone").put("listening",false).put("ready",true).put("djAvailable",available).put("djEnabled",on)
  WatchCache.accept(state(true,false));activity=startActivitySync(Intent(ctx,WatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync()
  fun views(v:View):List<View> =listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList())
  fun dj()=views(activity!!.window.decorView).filterIsInstance<Button>().single{it.text.toString()=="DJ"}
  check(dj().isEnabled&&!dj().isSelected)
  runOnMainSync{WatchCache.accept(state(true,true))};waitForIdleSync();check(dj().isEnabled&&dj().isSelected&&dj().contentDescription.toString().contains("on:"))
  runOnMainSync{WatchCache.accept(state(false,false))};waitForIdleSync();check(!dj().isEnabled&&dj().contentDescription.toString().contains("unavailable"))
  runOnMainSync{WatchCache.acceptHistory(JSONArray().put(JSONObject().put("time","2026-10-30T12:00:00Z").put("orchestra","Fixture").put("title","Song").put("dj",JSONObject().put("enabled",true))))};runOnMainSync{views(activity!!.window.decorView).filterIsInstance<Button>().single{it.contentDescription=="History"}.performClick()};waitForIdleSync();check(views(activity!!.window.decorView).filterIsInstance<TextView>().any{it.text.toString().contains("DJ on")})
  result.putString("checks","watch DJ off/on/unavailable UI and history marker; no paired transport or microphone test")
 }catch(e:Throwable){code=1;result.putString("error",e.stackTraceToString())}finally{
  runOnMainSync{activity?.finishAndRemoveTask()};WatchCache::class.java.getDeclaredField("state").apply{isAccessible=true}.set(null,old);ctx.getSharedPreferences("watch-cache",0).edit().putString("state",old.toString()).commit();WatchCache.acceptHistory(oldHistory);finish(code,result)
 }
}}
