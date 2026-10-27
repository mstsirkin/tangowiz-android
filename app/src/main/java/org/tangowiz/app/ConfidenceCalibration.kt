package org.tangowiz.app

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Optional member of the existing resource ZIP; old bundles use built-in defaults. */
object ConfidenceCalibration {
 const val FILE_NAME="confidence-calibration.json"
 data class Parameters(val a:Double=DisplayConfidence.DEFAULT_A,val b:Double=DisplayConfidence.DEFAULT_B){
  fun percent(score:Double)=DisplayConfidence.percent(score,a,b)
 }
 private val defaults=Parameters()
 private var cacheKey=""
 private var cached=defaults
 @Synchronized fun parameters(bundle:BundleSnapshot?):Parameters {
  if(bundle==null)return defaults
  val file=File(bundle.directory,FILE_NAME)
  val verified=File(bundle.directory,"verified.json")
  val key="${bundle.directory.absolutePath}|${file.length()}|${file.lastModified()}|${verified.length()}|${verified.lastModified()}"
  if(key!=cacheKey){cached=read(bundle);cacheKey=key}
  return cached
 }
 fun percent(bundle:BundleSnapshot?,score:Double)=parameters(bundle).percent(score)
 private fun read(bundle:BundleSnapshot):Parameters=runCatching{
  val spec=JSONObject(File(bundle.directory,"verified.json").readText()).optJSONObject("files")?.optJSONObject(FILE_NAME)?:return defaults
  val file=File(bundle.directory,FILE_NAME);val size=spec.getLong("bytes");require(size in 1..4096&&file.length()==size)
  val bytes=file.readBytes();val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
  require(hash==spec.getString("sha256"));decode(bytes)
 }.getOrDefault(defaults)
 fun decode(bytes:ByteArray):Parameters {
  require(bytes.size in 1..4096)
  val j=JSONObject(String(bytes,Charsets.UTF_8));require(j.getInt("version")==1&&j.getString("method")=="sigmoid")
  val a=j.getDouble("a");val b=j.getDouble("b");require(a.isFinite()&&a>0&&a<=1000&&b.isFinite()&&kotlin.math.abs(b)<=1000)
  return Parameters(a,b)
 }
}
