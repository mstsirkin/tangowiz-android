package org.tangowiz.wear

import android.app.AlertDialog
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.view.View
import android.view.ViewGroup
import android.view.PixelCopy
import org.json.JSONArray
import org.json.JSONObject
import org.tangowiz.link.WearProtocol
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Local synthetic display fixtures; no audio capture or phone setting commands. */
object DisplayNamesValidation {
 fun run(i:Instrumentation){
  val ctx=i.targetContext;WatchCache.init(ctx)
  val old=JSONObject(WatchCache.state.toString());val oldConnected=WatchCache.connected
  val preferences=ctx.getSharedPreferences("display-names",0);val oldPreferences=preferences.all
  var controls:WatchActivity?=null;var display:AmbientDisplayActivity?=null;var dialog:AlertDialog?=null
  var changedPreferences=false;var ownDisplay=false
  val checks=JSONArray();var failure:Throwable?=null
  fun ui(action:()->Unit){var error:Throwable?=null;i.runOnMainSync{try{action()}catch(e:Throwable){error=e}};error?.let{throw it};i.waitForIdleSync()}
  fun frame(){
   val committed=CountDownLatch(1)
   i.runOnMainSync{val root=display!!.window.decorView;root.viewTreeObserver.registerFrameCommitCallback{committed.countDown()};root.invalidate()}
   check(committed.await(10,TimeUnit.SECONDS)){"Updated display frame did not commit"}
  }
  fun screenshot(name:String){
   frame()
   val root=display!!.window.decorView;val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
   val copied=CountDownLatch(1);var result=-1
   PixelCopy.request(display!!.window,bitmap,{code->result=code;copied.countDown()},Handler(Looper.getMainLooper()))
   check(copied.await(10,TimeUnit.SECONDS)&&result==PixelCopy.SUCCESS){"Unable to copy the updated display frame"}
   File(ctx.filesDir,"names-$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
  }
  fun views(root:View):List<View> = listOf(root)+if(root is ViewGroup)(0 until root.childCount).flatMap{views(root.getChildAt(it))} else emptyList()
  fun singer()=views(display!!.window.decorView).filterIsInstance<SingerNameView>().single()
  fun orchestra()=views(display!!.window.decorView).filterIsInstance<OrchestraNameView>().single()
  try{
   check(!WatchCache.capturing&&!DisplaySession.enabled(ctx)){"Do not interrupt an active display or capture"}
   changedPreferences=true
   preferences.edit().clear().commit()
   val fixture=JSONObject().put("protocol",1).put("supportedProtocols",JSONArray(listOf(1))).put("capabilities",JSONArray(listOf(WearProtocol.ORCHESTRA_KEYWORD))).put("revision",old.optLong("revision")+1000).put("updatedAt",System.currentTimeMillis()).put("listening",false).put("ready",true).put("microphone","phone").put("best",JSONObject().put("orchestra","Miguel Caló").put("singer","Alberto Podestá").put("title","Jamás retornarás").put("percent",90).put("orchestraKeyword",JSONObject().put("text","Caló").put("placement","in_name")))
   ui{WatchCache.connected=true;WatchCache.accept(fixture)}
   check(WatchCache.orchestra()=="Miguel Caló"&&WatchCache.singer().isEmpty())
   checks.put("defaults preserve the existing orchestra display and hide the singer")
   controls=i.startActivitySync(Intent(ctx,WatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as WatchActivity
   ui{dialog=DisplayPreferences.show(controls!!)}
   ui{val list=dialog!!.listView;list.performItemClick(list.getChildAt(1),1,list.adapter.getItemId(1))}
   check(DisplayPreferences.showSinger&&preferences.getBoolean("show_singer",false))
   ui{dialog!!.dismiss();DisplayPreferences.shortOrchestra=true;WatchCache.changed()}
   checks.put("real settings dialog toggles and persists the singer preference")
   display=i.startActivitySync(Intent(ctx,AmbientDisplayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AmbientDisplayActivity
   ownDisplay=true
   i.waitForIdleSync()
   frame()
   ui{
    check(WatchCache.orchestra()=="Caló"&&WatchCache.singer()=="Podestá")
    check(orchestra().text.toString().removePrefix("? ")=="Caló"&&singer().text.toString()=="Podestá")
    check(orchestra().contentDescription.contains("Miguel Caló"))
    check(singer().contentDescription.contains("Alberto Podestá"))
    check(singer().layout.lineCount==1&&singer().layout.getEllipsisCount(0)==0)
    check(orchestra().layout.height<=orchestra().height&&singer().layout.height<=singer().height)
   }
   screenshot("surname")
   checks.put("short orchestra and singer surname fit the actual display; full credits remain accessible")
   ui{DisplayPreferences.singerFirstName=true;WatchCache.changed()}
   frame()
   ui{
    val singer=singer();check(singer.text.toString()=="Alberto Podestá")
    val text=singer.text as Spanned;val span=text.getSpans(0,text.length,AbsoluteSizeSpan::class.java).single()
    check(text.subSequence(text.getSpanStart(span),text.getSpanEnd(span)).toString()=="Podestá")
    check(span.size>singer.textSize&&singer.layout.lineCount==1&&singer.layout.getEllipsisCount(0)==0)
   }
   screenshot("full-singer")
   checks.put("optional first name is smaller than the surname and both fit one line")
   ui{DisplayPreferences.showSinger=false;WatchCache.changed()}
   frame()
   ui{check(singer().visibility==View.GONE)}
   checks.put("disabling the singer releases its line")
   val unknown=JSONObject(fixture.toString()).put("revision",fixture.getLong("revision")+1).put("updatedAt",System.currentTimeMillis())
   unknown.getJSONObject("best").remove("orchestraKeyword")
   ui{DisplayPreferences.showSinger=true;DisplayPreferences.singerFirstName=false;WatchCache.accept(unknown)}
   check(WatchCache.orchestra()=="Miguel Caló")
   check(WatchCache.state.getJSONObject("best").getString("singer")=="Alberto Podestá")
   check(!WatchCache.capturing)
   checks.put("missing preferred-name data retains the full orchestra; source singer and capture are untouched")
  }catch(e:Throwable){failure=e}
  finally{
   runCatching{ui{dialog?.dismiss();display?.finishAndRemoveTask();controls?.finishAndRemoveTask();if(ownDisplay)DisplaySession.close(ctx)}}
   if(changedPreferences){
    preferences.edit().clear().apply{for((key,value) in oldPreferences)if(value is Boolean)putBoolean(key,value)}.commit()
    WatchCache::class.java.getDeclaredField("state").apply{isAccessible=true}.set(null,old)
    ctx.getSharedPreferences("watch-cache",0).edit().putString("state",old.toString()).commit();WatchCache.connected=oldConnected;WatchCache.changed()
   }
   val report=JSONObject().put("status",if(failure==null)"passed" else "failed").put("round_screen",ctx.resources.configuration.isScreenRound).put("checks",checks)
   failure?.let{report.put("error",it.stackTraceToString())}
   File(ctx.filesDir,"display-names-validation.json").writeText(report.toString(2))
   i.finish(if(failure==null)0 else 1,Bundle().apply{putString("report",report.toString())})
  }
 }
}
