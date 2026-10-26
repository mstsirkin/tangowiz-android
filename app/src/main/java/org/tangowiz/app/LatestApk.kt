package org.tangowiz.app

import org.json.JSONArray
import android.content.Intent
import android.net.Uri
import java.net.URI
import java.io.IOException

data class ApkOffer(val version:String,val url:String)
object LatestApk {
 const val API="https://api.github.com/repos/mstsirkin/tangowiz-android/releases?per_page=30"
 private fun parts(s:String):List<Int>?{if(!s.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")))return null;return s.split('.').map{it.toIntOrNull()?:return null}}
 fun newer(a:String,b:String):Boolean{val x=parts(a)?:return false;val y=parts(b)?:return false;for(i in 0..2)if(x[i]!=y[i])return x[i]>y[i];return false}
 fun choose(releases:JSONArray,current:String):ApkOffer?{
  var result:ApkOffer?=null
  for(i in 0 until releases.length()){
   val release=releases.optJSONObject(i)?:continue;if(release.optBoolean("draft",true))continue
   val assets=release.optJSONArray("assets")?:continue
   for(j in 0 until assets.length()){
    val a=assets.optJSONObject(j)?:continue;val match=Regex("TangoWiz-([0-9]+\\.[0-9]+\\.[0-9]+)\\.apk").matchEntire(a.optString("name"))?:continue
    val version=match.groupValues[1];if(release.optString("tag_name")!="v$version"||a.optString("state")!="uploaded"||!newer(version,current))continue
    val url=a.optString("browser_download_url");val uri=runCatching{URI(url)}.getOrNull()?:continue
    if(uri.scheme!="https"||uri.host!="github.com"||uri.userInfo!=null||uri.port!=-1||uri.fragment!=null||uri.query!=null||uri.path!="/mstsirkin/tangowiz-android/releases/download/v$version/TangoWiz-$version.apk")continue
    if(result==null||newer(version,result.version))result=ApkOffer(version,url)
   }
  }
  return result
 }
 fun downloadIntent(offer:ApkOffer)=Intent(Intent.ACTION_VIEW,Uri.parse(offer.url)).addCategory(Intent.CATEGORY_BROWSABLE)
 fun check(current:String,transport:ResourceTransport=HttpResourceTransport()):ApkOffer?=transport.open(API,mapOf("Accept" to "application/vnd.github+json","User-Agent" to "TangoWiz/$current")).use{r->
  if(r.code!=200)throw IOException("App update check failed (HTTP ${r.code})")
  choose(JSONArray(r.body.readBytesLimited(4*1024*1024).toString(Charsets.UTF_8)),current)
 }
}
