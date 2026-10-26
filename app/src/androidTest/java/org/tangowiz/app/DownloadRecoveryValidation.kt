package org.tangowiz.app
import android.app.Instrumentation
import android.os.Bundle
import org.json.*
import java.io.*
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
object DownloadRecoveryValidation{
 fun run(i:Instrumentation){val root=File(i.targetContext.cacheDir,"range-recovery-test");root.deleteRecursively();root.mkdirs();val checks=JSONArray()
  try{val bytes=ByteArray(128*1024){(it*37).toByte()};val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)};val seen=ConcurrentHashMap<Int,MutableList<Int>>();val cancelled=AtomicBoolean(false)
   val transport=object:ResourceTransport{
    override fun cancel(){cancelled.set(true)}
    override fun open(url:String,headers:Map<String,String>):RemoteResponse{
     val match=Regex("bytes=(\\d+)-(\\d+)").matchEntire(headers["Range"]!!)!!;val start=match.groupValues[1].toInt();val end=match.groupValues[2].toInt();val lane=start/(bytes.size/4);val calls=seen.computeIfAbsent(lane){mutableListOf()};calls.add(start)
     val blocked=AtomicBoolean(false);val first=lane==0&&calls.size==1
     val raw=if(first)object:InputStream(){var count=0;override fun read():Int{val b=ByteArray(1);return if(read(b,0,1)<0)-1 else b[0].toInt()and 255};override fun read(b:ByteArray,o:Int,l:Int):Int{if(count<4096){val n=minOf(l,4096-count);System.arraycopy(bytes,start+count,b,o,n);count+=n;return n};while(!blocked.get()&&!cancelled.get())Thread.sleep(5);throw IOException("deliberate blocked stream")}} else ByteArrayInputStream(bytes.copyOfRange(start,end+1))
     val body=DownloadReadWatchdog(raw,100){blocked.set(true)}
     return RemoteResponse(206,mapOf("Content-Range" to "bytes $start-$end/${bytes.size}"),body)
    }
   }
   val manifest=ResourceManifest("recovery","https://test.invalid/archive",ResourceFile(bytes.size.toLong(),hash),emptyMap(),JSONObject())
   val part=File(root,"$hash.part");check(ParallelArchive(transport,{},retryBaseMs=10).fetch(manifest,part));check(part.readBytes().contentEquals(bytes))
   check(seen[0]==listOf(0,4096));check((1..3).all{seen[it]?.size==1});checks.put("one blocked stream aborted and resumed at exact saved byte; three healthy streams not restarted")
   val checkpoint=JSONObject(File(root,"$hash.ranges.json").readText()).getJSONArray("done");check((0..3).all{checkpoint.getLong(it)==bytes.size/4L});checks.put("all final checkpoints forced and complete")
   val failureCalls=java.util.concurrent.atomic.AtomicInteger();val failing=ResourceTransport{_,_->failureCalls.incrementAndGet();throw IOException("deliberate no-progress failure")}
   val failedDir=File(root,"exhausted").apply{mkdirs()};val began=System.nanoTime();val failure=runCatching{ParallelArchive(failing,{},retryBaseMs=1,maxNoProgressAttempts=3).fetch(manifest,File(failedDir,"$hash.part"))}.exceptionOrNull()
   check(failure is IOException&&failureCalls.get() in 3..12&&(System.nanoTime()-began)<5_000_000_000L);checks.put("consecutive no-progress failures stop after a bounded retry budget")
   val report=JSONObject().put("status","passed").put("checks",checks);File(i.targetContext.filesDir,"download-recovery-validation.json").writeText(report.toString(2));i.finish(0,Bundle().apply{putString("report",report.toString())})
  }catch(e:Throwable){i.finish(1,Bundle().apply{putString("error",e.stackTraceToString())})}finally{root.deleteRecursively()}
 }
}
