package org.tangowiz.app
import android.app.Instrumentation
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import org.json.*
object LatestApkValidation{
 fun run(i:Instrumentation){try{
  fun release(v:String,name:String="TangoWiz-$v.apk",url:String="https://github.com/mstsirkin/tangowiz-android/releases/download/v$v/$name",draft:Boolean=false)=JSONObject().put("tag_name","v$v").put("draft",draft).put("prerelease",true).put("assets",JSONArray().put(JSONObject().put("name",name).put("state","uploaded").put("browser_download_url",url)))
  check(LatestApk.newer("0.10.0","0.9.9"));check(!LatestApk.newer("0.5.4","0.5.4"));check(!LatestApk.newer("bad","0.5.4"))
  val available=LatestApk.choose(JSONArray().put(release("0.5.3")).put(release("0.5.9",draft=true)).put(release("0.5.8",name="TangoWiz-Watch-0.5.8.apk")).put(release("0.5.7",url="https://evil.example/TangoWiz-0.5.7.apk")).put(release("0.5.5")),"0.5.4")!!
  check(available.version=="0.5.5");check(LatestApk.choose(JSONArray().put(release("0.5.4")),"0.5.4")==null)
  val intent=LatestApk.downloadIntent(available);check(intent.`package`==null&&intent.component==null)
  i.finish(0,Bundle().apply{putString("report",JSONObject().put("status","passed").put("checks",8).put("default_browser_intent",true).toString())})
 }catch(e:Throwable){i.finish(1,Bundle().apply{putString("error",e.stackTraceToString())})}}
}
