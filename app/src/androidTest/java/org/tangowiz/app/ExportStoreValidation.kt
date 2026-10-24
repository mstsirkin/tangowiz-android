package org.tangowiz.app

import android.app.Instrumentation
import android.content.ContextWrapper
import android.os.Bundle
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.CRC32
import java.util.zip.ZipFile

object ExportStoreValidation {
 fun run(i:Instrumentation){val checks=JSONArray();val report=JSONObject();try{
  fun isolated(name:String)=object:ContextWrapper(i.targetContext){override fun getFilesDir()=File(super.getFilesDir(),name).apply{mkdirs()};override fun getCacheDir()=File(super.getCacheDir(),name).apply{mkdirs()}}
  val ctx=isolated("export-store-test");ctx.filesDir.deleteRecursively();ctx.cacheDir.deleteRecursively();ctx.filesDir.mkdirs();ctx.cacheDir.mkdirs();val store=Store(ctx)
  val session=File(store.root,"synthetic-three-hours").apply{mkdirs()};val wav=File(session,"microphone-0.wav");val payload=172800000L
  val originalCrc=CRC32();val dataCrc=CRC32();val random=java.util.Random(4141);val chunk=ByteArray(262144);val header=ByteArray(44)
  wav.outputStream().buffered(262144).use{out->out.write(header);originalCrc.update(header);var left=payload;while(left>0){random.nextBytes(chunk);val n=minOf(left,chunk.size.toLong()).toInt();out.write(chunk,0,n);originalCrc.update(chunk,0,n);dataCrc.update(chunk,0,n);left-=n};out.write(127);originalCrc.update(127)}
  val fingerprints=File(session,"fingerprints.bin").apply{writeBytes(ByteArray(524*3+17){(it%251).toByte()})};val history=File(store.root,"history.jsonl").apply{writeText("{\"id\":\"synthetic-export-only\"}\n")}
  val events=mutableListOf<ExportProgress>();val began=SystemClock.elapsedRealtime();val archive=store.export({events+=it});val elapsed=SystemClock.elapsedRealtime()-began
  check(events.first().copiedBytes==0L&&events.first().totalFiles==3);check(events.last().copiedBytes==payload+44+524*3+history.length());check(events.last().filesDone==3);check(events.zipWithNext().all{(a,b)->a.copiedBytes<=b.copiedBytes&&a.filesDone<=b.filesDone});checks.put("three-hour-equivalent audio exports with accurate monotonic byte/file progress")
  check(archive.name.matches(Regex("tangowiz-debug-[0-9]+\\.zip")));check(ctx.cacheDir.listFiles()!!.none{it.extension=="partial"})
  ZipFile(archive).use{zip->
   val entry=zip.getEntry("synthetic-three-hours/microphone-0.wav");check(entry.size==payload+44)
   zip.getInputStream(entry).use{raw->val input=java.io.DataInputStream(raw);val repaired=ByteArray(44);input.readFully(repaired);val b=java.nio.ByteBuffer.wrap(repaired).order(java.nio.ByteOrder.LITTLE_ENDIAN);check(String(repaired,0,4)=="RIFF"&&b.getInt(4).toLong()==payload+36&&String(repaired,8,8)=="WAVEfmt "&&b.getInt(24)==8000&&b.getInt(40).toLong()==payload);val crc=CRC32();var length=0L;while(true){val n=input.read(chunk);if(n<0)break;crc.update(chunk,0,n);length+=n};check(length==payload&&crc.value==dataCrc.value)}
   check(zip.getEntry("synthetic-three-hours/fingerprints.bin").size==524L*3);check(zip.getInputStream(zip.getEntry("history.jsonl")).bufferedReader().readText()==history.readText());val metadata=JSONObject(zip.getInputStream(zip.getEntry("export_metadata.json")).bufferedReader().readText());check(metadata.getInt("files")==3&&metadata.has("app_version")&&metadata.has("active_resources"));check(zip.getEntry("README.txt")!=null)
  };checks.put("ZIP preserves PCM/history, repairs unclosed WAV header, trims partial sample/fingerprint record and includes app/resource diagnostics")
  val after=CRC32();wav.inputStream().buffered().use{input->while(true){val n=input.read(chunk);if(n<0)break;after.update(chunk,0,n)}};check(wav.length()==payload+45&&after.value==originalCrc.value&&fingerprints.length()==524L*3+17);checks.put("saved source recordings and incomplete fingerprints remain byte-for-byte unchanged")
  val failures=isolated("export-store-failure-test");failures.filesDir.deleteRecursively();failures.cacheDir.deleteRecursively();failures.filesDir.mkdirs();failures.cacheDir.mkdirs();val failedStore=Store(failures);val source=File(failedStore.root,"input.bin").apply{writeBytes(ByteArray(4*1024*1024){1})}
  var calls=0;val cancel=runCatching{failedStore.export(cancelled={++calls>6})}.exceptionOrNull();check(cancel is java.io.InterruptedIOException);check(source.length()==4L*1024*1024&&failures.cacheDir.listFiles()!!.isEmpty());checks.put("cancellation cleans incomplete ZIP without deleting source files")
  val eof=runCatching{failedStore.export(progress={if(it.copiedBytes==0L)RandomAccessFile(source,"rw").use{f->f.setLength(1)}})}.exceptionOrNull();check(eof is java.io.EOFException);check(failures.cacheDir.listFiles()!!.isEmpty()&&source.exists());checks.put("mid-export truncation reports failure and never exposes a partial archive")
  report.put("status","passed").put("checks",checks).put("count",checks.length()).put("synthetic_audio_seconds",10800).put("input_audio_bytes",payload).put("export_elapsed_ms",elapsed).put("archive_bytes",archive.length()).put("fixture_wav",wav.absolutePath).put("archive",archive.absolutePath)
  File(i.targetContext.filesDir,"export-store-validation.json").writeText(report.toString(2));i.finish(0,Bundle().apply{putString("summary",report.toString())})
 }catch(e:Throwable){report.put("status","failed").put("checks",checks).put("error",e.stackTraceToString());File(i.targetContext.filesDir,"export-store-validation.json").writeText(report.toString(2));i.finish(1,Bundle().apply{putString("error",e.stackTraceToString())})}}
}
