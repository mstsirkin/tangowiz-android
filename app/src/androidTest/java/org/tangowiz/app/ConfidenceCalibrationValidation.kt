package org.tangowiz.app

import android.app.Instrumentation
import android.content.ContextWrapper
import android.os.Bundle
import org.json.*
import java.io.*
import java.security.MessageDigest
import java.util.zip.*

/** Small synthetic resource ZIPs only: no model inference, microphone, or real library writes. */
object ConfidenceCalibrationValidation {
 fun run(i:Instrumentation){val work=File(i.targetContext.cacheDir,"calibration-resource-validation");val checks=JSONArray()
  fun sha(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it)}
  fun zip(entries:Map<String,ByteArray>)=ByteArrayOutputStream().also{b->ZipOutputStream(b).use{z->for((n,v) in entries){z.putNextEntry(ZipEntry(n));z.write(v);z.closeEntry()}}}.toByteArray()
  fun spec(b:ByteArray)=JSONObject().put("bytes",b.size).put("sha256",sha(b))
  try {
   work.deleteRecursively();work.mkdirs();val ctx=object:ContextWrapper(i.targetContext){override fun getFilesDir()=work}
   val entries=linkedMapOf("catalog.bin" to "TWIDX002fixture".toByteArray(),"nmfp.tflite" to byteArrayOf(1))
   var archive=zip(entries);val manifest=JSONObject().put("formatVersion",2).put("bundleVersion","legacy").put("files",JSONObject())
   fun update(version:String){archive=zip(entries);manifest.put("bundleVersion",version).put("archive",spec(archive).put("url","https://test.invalid/archive"));val f=JSONObject();for((n,v) in entries)f.put(n,spec(v));manifest.put("files",f)}
   update("legacy")
   val transport=object:ResourceTransport {override fun open(url:String,headers:Map<String,String>):RemoteResponse {if(url.endsWith("manifest"))return RemoteResponse(200,emptyMap(),manifest.toString().byteInputStream());val m=Regex("bytes=(\\d+)-(\\d+)").matchEntire(headers["Range"]?:"");if(m==null)return RemoteResponse(200,emptyMap(),archive.inputStream());val first=m.groupValues[1].toInt();val last=m.groupValues[2].toInt();return RemoteResponse(206,mapOf("Content-Range" to "bytes $first-$last/${archive.size}"),ByteArrayInputStream(archive,first,last-first+1))}}
   val store=ResourceStore(ctx,transport,"https://test.invalid/manifest",progress={},validate={})
   store.check(true);check(ConfidenceCalibration.percent(store.current(),.5)==79);checks.put("Original ZIP uses the approved hardcoded single-score curve")
   val data=JSONObject().put("version",1).put("method","sigmoid").put("a",2).put("b",-1).toString().toByteArray();entries[ConfidenceCalibration.FILE_NAME]=data;update("custom")
   store.check(true);val current=store.current()!!;check(ConfidenceCalibration.percent(current,.5)==50);checks.put("Verified optional ZIP member overrides both display parameters")
   entries["orchestra-keywords.json"]="{\"version\":1,\"names\":[]}".toByteArray();update("both-optional");store.check(true);val both=store.current()!!;check(ConfidenceCalibration.percent(both,.5)==50);checks.put("Keyword table and display parameters coexist in one resource ZIP")
   entries[ConfidenceCalibration.FILE_NAME]="{\"version\":1,\"method\":\"sigmoid\",\"a\":-1,\"b\":0}".toByteArray();update("invalid");check(runCatching{store.check(true)}.exceptionOrNull() is IOException);check(store.current()!!.directory==both.directory);check(ConfidenceCalibration.percent(store.current(),.5)==50);checks.put("Invalid parameter update preserves the previous complete bundle")
   entries[ConfidenceCalibration.FILE_NAME]=data;update("bad-checksum");archive+=byteArrayOf(1);manifest.put("archive",spec(archive).put("url","https://test.invalid/archive"));manifest.getJSONObject("files").getJSONObject(ConfidenceCalibration.FILE_NAME).put("sha256","f".repeat(64));check(runCatching{store.check(true)}.exceptionOrNull() is IOException);check(store.current()!!.directory==both.directory);checks.put("Calibration checksum failure cannot replace the installed bundle")
   File(both.directory,ConfidenceCalibration.FILE_NAME).delete();check(store.current()!=null);check(ConfidenceCalibration.percent(store.current(),.5)==79);checks.put("Missing installed optional file leaves recognition available and restores defaults")
   entries.remove(ConfidenceCalibration.FILE_NAME);update("no-calibration");store.check(true);check(ConfidenceCalibration.percent(store.current(),.5)==79);checks.put("New resource generation without parameters uses defaults")
   check(ConfidenceCalibration.percent(null,.5)==79)
   for(bad in listOf("{\"version\":2,\"method\":\"sigmoid\",\"a\":2,\"b\":0}","{\"version\":1,\"method\":\"other\",\"a\":2,\"b\":0}","{}"))check(runCatching{ConfidenceCalibration.decode(bad.toByteArray())}.isFailure)
   checks.put("Unsupported schema and missing fields are rejected")
   val live=LiveState.current
   try {
    val candidate=Candidate("display-fixture","Fixture orchestra","Fixture tune",score=.5,confidence=50)
    LiveState.current=live.copy(best=candidate,second=null)
    PhoneWatch.init(i.targetContext)
    val watch=PhoneWatch.state().getJSONObject("best")
    check(watch.getInt("percent")==79&&watch.getDouble("score")==.5)
    check(LiveState.current.best!!.score==.5&&LiveState.current.best!!.confidence==50)
    val history=Store(ctx);history.result(candidate,null,2.0,"calibration-fixture")
    val row=history.history().last();check(row.getInt("confidence_score")==50&&row.getDouble("score")==.5&&row.getString("state")=="tentative")
    checks.put("Phone/watch display uses 79 percent while raw state and stored history remain 50")
   }finally{LiveState.current=live}

   val out=JSONObject().put("status","passed").put("checks",checks).put("raw_score_and_history_untouched",true)
   i.finish(0,Bundle().apply{putString("report",out.toString())})
  }catch(e:Throwable){i.finish(1,Bundle().apply{putString("error",e.stackTraceToString())})}finally{work.deleteRecursively()}
 }
}
