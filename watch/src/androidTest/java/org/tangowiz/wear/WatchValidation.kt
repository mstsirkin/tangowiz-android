package org.tangowiz.wear
import android.app.*
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.TextView
import org.json.*
import java.io.File
import kotlin.math.*

/** Device-side validation uses synthetic public labels, restores cache, and never starts a microphone. */
class WatchValidation:Instrumentation(){private var arguments=Bundle();override fun onCreate(arguments:Bundle?){super.onCreate(arguments);this.arguments=arguments?:Bundle();start()}
 override fun onStart(){if(arguments.getString("mode")=="keywords"){KeywordValidation.run(this);return};if(arguments.getString("mode")=="display_names"){DisplayNamesValidation.run(this);return};val checks=JSONArray();var activity:Activity?=null;val ctx=targetContext;WatchCache.init(ctx);val old=JSONObject(WatchCache.state.toString());val prefs=ctx.getSharedPreferences("watch-cache",0);val oldHistory=WatchCache.history;var resultCode=0;var result=Bundle()
  try{
   val pcm=ShortArray(8000){(12000*sin(2*PI*1000*it/8000)).toInt().toShort()};check(PcmResampler(8000).append(pcm,pcm.size).contentEquals(pcm));checks.put("8000 Hz PCM bypass is sample-exact")
   fun convert(rate:Int,tone:Int):ShortArray{val input=ShortArray(rate*2){(10000*sin(2*PI*tone*it/rate)).toInt().toShort()};val r=PcmResampler(rate);val all=ArrayList<Short>();var at=0;while(at<input.size){val n=minOf(2048,input.size-at);all.addAll(r.append(input.copyOfRange(at,at+n),n).toList());at+=n};return all.toShortArray()}
   fun rms(values:ShortArray)=sqrt(values.drop(200).map{it.toDouble()*it}.average())
   for(rate in listOf(44100,48000)){val low=convert(rate,1000);val high=convert(rate,6000);check(low.size in 15970..16010);check(rms(low)>5000&&rms(high)<500)};checks.put("44100/48000 Hz streaming resampling preserves 1 kHz and rejects aliased 6 kHz")
   val state=JSONObject().put("revision",old.optLong("revision",0)+100).put("updatedAt",System.currentTimeMillis()).put("microphone","phone").put("listening",false).put("ready",true).put("audio",false).put("timing",false).put("updateIntervalMs",5000).put("best",JSONObject().put("orchestra","Miguel Caló").put("title","Jamás retornarás").put("percent",92))
   WatchCache.connected=true;WatchCache.accept(state);WatchCache.accept(JSONObject(state.toString()).put("revision",state.getLong("revision")-1).put("best",JSONObject().put("orchestra","Stale wrong orchestra")));check(WatchCache.orchestra()=="Miguel Caló");WatchCache.accept(JSONObject(state.toString()).put("updatedAt",1).put("best",JSONObject().put("orchestra","Stale same revision")));check(WatchCache.orchestra()=="Miguel Caló");checks.put("late ACK/state cannot roll cache back to an older revision or result timestamp")
   check(WatchCache.keyword()==null);checks.put("legacy phone without hint/capability retains plain names")
   val incompatible=JSONObject(state.toString()).put("protocol",2).put("supportedProtocols",JSONArray(listOf(2))).put("revision",state.getLong("revision")+1)
   WatchCache.accept(incompatible);check(!WatchCache.protocolCompatible&&WatchCache.error.contains("Update"));check(WatchCache.orchestra()=="Miguel Caló")
   WatchCache.accept(JSONObject(state.toString()).put("updatedAt",System.currentTimeMillis()));check(WatchCache.protocolCompatible);checks.put("incompatible protocol is rejected and legacy protocol 1 recovers")
   activity=startActivitySync(Intent(ctx,WatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync()
   fun texts(v:View):List<String> =if(v is TextView)listOf(v.text.toString().replace(Regex("""\s+""")," ")) else if(v is ViewGroup)(0 until v.childCount).flatMap{texts(v.getChildAt(it))} else emptyList()
   val labels=texts(activity!!.window.decorView);check(labels.contains("Miguel Caló")&&labels.contains("Jamás retornarás"));checks.put("actual round companion UI displays orchestra/song and scrollable controls without starting capture")
   runOnMainSync{activity!!.recreate()};waitForIdleSync();check(!WatchCache.capturing);checks.put("view recreation does not automatically activate microphone")
   val report=JSONObject().put("status","passed").put("checks",checks).put("count",checks.length());File(ctx.filesDir,"watch-validation.json").writeText(report.toString(2));result=Bundle().apply{putString("summary",report.toString())}
  }catch(e:Throwable){File(ctx.filesDir,"watch-validation.json").writeText(JSONObject().put("status","failed").put("checks",checks).put("error",e.stackTraceToString()).toString(2));resultCode=1;result=Bundle().apply{putString("error",e.stackTraceToString())}}
  finally{WatchCache::class.java.getDeclaredField("state").apply{isAccessible=true}.set(null,old);prefs.edit().putString("state",old.toString()).commit();WatchCache.acceptHistory(oldHistory);WatchCache.connected=false;WatchCache.changed();runCatching{runOnMainSync{activity?.finishAndRemoveTask()}};finish(resultCode,result)}
 }
}
