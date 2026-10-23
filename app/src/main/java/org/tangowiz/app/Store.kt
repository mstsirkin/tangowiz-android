package org.tangowiz.app

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

data class RecognitionState(val listening:Boolean=false,val phase:String="",val best:Candidate?=null,val second:Candidate?=null,val seconds:Double=0.0,val error:String="",val resident:Boolean=false,val exited:Boolean=false)
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
 var audio:Boolean get()=prefs.getBoolean("audio",false); set(v){prefs.edit().putBoolean("audio",v).apply()}
 var timing:Boolean get()=prefs.getBoolean("timing",false); set(v){prefs.edit().putBoolean("timing",v).apply()}
 @Synchronized fun append(file:File,json:JSONObject){file.appendText(json.toString()+"\n")}
 fun history():List<JSONObject> = if(!history.exists()) emptyList() else java.io.RandomAccessFile(history,"r").use{f->val start=(f.length()-512000).coerceAtLeast(0);f.seek(start);val bytes=ByteArray((f.length()-start).toInt());f.readFully(bytes);bytes.toString(Charsets.UTF_8).lineSequence().mapNotNull{runCatching{JSONObject(it)}.getOrNull()}.toList().takeLast(1000)}
 fun result(c:Candidate,second:Candidate?, seconds:Double,session:String,rawResult:JSONObject?=null){
  append(history,JSONObject().put("time",Instant.now().toString()).put("session",session).put("audio_seconds",seconds).put("id",c.id).put("sourceIds",JSONArray(c.aliases.sorted())).put("metadataAlternatives",c.alternativesJson()).put("orchestra",c.orchestra).put("title",c.title).put("singer",c.singer).put("year",c.year).put("score",c.score).put("confidence_score",c.confidence).put("speed",c.speed).put("state",if(c.confidence>=70) "strong" else "tentative").put("runner_up",second?.id ?: ""));if(rawResult!=null){val dir=File(root,session).apply{mkdirs()};append(File(dir,"matches.jsonl"),JSONObject().put("time",Instant.now().toString()).put("audio_seconds",seconds).put("result",rawResult))};LiveState.historyVersion.incrementAndGet()
 }
 fun export():File {
  val out=File(context.cacheDir,"tangowiz-debug-${System.currentTimeMillis()}.zip")
  java.util.zip.ZipOutputStream(out.outputStream().buffered()).use{zip->
   root.walkTopDown().filter{it.isFile}.forEach{file->
    var length=file.length();val wav=file.extension=="wav"&&length>=44
    if(file.name=="fingerprints.bin")length-=length%524
    zip.putNextEntry(java.util.zip.ZipEntry(file.relativeTo(root).path))
    file.inputStream().use{src->
     if(wav){val data=(length-44)/2*2;val h=java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN);h.put("RIFF".toByteArray()).putInt((data+36).toInt()).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16).put("data".toByteArray()).putInt(data.toInt());zip.write(h.array());src.skip(44);length=data}
     val buffer=ByteArray(65536);while(length>0){val n=src.read(buffer,0,minOf(buffer.size.toLong(),length).toInt());if(n<=0)break;zip.write(buffer,0,n);length-=n}
    };zip.closeEntry()
   }
   context.assets.list("notices")?.forEach{name->zip.putNextEntry(java.util.zip.ZipEntry("notices/$name"));context.assets.open("notices/$name").use{it.copyTo(zip)};zip.closeEntry()}
   zip.putNextEntry(java.util.zip.ZipEntry("README.txt"));zip.write("TangoWiz microphone audio, fingerprints, results and optional timing logs. Confidence scores are heuristic, not calibrated probabilities. Times UTC. No automatic uploads.\n".toByteArray());zip.closeEntry()
  }
  return out
 }
}
