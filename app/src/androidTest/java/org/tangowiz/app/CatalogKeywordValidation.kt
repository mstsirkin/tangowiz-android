package org.tangowiz.app

import android.app.Instrumentation
import android.content.ContextWrapper
import android.os.Bundle
import org.json.*
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.*

/** One ZIP with optional keyword member; old bundles and absent hints retain plain names. */
object CatalogKeywordValidation {
 fun run(i:Instrumentation){val ctx=i.targetContext;val report=JSONObject();val checks=JSONArray();val active=File(ResourceStore(ctx).root,"active.json");val original=active.takeIf{it.exists()}?.readBytes();val live=LiveState.current;val work=File(ctx.cacheDir,"keyword-resource-test");var injected:File?=null
  fun sha(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it)}
  fun zip(entries:Map<String,ByteArray>)=ByteArrayOutputStream().also{b->ZipOutputStream(b).use{z->for((n,v) in entries){z.putNextEntry(ZipEntry(n));z.write(v);z.closeEntry()}}}.toByteArray()
  fun spec(b:ByteArray)=JSONObject().put("bytes",b.size).put("sha256",sha(b))
  try{
   check(!live.listening){"Pause before test"};work.deleteRecursively();work.mkdirs();val testCtx=object:ContextWrapper(ctx){override fun getFilesDir()=work}
   val catalog="TWIDX002fixture".toByteArray();val model=byteArrayOf(1);val table=JSONObject().put("version",1).put("names",JSONArray().put(JSONArray(listOf("Carlos Di Sarli","Di Sarli",false))).put(JSONArray(listOf("La Juan D'Arienzo","LJD",true)))).toString().toByteArray()
   var archive=zip(linkedMapOf("catalog.bin" to catalog,"nmfp.tflite" to model));val manifest=JSONObject().put("formatVersion",2).put("bundleVersion","base").put("archive",spec(archive).put("url","https://test.invalid/archive")).put("files",JSONObject().put("catalog.bin",spec(catalog)).put("nmfp.tflite",spec(model)))
   val transport=object:ResourceTransport{override fun open(url:String,headers:Map<String,String>):RemoteResponse{if(url.endsWith("manifest"))return RemoteResponse(200,emptyMap(),manifest.toString().byteInputStream());val m=Regex("bytes=(\\d+)-(\\d+)").matchEntire(headers["Range"]?:"");return if(m==null)RemoteResponse(200,emptyMap(),archive.inputStream())else{val start=m.groupValues[1].toInt();val end=m.groupValues[2].toInt();RemoteResponse(206,mapOf("Content-Range" to "bytes $start-$end/${archive.size}"),ByteArrayInputStream(archive,start,end-start+1))}}}
   val store=ResourceStore(testCtx,transport,"https://test.invalid/manifest",progress={},validate={})
   store.check(true);val baseline=store.current()!!;check(CatalogKeywords.read(baseline).isEmpty());checks.put("Original two-member ZIP remains supported with plain names")
   archive=zip(linkedMapOf("catalog.bin" to catalog,"nmfp.tflite" to model,"orchestra-keywords.json" to table));manifest.put("bundleVersion","keywords").put("archive",spec(archive).put("url","https://test.invalid/archive"));manifest.getJSONObject("files").put("orchestra-keywords.json",spec(table));store.check(true)
   val updated=store.current()!!;check(updated.directory!=baseline.directory);check(File(updated.directory,"catalog.bin").readBytes().contentEquals(catalog));check(CatalogKeywords.hint(updated,"Carlos Di Sarli")?.text=="Di Sarli");check(CatalogKeywords.hint(updated,"La Juan D’Arienzo")?.append==true);checks.put("Third ZIP member supplies verified hints without changing index bytes")
   archive+=byteArrayOf(1);manifest.put("archive",spec(archive).put("url","https://test.invalid/archive"));manifest.getJSONObject("files").getJSONObject("orchestra-keywords.json").put("sha256","f".repeat(64));check(runCatching{store.check(true)}.exceptionOrNull() is IOException);check(store.current()!!.directory==updated.directory);checks.put("Failed keyword checksum preserves the entire previous bundle")
   manifest.getJSONObject("files").getJSONObject("orchestra-keywords.json").put("sha256",sha(table))
   injected=File(ResourceStore(ctx).root,"bundle-${sha(archive)}-${UUID.randomUUID()}").apply{mkdirs()};for(name in listOf("catalog.bin","nmfp.tflite","orchestra-keywords.json"))File(updated.directory,name).copyTo(File(injected!!,name));File(injected!!,"verified.json").writeText(manifest.toString());active.writeText(JSONObject(manifest.toString()).put("directory",injected!!.name).toString());LiveState.current=live.copy(best=Candidate("fixture","Carlos Di Sarli","Fixture"));PhoneWatch.init(ctx)
   val state=PhoneWatch.state();check(state.getJSONObject("best").getJSONObject("orchestraKeyword").getString("text")=="Di Sarli")
   val forPeer=PhoneWatch::class.java.getDeclaredMethod("forPeer",String::class.java,JSONObject::class.java).apply{isAccessible=true};check(!(forPeer.invoke(PhoneWatch,"legacy-fixture",state) as JSONObject).getJSONObject("best").has("orchestraKeyword"))
   @Suppress("UNCHECKED_CAST") val peers=PhoneWatch::class.java.getDeclaredField("peerKeywords").apply{isAccessible=true}.get(PhoneWatch) as java.util.concurrent.ConcurrentHashMap<String,Boolean>;peers["new-fixture"]=true;check((forPeer.invoke(PhoneWatch,"new-fixture",state) as JSONObject).getJSONObject("best").has("orchestraKeyword"));peers.remove("new-fixture");checks.put("Capable peers get hints while legacy direct messages remain plain")
   File(injected!!,"orchestra-keywords.json").delete();check(ResourceStore(ctx).current()!=null);check(CatalogKeywords.hint(ResourceStore(ctx).current(),"Carlos Di Sarli")==null);checks.put("Absent optional table does not block recognition and removes emphasis")
   check(CatalogKeywords.hint(updated,"Unknown ensemble")==null)
   report.put("status","passed").put("checks",checks).put("count",checks.length()).put("single_zip",true).put("index_bytes_unchanged",true);File(ctx.filesDir,"catalog-keyword-validation.json").writeText(report.toString(2));i.finish(0,Bundle().apply{putString("report",report.toString())})
  }catch(e:Throwable){report.put("status","failed").put("checks",checks).put("error",e.stackTraceToString());File(ctx.filesDir,"catalog-keyword-validation.json").writeText(report.toString(2));i.finish(1,Bundle().apply{putString("error",e.stackTraceToString())})}finally{synchronized(ResourcePins.lock){if(original!=null)active.writeBytes(original)else active.delete();injected?.deleteRecursively()};LiveState.current=live;work.deleteRecursively()}
 }
}
