package org.tangowiz.wear

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercise actual singer visibility and the space gained by the song on a Wear emulator. */
object InstrumentalLayoutValidation {
 fun run(i:Instrumentation){
  val ctx=i.targetContext;WatchCache.init(ctx)
  var activity:AmbientDisplayActivity?=null;var failure:Throwable?=null
  fun ui(block:()->Unit){var problem:Throwable?=null;i.runOnMainSync{try{block()}catch(t:Throwable){problem=t}};problem?.let{throw it};i.waitForIdleSync()}
  fun frame(){val done=CountDownLatch(1);i.runOnMainSync{val root=activity!!.window.decorView;root.viewTreeObserver.registerFrameCommitCallback{done.countDown()};root.invalidate()};check(done.await(10,TimeUnit.SECONDS)){"Display frame did not commit"}}
  fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap{views(v.getChildAt(it))}else emptyList()
  fun singer()=views(activity!!.window.decorView).filterIsInstance<SingerNameView>().single()
  fun song()=views(activity!!.window.decorView).filterIsInstance<TextView>().single{it.text.toString()=="A very long tango title for the singer layout"}
  try{
   DisplayPreferences.showSinger=true;DisplayPreferences.singerFirstName=true
   val fixture=JSONObject().put("protocol",1).put("supportedProtocols",JSONArray(listOf(1))).put("revision",System.currentTimeMillis()).put("updatedAt",System.currentTimeMillis()).put("listening",false).put("ready",true).put("microphone","phone").put("best",JSONObject().put("id","layout-test").put("orchestra","Pedro Laurenz").put("singer","Héctor Farrel").put("title","A very long tango title for the singer layout").put("percent",90))
   ui{WatchCache.connected=true;WatchCache.accept(fixture)}
   activity=i.startActivitySync(Intent(ctx,AmbientDisplayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AmbientDisplayActivity;i.waitForIdleSync();frame()
   var singerHeight=0;var songHeight=0
   ui{check(singer().visibility==View.VISIBLE);singerHeight=singer().height;songHeight=song().height;check(singerHeight>0)}
   val instrumental=JSONObject(fixture.toString()).put("revision",fixture.getLong("revision")+1).put("updatedAt",System.currentTimeMillis());instrumental.getJSONObject("best").put("singer","Instrumental")
   ui{WatchCache.accept(instrumental)};frame()
   ui{check(WatchCache.singer().isBlank());check(singer().visibility==View.GONE);check(song().height>songHeight){"Song did not gain singer space: before=$songHeight after=${song().height}"}}
  }catch(t:Throwable){failure=t}finally{runCatching{ui{activity?.finishAndRemoveTask();DisplaySession.close(ctx)}};val result=Bundle().apply{putString("status",if(failure==null)"passed" else "failed");failure?.let{putString("error",it.stackTraceToString())}};i.finish(if(failure==null)0 else 1,result)}
 }
}
