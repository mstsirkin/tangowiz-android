package org.tangowiz.app

import android.app.Instrumentation
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Actual production HTTPS transport and redirect/range handling on Android. */
object DownloadNetworkValidation {
 fun run(i:Instrumentation){val pool=Executors.newFixedThreadPool(4);val report=JSONObject();val transports=(0..3).map{HttpResourceTransport()}
  try{
   val manifest=HttpResourceTransport().open(RESOURCE_MANIFEST_URL,emptyMap()).use{r->check(r.code==200);ResourceManifest.parse(JSONObject(r.body.bufferedReader().readText()))}
   val jobs=(0..3).map{lane->pool.submit<JSONObject>{val start=manifest.archive.bytes*lane/4;val end=start+1024*1024-1;val began=System.nanoTime()
    transports[lane].open(manifest.archiveUrl,mapOf("Range" to "bytes=$start-$end")).use{r->check(r.code==206);check(r.header("Content-Range")=="bytes $start-$end/${manifest.archive.bytes}");val bytes=r.body.readBytes();check(bytes.size==1024*1024)
     JSONObject().put("lane",lane).put("start",start).put("bytes",bytes.size).put("sha256",MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}).put("seconds",(System.nanoTime()-began)/1e9)
    }
   }}
   val results=JSONArray();jobs.forEach{results.put(it.get(90,TimeUnit.SECONDS))}
   report.put("status","passed").put("manifest_version",manifest.version).put("four_real_https_ranges",results).put("requested_receive_bytes",DownloadSocketFactory.REQUESTED_RECEIVE_BYTES).put("physical_phone_speed_measured",false)
   File(i.targetContext.filesDir,"download-network-validation.json").writeText(report.toString(2));i.finish(0,Bundle().apply{putString("report",report.toString())})
  }catch(e:Throwable){report.put("status","failed").put("error",e.stackTraceToString());File(i.targetContext.filesDir,"download-network-validation.json").writeText(report.toString(2));i.finish(1,Bundle().apply{putString("error",e.stackTraceToString())})}
  finally{transports.forEach{it.cancel()};pool.shutdownNow()}
 }
}
