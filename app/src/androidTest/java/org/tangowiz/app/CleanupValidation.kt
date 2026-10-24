package org.tangowiz.app

import android.app.Instrumentation
import android.content.ContextWrapper
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.zip.ZipFile

object CleanupValidation {
 fun run(i:Instrumentation){
  val checks=JSONArray();val report=JSONObject()
  try{
   val ctx=object:ContextWrapper(i.targetContext){override fun getFilesDir()=File(super.getFilesDir(),"cleanup-test").apply{mkdirs()};override fun getCacheDir()=File(super.getCacheDir(),"cleanup-test").apply{mkdirs()}}
   ctx.filesDir.deleteRecursively();ctx.cacheDir.deleteRecursively();ctx.filesDir.mkdirs();ctx.cacheDir.mkdirs()
   val store=Store(ctx);val old=File(store.root,"2026-10-23T18-30-29Z").apply{mkdirs()}
   File(old,"microphone-0.wav").writeBytes(ByteArray(1024*1024){17});File(old,"timing.jsonl").writeText("old timing")
   val sid="2026-10-24T18-30-29Z";val current=SessionFiles.begin(store.root,sid)
   val wav=File(current,"microphone-0.wav");val writer=WavWriter(wav);writer.write(ShortArray(8000){123})
   val timing=File(current,"timing.jsonl");store.append(timing,JSONObject().put("stage","before"))
   val fp=File(current,"fingerprints.bin").apply{writeBytes(ByteArray(524){3})};val lifecycle=File(store.root,"lifecycle.jsonl").apply{writeText("active lifecycle")}
   fun row(session:String,n:Int)=JSONObject().put("session",session).put("id","track-$n")
   store.append(store.history,row(old.name,0));store.append(store.history,row(sid,1))
   val resources=File(ctx.filesDir,"resources-v1").apply{mkdirs()};val catalog=File(resources,"catalog.bin").apply{writeText("keep index")}
   val archive=File(ctx.cacheDir,"tangowiz-debug-12345.zip").apply{writeText("keep export")}
   val outside=File(ctx.filesDir,"outside").apply{mkdirs()};val outsideFile=File(outside,"must-stay").apply{writeText("safe")}
   Files.createSymbolicLink(File(old,"external-link").toPath(),outside.toPath())
   val stale=File(ctx.filesDir,"session-cleanup-00000000-0000-0000-0000-000000000000").apply{mkdirs()};File(stale,"abandoned.wav").writeBytes(ByteArray(77))
   val start=CountDownLatch(1);val producer=Thread{start.await();repeat(100){store.append(store.history,row(sid,it+2))}}
   producer.start();start.countDown();val result=SessionFiles.clearOlder(ctx);producer.join()
   check(!old.exists()&&current.isDirectory&&result.retained==1&&result.failed==0&&result.bytes>=1024*1024)
   check(store.history().size==101&&store.history().all{it.getString("session")==sid})
   checks.put("older sessions removed; all current history retained during concurrent appends")
   writer.write(ShortArray(8000){456});store.append(timing,JSONObject().put("stage","after"));writer.close()
   check(wav.length()==32044L&&timing.readLines().size==2&&fp.length()==524L&&lifecycle.readText()=="active lifecycle")
   checks.put("open WAV continues writing across cleanup; current timing, fingerprints and lifecycle remain intact")
   check(catalog.readText()=="keep index"&&archive.readText()=="keep export"&&outsideFile.readText()=="safe"&&!stale.exists())
   checks.put("resources and exported ZIP untouched; symbolic links not followed; interrupted cleanup recovered")
   val blocked=File(store.root,"older-exported-session").apply{mkdirs()};File(blocked,"recording.wav").writeText("retain during export")
   val before=store.history.readText();var refused=false
   val exported=store.export(progress={if(!refused){val e=runCatching{SessionFiles.clearOlder(ctx)}.exceptionOrNull();check(e?.message?.contains("export")==true);refused=true;check(blocked.exists()&&store.history.readText()==before)}})
   check(refused);ZipFile(exported).use{z->check(z.getEntry("$sid/microphone-0.wav")!=null)}
   checks.put("cleanup is rejected before modifying files while an export is reading sessions")
   SessionFiles.end(store.root,sid);val final=SessionFiles.clearOlder(ctx)
   check(!current.exists()&&!blocked.exists()&&store.history().isEmpty()&&final.retained==0&&final.failed==0)
   checks.put("closed sessions and their history become eligible; export protection is released")
   report.put("status","passed").put("checks",checks).put("count",checks.length())
   File(i.targetContext.filesDir,"cleanup-validation.json").writeText(report.toString(2));i.finish(0,Bundle().apply{putString("summary",report.toString())})
  }catch(e:Throwable){report.put("status","failed").put("checks",checks).put("error",e.stackTraceToString());File(i.targetContext.filesDir,"cleanup-validation.json").writeText(report.toString(2));i.finish(1,Bundle().apply{putString("error",e.stackTraceToString())})}
 }
}
