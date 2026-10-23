package org.tangowiz.app
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.nio.ByteBuffer
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

private class RangeReset:IOException("Download checkpoint expired; retry to restart")
private class RangeUnsupported:IOException("Server does not support parallel ranges")
/** Four fixed non-overlapping ranges. Only forced bytes enter the durable checkpoint. */
class ParallelArchive(private val transport:ResourceTransport,private val progress:(ResourceState)->Unit){
 fun fetch(m:ResourceManifest,part:File):Boolean {
  val checkpoint=AtomicFile(File(part.parentFile,"${m.archive.sha256}.ranges.json"));val lock=Any();val stop=AtomicBoolean(false)
  val starts=LongArray(4){m.archive.bytes*it/4};val ends=LongArray(4){m.archive.bytes*(it+1)/4-1};val saved=LongArray(4);val current=LongArray(4)
  val restored=runCatching{val j=checkpoint.openRead().use{JSONObject(it.bufferedReader().readText())};require(j.getString("sha256")==m.archive.sha256&&j.getLong("bytes")==m.archive.bytes&&part.isFile&&part.length()==m.archive.bytes);val a=j.getJSONArray("done");require(a.length()==4);for(i in 0..3){saved[i]=a.getLong(i);require(saved[i] in 0..ends[i]-starts[i]+1)};true}.getOrDefault(false)
  if(!restored){part.delete();checkpoint.delete();saved.fill(0)};saved.copyInto(current)
  fun save(){val out=checkpoint.startWrite();try{out.write(JSONObject().put("sha256",m.archive.sha256).put("bytes",m.archive.bytes).put("done",JSONArray(saved.toList())).toString().toByteArray());out.fd.sync();checkpoint.finishWrite(out)}catch(e:Exception){checkpoint.failWrite(out);throw e}}
  val responses=java.util.Collections.synchronizedSet(mutableSetOf<RemoteResponse>())
  val pool=Executors.newFixedThreadPool(4);val completion=ExecutorCompletionService<Unit>(pool);val futures=mutableListOf<Future<*>>();var fallback=false;var reset=false
  RandomAccessFile(part,"rw").use{file->file.setLength(m.archive.bytes);file.fd.sync();synchronized(lock){save()}
   try{
    for(i in 0..3)futures+=completion.submit(Callable<Unit>{
     var done=saved[i];var attempt=0
     while(done<ends[i]-starts[i]+1){
      if(stop.get()||Thread.currentThread().isInterrupted)throw InterruptedIOException("Download paused; retry to continue")
      try{
       val offset=starts[i]+done;val response=transport.open(m.archiveUrl,mapOf("Range" to "bytes=$offset-${ends[i]}"));responses.add(response)
       try{response.use{r->
        if(r.code==200)throw RangeUnsupported()
        if(r.code==416)throw RangeReset()
        if(r.code!=206)throw IOException("Download failed (HTTP ${r.code}); retry to continue")
        val range=Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(r.header("Content-Range")?:"")?:throw IOException("Invalid download range response")
        if(range.groupValues[1].toLong()!=offset||range.groupValues[2].toLong()!=ends[i]||range.groupValues[3].toLong()!=m.archive.bytes)throw IOException("Download range does not match checkpoint")
        var forced=done;var updateAt=0L;val b=ByteArray(262144)
        try{while(done<ends[i]-starts[i]+1){
         if(stop.get()||Thread.currentThread().isInterrupted)throw InterruptedIOException("Download paused; retry to continue")
         val n=r.body.read(b,0,minOf(b.size.toLong(),ends[i]-starts[i]+1-done).toInt());if(n<0)throw EOFException("Download interrupted; retry to continue")
         val buffer=ByteBuffer.wrap(b,0,n);var pos=starts[i]+done;while(buffer.hasRemaining()){val written=file.channel.write(buffer,pos);pos+=written};done+=n
         synchronized(lock){current[i]=done}
         if(done-forced>=4*1024*1024){file.channel.force(false);synchronized(lock){saved[i]=done;save()};forced=done}
         val now=System.currentTimeMillis();if(now-updateAt>=250){val received=synchronized(lock){current.sum()};progress(ResourceState("downloading","Downloading resources",received,m.archive.bytes));updateAt=now}
        }}finally{file.channel.force(false);synchronized(lock){saved[i]=done;save()}}
       }}finally{responses.remove(response)}
      }catch(e:RangeUnsupported){throw e}catch(e:RangeReset){throw e}catch(e:Exception){if(stop.get()||Thread.currentThread().isInterrupted||++attempt>=2)throw e;Thread.sleep(500L*attempt)}
     }
     Unit
    })
    repeat(4){completion.take().get()}
   }catch(e:Exception){val cause=if(e is ExecutionException)e.cause?:e else e;fallback=cause is RangeUnsupported;reset=cause is RangeReset;if(!fallback&&!reset)throw if(cause is Exception)cause else IOException(cause)}
   finally{stop.set(true);transport.cancel();futures.forEach{it.cancel(true)};pool.shutdownNow();var interrupted=Thread.interrupted()
    while(!pool.isTerminated){try{pool.awaitTermination(1,TimeUnit.SECONDS)}catch(e:InterruptedException){interrupted=true};if(!pool.isTerminated)transport.cancel()}
    synchronized(responses){responses.toList().forEach{runCatching{it.close()}}};if(interrupted)Thread.currentThread().interrupt()
   }
  }
  if(reset){part.delete();checkpoint.delete();throw IOException("Download checkpoint expired; retry to restart")}
  if(fallback){part.delete();checkpoint.delete();return false};return true
 }
}
