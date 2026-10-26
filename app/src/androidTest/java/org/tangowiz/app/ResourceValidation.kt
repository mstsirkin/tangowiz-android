package org.tangowiz.app
import android.app.Instrumentation
import android.content.ContextWrapper
import android.os.Bundle
import org.json.*
import java.io.*
import java.security.MessageDigest
import java.util.zip.*
import java.util.concurrent.CopyOnWriteArrayList

object ResourceValidation {
 private fun hash(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it)}
 private fun zip(entries:List<Pair<String,ByteArray>>):ByteArray{val b=ByteArrayOutputStream();ZipOutputStream(b).use{z->for((n,v) in entries){z.putNextEntry(ZipEntry(n));z.write(v);z.closeEntry()}};return b.toByteArray()}
 fun run(i:Instrumentation){val checks=JSONArray();val ctx=object:ContextWrapper(i.targetContext){override fun getFilesDir()=File(super.getFilesDir(),"resource-tests").apply{mkdirs()}}
 try{
  File(ctx.filesDir,"resources-v1").deleteRecursively()
  val catalog=ByteArray(400000);val model=ByteArray(80000);java.util.Random(42).nextBytes(catalog);java.util.Random(43).nextBytes(model)
  var archive=zip(listOf("catalog.bin" to catalog,"nmfp.tflite" to model));var manifest=manifest(archive,catalog,model,"one")
  var manifestStatus=200;var manifestNetwork=false;var failBody=false;var ignoreRange=false
  val requests=CopyOnWriteArrayList<Pair<String,Map<String,String>>>()
  val transport=ResourceTransport{url,headers->requests+=url to headers
   if(url.endsWith("manifest")){if(manifestNetwork)throw IOException("DNS unavailable");RemoteResponse(manifestStatus,emptyMap(),manifest.toString().byteInputStream())}
   else{val range=headers["Range"];if(range==null||ignoreRange)RemoteResponse(200,emptyMap(),archive.inputStream()) else{
    val m=Regex("bytes=(\\d+)-(\\d*)").matchEntire(range)!!;val start=m.groupValues[1].toInt();val end=m.groupValues[2].toIntOrNull()?:archive.lastIndex;val bytes=archive.copyOfRange(start,end+1)
    // Allow one partial response per lane, then keep failing until the retry budget is exhausted.
    val body=if(failBody)object:InputStream(){var n=if((0..3).any{start==archive.size*it/4})0 else 4000;override fun read():Int{if(n>=4000)throw IOException("Connection lost");return if(n<bytes.size)bytes[n++].toInt()and 255 else -1};override fun read(b:ByteArray,o:Int,l:Int):Int{if(n>=4000)throw IOException("Connection lost");if(n>=bytes.size)return -1;val take=minOf(l,4000-n,bytes.size-n);System.arraycopy(bytes,n,b,o,take);n+=take;return take}} else bytes.inputStream()
    RemoteResponse(206,mapOf("Content-Range" to "bytes $start-$end/${archive.size}"),body)
   }}
  }
  val progressStates=CopyOnWriteArrayList<ResourceState>()
  fun store()=ResourceStore(ctx,transport,"https://test.invalid/manifest",progress={progressStates+=it},validate={b->check(b.catalog.readBytes().contentEquals(catalog)&&b.model.readBytes().contentEquals(model))})
  var s=store();s.automaticChecks=false;check(!s.dueAutomaticCheck()&&requests.isEmpty());checks.put("automatic checks default/off make no network requests")
  s.check(false);check(s.current()==null&&requests.size==1&&s.offered()!=null);checks.put("bootstrap manifest shows size without downloading archive")
  s.acceptOffer();check(progressStates.any{it.kind=="verifying"&&it.total>0&&it.received>0});val first=s.current()!!;check(first.catalog.readBytes().contentEquals(catalog));checks.put("fresh four-range bootstrap validates and activates")
  val pinned=s.pinCurrent()!!
  manifestNetwork=true;expect<IOException>{s.check(false)};manifestNetwork=false;check(s.current()!!.directory==first.directory);checks.put("network failure retains installed bundle and remains retryable")
  manifestStatus=503;val transient=runCatching{s.check(false)}.exceptionOrNull();check(transient is IOException&&transient !is AppUpdateRequired);manifestStatus=404;expect<AppUpdateRequired>{s.check(false)};manifestStatus=200;checks.put("404 format retired differs from 503/network retry")
  manifest.put("formatVersion",3);expect<AppUpdateRequired>{s.check(false)};manifest.put("formatVersion",1);checks.put("incompatible manifest requests app update")
  manifest=manifest(archive,catalog,model,"bad-hash");manifest.getJSONObject("archive").put("sha256","0".repeat(64));expect<IOException>{s.check(true)};check(s.current()!!.directory==first.directory);checks.put("bad archive hash cannot replace current bundle")
  archive=zip(listOf("catalog.bin" to catalog,"nmfp.tflite" to model,"../evil" to byteArrayOf(1)));manifest=manifest(archive,catalog,model,"unsafe");expect<IOException>{s.check(true)};check(s.current()!!.directory==first.directory);checks.put("ZIP traversal/extra entries rejected before activation")
  val normalZip=zip(listOf("catalog.bin" to catalog,"nmfp.tflite" to model))
  archive=normalZip.copyOf();val from="nmfp.tflite".toByteArray();val to="catalog.bin".toByteArray();for(pos in 0..archive.size-from.size){if(from.indices.all{archive[pos+it]==from[it]})to.copyInto(archive,pos)}
  manifest=manifest(archive,catalog,model,"duplicate");expect<IOException>{s.check(true)};check(s.current()!!.directory==first.directory);checks.put("duplicate ZIP entries rejected")
  archive=normalZip+byteArrayOf(9);manifest=manifest(archive,catalog,model,"oversize");manifest.getJSONObject("files").getJSONObject("catalog.bin").put("bytes",catalog.size-1);expect<IOException>{s.check(true)};check(s.current()!!.directory==first.directory);checks.put("expanded-file size limit enforced")
  archive=zip(listOf("catalog.bin" to catalog,"nmfp.tflite" to model));manifest=manifest(archive,catalog,model,"two").also{it.getJSONObject("archive").put("sha256",hash(archive))}
  // Change ZIP bytes while preserving real test file payloads.
  archive=archive+byteArrayOf(0);manifest=manifest(archive,catalog,model,"two")
  failBody=true;expect<IOException>{s.check(true)};check(s.current()!!.directory==first.directory&&s.hasPending());failBody=false
  val before=requests.size;s=store();s.resume();check(requests.drop(before).any{(_,h)->h["Range"]?.substringAfter("bytes=")?.substringBefore('-')?.toLongOrNull()?.let{it>0}==true});check(s.current()!!.archiveHash==hash(archive));checks.put("process-restart resume uses durable per-range checkpoints")
  s.cleanupUnused();check(first.directory.exists());ResourcePins.release(pinned);s.cleanupUnused();check(!first.directory.exists());checks.put("old mapped generation retained until pin release")
  archive=archive+byteArrayOf(1);manifest=manifest(archive,catalog,model,"three");ignoreRange=true;s.check(true);check(s.current()!!.archiveHash==hash(archive));ignoreRange=false;checks.put("HTTP 200 to Range falls back to clean single stream")
  archive=archive+byteArrayOf(2);manifest=manifest(archive,catalog,model,"four")
  val began=java.util.concurrent.CountDownLatch(4);val cancelled=java.util.concurrent.atomic.AtomicBoolean(false)
  val blocked=object:ResourceTransport{override fun cancel(){cancelled.set(true)};override fun open(url:String,headers:Map<String,String>):RemoteResponse{
   if(url.endsWith("manifest"))return RemoteResponse(200,emptyMap(),manifest.toString().byteInputStream())
   val match=Regex("bytes=(\\d+)-(\\d+)").matchEntire(headers["Range"]!!)!!;val start=match.groupValues[1];val end=match.groupValues[2]
   return RemoteResponse(206,mapOf("Content-Range" to "bytes $start-$end/${archive.size}"),object:InputStream(){var signalled=false;override fun read():Int{if(!signalled){signalled=true;began.countDown()};while(!cancelled.get())Thread.sleep(10);throw IOException("Disconnected")};override fun read(b:ByteArray,o:Int,l:Int):Int=read()})
  }}
  val cancelling=ResourceStore(ctx,blocked,"https://test.invalid/manifest",progress={},validate={})
  val job=Thread{runCatching{cancelling.check(true)}};job.start();check(began.await(15,java.util.concurrent.TimeUnit.SECONDS));cancelling.cancel();job.interrupt();job.join(15000);check(!job.isAlive&&cancelled.get());val currentBefore=s.current()!!.archiveHash
  s=store();s.resume();check(s.current()!!.archiveHash==hash(archive)&&s.current()!!.archiveHash!=currentBefore);checks.put("timeout-style cancellation disconnects/quiesces all writers; preallocated length is not treated as completion")
  archive=archive+byteArrayOf(3);manifest=manifest(archive,catalog,model,"five")
  val renamed=java.util.concurrent.CountDownLatch(1);val commit=java.util.concurrent.CountDownLatch(1);val cleaned=java.util.concurrent.CountDownLatch(1);val read=java.util.concurrent.CountDownLatch(1)
  val swapping=ResourceStore(ctx,transport,"https://test.invalid/manifest",progress={},validate={},beforeActivationCommit={renamed.countDown();check(commit.await(15,java.util.concurrent.TimeUnit.SECONDS))})
  var swapError:Throwable?=null;var readBundle:BundleSnapshot?=null;val swap=Thread{try{swapping.check(true)}catch(e:Throwable){swapError=e}};swap.start();check(renamed.await(15,java.util.concurrent.TimeUnit.SECONDS))
  val cleaner=Thread{s.cleanupUnused();cleaned.countDown()};val reader=Thread{readBundle=s.current();read.countDown()};cleaner.start();reader.start();Thread.sleep(150);check(cleaned.count==1L&&read.count==1L);commit.countDown();swap.join(15000);cleaner.join(15000);reader.join(15000)
  check(swapError==null&&!swap.isAlive&&!cleaner.isAlive&&!reader.isAlive);check(readBundle!!.directory==s.current()!!.directory&&s.current()!!.catalog.isFile);checks.put("activation lock excludes concurrent cleanup and AtomicFile readers until pointer commit")
  val report=JSONObject().put("status","passed").put("checks",checks).put("count",checks.length());File(i.targetContext.filesDir,"resource-validation.json").writeText(report.toString(2));i.finish(0,Bundle().apply{putString("summary",report.toString())})
 }catch(e:Throwable){val r=JSONObject().put("status","failed").put("checks",checks).put("error",e.stackTraceToString());File(i.targetContext.filesDir,"resource-validation.json").writeText(r.toString(2));i.finish(1,Bundle().apply{putString("error",e.stackTraceToString())})}}
 private inline fun <reified T:Throwable> expect(block:()->Unit){val error=runCatching{block()}.exceptionOrNull();check(error is T){"Expected ${T::class.java.simpleName}, got $error"}}
 private fun manifest(a:ByteArray,c:ByteArray,m:ByteArray,v:String)=JSONObject().put("formatVersion",1).put("bundleVersion",v).put("archive",JSONObject().put("url","https://test.invalid/archive").put("bytes",a.size).put("sha256",hash(a))).put("files",JSONObject().put("catalog.bin",JSONObject().put("bytes",c.size).put("sha256",hash(c))).put("nmfp.tflite",JSONObject().put("bytes",m.size).put("sha256",hash(m))))
}
