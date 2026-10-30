package org.tangowiz.app

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

data class RecognitionState(val listening:Boolean=false,val phase:String="",val best:Candidate?=null,val second:Candidate?=null,val seconds:Double=0.0,val error:String="",val resident:Boolean=false,val exited:Boolean=false)
data class ExportProgress(val copiedBytes:Long,val totalBytes:Long,val filesDone:Int,val totalFiles:Int)
object LiveState {
 @Volatile var current=RecognitionState()
 val historyVersion=java.util.concurrent.atomic.AtomicLong(0)
 val listeners=CopyOnWriteArrayList<()->Unit>()
 fun publish(state:RecognitionState){ current=state; listeners.forEach{it()} }
}
class Store(val context:Context) {
 val root=File(context.filesDir,"logs").apply{mkdirs()}
 val history=File(root,"history.jsonl")
 val prefs=context.getSharedPreferences("settings",0)
 var recognitionSpeeds:List<Double>
  get()=runCatching{RecognitionSpeeds.parse(prefs.getString("recognition_speed_offsets",null)?:error("Unset"))}.getOrElse{RecognitionSpeeds.defaults}
  set(v){prefs.edit().putString("recognition_speed_offsets",RecognitionSpeeds.validate(v).joinToString(",")).commit()}
 var microphone:String get()=prefs.getString("microphone","phone")?:"phone";set(v){require(v in setOf("phone","watch"));prefs.edit().putString("microphone",v).commit()}
 var watchUpdateInterval:Long get()=prefs.getLong("watch_update_interval",5000L).coerceIn(2000L,60000L);set(v){prefs.edit().putLong("watch_update_interval",v.coerceIn(2000L,60000L)).commit()}
 var audio:Boolean get()=prefs.getBoolean("audio",false); set(v){prefs.edit().putBoolean("audio",v).apply()}
 var timing:Boolean get()=prefs.getBoolean("timing",false); set(v){prefs.edit().putBoolean("timing",v).apply()}
 fun append(file:File,json:JSONObject)=synchronized(SessionFiles.lock){file.appendText(json.toString()+"\n")}
 fun history():List<JSONObject> = synchronized(SessionFiles.lock){if(!history.exists()) emptyList() else java.io.RandomAccessFile(history,"r").use{f->val start=(f.length()-512000).coerceAtLeast(0);f.seek(start);val bytes=ByteArray((f.length()-start).toInt());f.readFully(bytes);bytes.toString(Charsets.UTF_8).lineSequence().mapNotNull{runCatching{JSONObject(it)}.getOrNull()}.toList().takeLast(1000)}}
 fun result(c:Candidate,second:Candidate?, seconds:Double,session:String,rawResult:JSONObject?=null){
  append(history,JSONObject().put("time",Instant.now().toString()).put("session",session).put("audio_seconds",seconds).put("id",c.id).put("sourceIds",JSONArray(c.aliases.sorted())).put("metadataAlternatives",c.alternativesJson()).put("presentationContributors",JSONArray(c.presentationContributors.map{raw->val contributor=JSONObject(raw);JSONObject().put("id",contributor.optString("id")).put("sourceIds",contributor.optJSONArray("sourceIds")?:JSONArray()).put("score",contributor.optDouble("score",0.0)).put("speed",contributor.optDouble("speed",1.0))})).put("orchestra",c.orchestra).put("title",c.title).put("singer",c.singer).put("year",c.year).put("score",c.score).put("confidence_score",c.confidence).put("speed",c.speed).put("state",if(c.confidence>=70) "strong" else "tentative").put("runner_up",second?.id ?: ""));if(rawResult!=null){val dir=File(root,session).apply{mkdirs()};append(File(dir,"matches.jsonl"),JSONObject().put("time",Instant.now().toString()).put("audio_seconds",seconds).put("result",rawResult))};LiveState.historyVersion.incrementAndGet()
 }
 fun export(progress:(ExportProgress)->Unit={},cancelled:()->Boolean={false}):File {
  SessionFiles.beginExport(root)
  try{return exportFiles(progress,cancelled)}finally{SessionFiles.endExport(root)}
 }
 private fun exportFiles(progress:(ExportProgress)->Unit,cancelled:()->Boolean):File {
  fun checkCancelled(){if(cancelled()||Thread.currentThread().isInterrupted)throw java.io.InterruptedIOException("Export cancelled")}
  data class Input(val file:File,val length:Long,val wav:Boolean)
  val files=root.walkTopDown().onEnter{checkCancelled();!java.nio.file.Files.isSymbolicLink(it.toPath())}.filter{it.isFile&&!java.nio.file.Files.isSymbolicLink(it.toPath())}.map{file->
   checkCancelled();var length=file.length();val wav=file.extension.equals("wav",true)&&length>=44
   if(file.name=="fingerprints.bin")length-=length%524
   if(wav)length=44+(length-44)/2*2
   Input(file,length,wav)
  }.toList()
  val total=files.sumOf{it.length};var copied=0L;var done=0;var notified=0L
  fun notifyProgress(force:Boolean=false){val now=System.nanoTime();if(force||now-notified>=200_000_000L){progress(ExportProgress(copied,total,done,files.size));notified=now}}
  notifyProgress(true)
  val staging=File.createTempFile("tangowiz-export-",".partial",context.cacheDir)
  val name=java.math.BigInteger(java.util.UUID.randomUUID().toString().replace("-",""),16).toString()
  val out=File(context.cacheDir,"tangowiz-debug-$name.zip")
  try{
   java.io.FileOutputStream(staging).use{fileOut->
    val buffered=java.io.BufferedOutputStream(fileOut,262144)
    java.util.zip.ZipOutputStream(buffered).use{zip->
     zip.setLevel(1)
     val buffer=ByteArray(262144)
     for(input in files){
      checkCancelled();zip.putNextEntry(java.util.zip.ZipEntry(input.file.relativeTo(root).invariantSeparatorsPath))
      java.io.RandomAccessFile(input.file,"r").use{src->
       var remaining=input.length
       if(input.wav){val data=input.length-44;require(data<=0xffffffffL-36){"Recording is too large for a WAV export"};val h=java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN);h.put("RIFF".toByteArray()).putInt((data+36).toInt()).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16).put("data".toByteArray()).putInt(data.toInt());zip.write(h.array());src.seek(44);remaining-=44;copied+=44;notifyProgress()}
       while(remaining>0){checkCancelled();val n=src.read(buffer,0,minOf(buffer.size.toLong(),remaining).toInt());if(n<=0)throw java.io.EOFException("A recording changed during export; pause and try again");zip.write(buffer,0,n);remaining-=n;copied+=n;notifyProgress()}
      }
      zip.closeEntry();done++;notifyProgress(true)
     }
     context.assets.list("notices")?.forEach{name->checkCancelled();zip.putNextEntry(java.util.zip.ZipEntry("notices/$name"));context.assets.open("notices/$name").use{it.copyTo(zip)};zip.closeEntry()}
     val metadata=JSONObject().put("exported_at",Instant.now().toString()).put("files",files.size).put("snapshot_bytes",total)
     runCatching{context.packageManager.getPackageInfo(context.packageName,0)}.onSuccess{metadata.put("app_version",it.versionName).put("app_version_code",if(android.os.Build.VERSION.SDK_INT>=28)it.longVersionCode else it.versionCode.toLong())}.onFailure{metadata.put("app_version_error",it.message)}
     runCatching{val current=ResourceStore(context).pinCurrent();if(current==null)metadata.put("active_resources",JSONObject.NULL) else try{val resources=JSONObject().put("format",current.formatVersion).put("bundle_version",current.version).put("archive_sha256",current.archiveHash);runCatching{JSONObject(File(current.directory,"verified.json").readText())}.onSuccess{resources.put("manifest",it)}.onFailure{resources.put("manifest_error",it.message)};metadata.put("active_resources",resources)}finally{ResourcePins.release(current)}}.onFailure{metadata.put("resource_error",it.message)}
     zip.putNextEntry(java.util.zip.ZipEntry("export_metadata.json"));zip.write(metadata.toString(2).toByteArray());zip.closeEntry()
     zip.putNextEntry(java.util.zip.ZipEntry("README.txt"));zip.write("TangoWiz microphone audio, fingerprints, results and optional timing logs. Confidence scores are heuristic, not calibrated probabilities. Times UTC. No automatic uploads. Files are snapshots; WAV headers are repaired for saved PCM and incomplete fingerprint records are omitted.\n".toByteArray());zip.closeEntry()
     checkCancelled();zip.finish();zip.flush();buffered.flush();fileOut.fd.sync()
    }
   }
   checkCancelled();if(!staging.renameTo(out))throw java.io.IOException("Cannot finish the export file")
   notifyProgress(true);return out
  }catch(e:Throwable){staging.delete();throw e}
 }
}
