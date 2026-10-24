package org.tangowiz.app

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import org.tangowiz.link.AudioWire
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Synthetic transport integration, not a paired-watch/Data Layer test. */
object WatchInputValidation {
 private const val EXPECTED="palazzo-f57cf688973fbd86315e1d87"
 fun run(i:Instrumentation){
  val ctx=i.targetContext
  val store=Store(ctx)
  val controls=ctx.getSharedPreferences("watch-link",0)
  val originalSettings=snapshot(store.prefs)
  val originalControls=snapshot(controls)
  val report=JSONObject().put("test","synthetic_watch_input").put("actual_paired_wear_transport",false)
  val checks=JSONArray()
  var activity:Activity?=null
  var serviceStarted=false
  var failure:Throwable?=null
  val log=File(store.root,"lifecycle.jsonl")
  try{
   stopExisting(i)
   i.runOnMainSync{LiveState.publish(RecognitionState())}
   store.microphone="phone";store.audio=false;store.timing=false;store.watchUpdateInterval=5000
   PhoneWatch.init(ctx)
   PhoneWatch.state()
   validateAtomicSettings(store,controls)
   checks.put("Malformed audio/timing values do not partially change settings or revision")
   validateCommands(i,store,controls)
   checks.put("Explicit commands apply once, exact retries are cached, stale revisions are rejected")
   i.waitForIdleSync()

   val fixture=File("/data/local/tmp/event.pcm")
   val bundle=ResourceStore(ctx).current()
   if(bundle==null||!fixture.isFile){
    report.put("capture_skipped",if(bundle==null)"Installed resources unavailable" else "event.pcm unavailable")
    report.put("status","passed_controls_capture_skipped")
   }else{
    val bytes=fixture.readBytes()
    check(bytes.size%4==0&&bytes.size>=80000*4){"Expected at least ten seconds of float32/8k event audio"}
    val pcm=FloatArray(80000)
    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(pcm)
    check(pcm.all{it.isFinite()}){"Invalid event PCM fixture"}
    val shorts=ShortArray(pcm.size){(pcm[it]*32768f).roundToInt().coerceIn(-32768,32767).toShort()}
    stopExisting(i)
    store.microphone="watch";store.audio=true;store.timing=true
    ResourceStore(ctx).automaticChecks=false
    activity=i.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    i.waitForIdleSync()
    val before=lineCount(log)
    fun events()=readEvents(log,before)
    val oldSessions=store.root.listFiles().orEmpty().filter{it.isDirectory}.map{it.name}.toSet()
    ctx.startForegroundService(Intent(ctx,RecognitionService::class.java).setAction(RecognitionService.RESUME))
    serviceStarted=true
    await("Watch engine readiness",90000){PhoneWatch.engineReady&&LiveState.current.listening}
    await("First watch capture",90000){events().count{it.optString("event")=="capture_started"}==1}
    val firstStream=UUID.randomUUID().toString()
    inject(firstStream,shorts,20)
    await("Tanturi recognition from injected watch audio",120000){
     LiveState.current.best?.let{it.id==EXPECTED||EXPECTED in it.aliases}==true&&LiveState.current.seconds>=9.9
    }
    val first=LiveState.current.best!!
    check(normalize(first.display("orchestra")).removePrefix("orquesta tipica ").removePrefix("orquesta ")=="ricardo tanturi"){
     "Unexpected watch-input orchestra: ${first.display("orchestra")}"
    }
    report.put("first_match",JSONObject().put("id",first.id).put("source_ids",JSONArray(first.aliases.sorted())).put("orchestra",first.display("orchestra")).put("score",first.score))
    checks.put("Ten seconds of framed PCM16 watch input identify the known Tanturi recording")

    // A new channel epoch/UUID must end the old capture and reuse the resident engine.
    WatchAudioInput.close()
    val secondStream=UUID.randomUUID().toString()
    inject(secondStream,shorts.copyOfRange(0,4000),1)
    await("New UUID starts a fresh capture",90000){events().count{it.optString("event")=="capture_started"}==2}
    inject(secondStream,shorts.copyOfRange(4000,16000),3,firstSequence=1)
    await("Second watch capture consumes fresh input",90000){LiveState.current.seconds>=1.9}
    ctx.startService(Intent(ctx,RecognitionService::class.java).setAction(RecognitionService.PAUSE))
    await("Watch capture pause",90000){events().any{it.optString("event")=="pause_complete"}&&!LiveState.current.listening}
    val lifecycle=events()
    check(lifecycle.count{it.optString("event")=="model_loaded"}==1){"Model reloaded across watch reconnect"}
    check(lifecycle.count{it.optString("event")=="index_loaded"}==1){"Index reloaded across watch reconnect"}
    check(lifecycle.count{it.optString("event")=="capture_started"}==2){"Unexpected capture-session count"}
    check(lifecycle.filter{it.optString("event")=="capture_started"}.all{it.optString("source")=="watch"}){"Phone microphone fallback occurred"}
    check(lifecycle.none{it.optString("event")=="session_error"}){"Watch capture session failed: $lifecycle"}
    val sessions=store.root.listFiles().orEmpty().filter{it.isDirectory&&it.name !in oldSessions&&File(it,"session.json").isFile}
    check(sessions.size==2){"Expected two distinct recorded watch sessions, got ${sessions.map{it.name}}"}
    val saved=JSONArray()
    for(dir in sessions){
     val metadata=JSONObject(File(dir,"session.json").readText())
     check(metadata.optString("input_source")=="watch"){"Missing watch input provenance"}
     val wav=dir.listFiles().orEmpty().singleOrNull{it.extension=="wav"&&it.length()>44}?:error("Watch audio WAV missing in ${dir.name}")
     val header=wav.inputStream().use{it.readBytes().copyOfRange(0,44)}
     check(String(header.copyOfRange(0,4),Charsets.US_ASCII)=="RIFF"&&String(header.copyOfRange(8,12),Charsets.US_ASCII)=="WAVE"){"Invalid saved WAV header"}
     check(ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(24)==8000){"Unexpected saved WAV rate"}
     check(ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(40).toLong()==wav.length()-44){"Saved WAV did not close consistently"}
     saved.put(JSONObject().put("session",dir.name).put("input_source","watch").put("wav_bytes",wav.length()).put("timing_logged",File(dir,"timing.jsonl").length()>0))
    }
    check(sessions.any{it.listFiles().orEmpty().any{f->f.extension=="wav"&&f.length()==160044L}}){"Full ten-second watch recording not saved"}
    report.put("sessions",saved).put("lifecycle",JSONArray(lifecycle)).put("model_loaded",1).put("index_loaded",1).put("capture_started",2).put("resource_format",bundle.formatVersion)
    checks.put("Reconnect creates another watch session while model/index load only once; both WAVs close with watch provenance")
    report.put("status","passed")
   }
  }catch(e:Throwable){failure=e;report.put("status","failed").put("error",e.stackTraceToString())}
  finally{
   runCatching{if(serviceStarted||LiveState.current.resident||LiveState.current.listening)stopExisting(i)}.onFailure{if(failure==null){failure=it;report.put("status","failed").put("error",it.stackTraceToString())}}
   runCatching{WatchAudioInput.close()}
   runCatching{activity?.let{a->i.runOnMainSync{if(!a.isFinishing&&!a.isDestroyed)a.finish()}};i.waitForIdleSync()}
   // Drain already submitted control work before restoring persistent state.
   runCatching{barrier("worker",15000);barrier("network",45000)}
   var restored=false
   runCatching{restore(store.prefs,originalSettings);restore(controls,originalControls);restored=true}.onFailure{if(failure==null){failure=it;report.put("status","failed").put("error",it.stackTraceToString())}}
   report.put("checks",checks).put("preferences_restored",restored)
   File(ctx.filesDir,"watch-input-validation.json").writeText(report.toString(2))
   i.finish(if(failure==null)0 else 1,Bundle().apply{putString("report",report.toString())})
  }
 }

 private fun validateAtomicSettings(store:Store,prefs:SharedPreferences){
  val apply=PhoneWatch::class.java.getDeclaredMethod("applyValues",JSONObject::class.java).apply{isAccessible=true}
  for(field in listOf("audio","timing")){
   val before=values(store)
   val revision=prefs.getLong("revision",0)
   val invalid=JSONObject().put("microphone","watch").put("updateIntervalMs",7000).put("listening",false).put("audio",true).put("timing",true).put(field,"invalid-boolean")
   val error=runCatching{apply.invoke(PhoneWatch,invalid)}.exceptionOrNull()
   check(error is InvocationTargetException){"Malformed settings were not rejected"}
   check(values(store)==before){"Malformed $field command partially changed persistent settings"}
   check(prefs.getLong("revision",0)==revision){"Malformed settings changed revision"}
  }
 }
 private fun validateCommands(i:Instrumentation,store:Store,prefs:SharedPreferences){
  val revision=PhoneWatch.state().getLong("revision")
  val id="watch-validation-${UUID.randomUUID()}"
  val command=JSONObject().put("requestId",id).put("kind","set").put("expectedRevision",revision).put("listening",false).put("microphone","phone").put("audio",true).put("timing",true).put("updateIntervalMs",6000)
  val bytes=command.toString().toByteArray(Charsets.UTF_8)
  PhoneWatch.command(i.targetContext,"synthetic-watch-validation-node",bytes)
  barrier("worker",15000);i.waitForIdleSync()
  val ack=handled(prefs,id)
  check(ack.getBoolean("ok")){"Valid explicit settings command failed: $ack"}
  check(store.audio&&store.timing&&store.watchUpdateInterval==6000L&&store.microphone=="phone")
  val appliedRevision=prefs.getLong("revision",0)
  check(appliedRevision==revision+1){"Explicit command incremented revision more than once"}
  val settings=values(store)
  PhoneWatch.command(i.targetContext,"synthetic-watch-validation-node",bytes)
  barrier("worker",15000);i.waitForIdleSync()
  check(prefs.getLong("revision",0)==appliedRevision&&values(store)==settings){"Exact command retry applied twice"}
  check(handled(prefs,id).getBoolean("ok"))
  val staleId="watch-stale-${UUID.randomUUID()}"
  val stale=JSONObject().put("requestId",staleId).put("kind","set").put("expectedRevision",revision).put("listening",false).put("microphone","watch").put("audio",false).put("timing",false).put("updateIntervalMs",7000)
  PhoneWatch.command(i.targetContext,"synthetic-watch-validation-node",stale.toString().toByteArray(Charsets.UTF_8))
  barrier("worker",15000);i.waitForIdleSync()
  check(!handled(prefs,staleId).getBoolean("ok")){"Stale settings revision was accepted"}
  check(prefs.getLong("revision",0)==appliedRevision&&values(store)==settings){"Stale command changed settings"}
 }
 private fun handled(prefs:SharedPreferences,id:String)=JSONObject(prefs.getString("handled","{}")?:"{}").getJSONObject(id).getJSONObject("ack")
 private fun values(store:Store)=listOf(store.microphone,store.audio,store.timing,store.watchUpdateInterval)
 private fun barrier(name:String,timeout:Long){
  val field=PhoneWatch::class.java.getDeclaredField(name).apply{isAccessible=true}
  (field.get(PhoneWatch) as ExecutorService).submit{}.get(timeout,TimeUnit.MILLISECONDS)
 }
 private fun stopExisting(i:Instrumentation){
  if(!LiveState.current.resident&&!LiveState.current.listening)return
  i.targetContext.startService(Intent(i.targetContext,RecognitionService::class.java).setAction(RecognitionService.EXIT))
  await("Engine exits",90000){LiveState.current.exited&&!LiveState.current.resident}
  @Suppress("DEPRECATION")
  fun registered()=i.targetContext.getSystemService(android.app.ActivityManager::class.java).getRunningServices(100)
   .any{it.service.className==RecognitionService::class.java.name}
  await("Exited service is removed",15000){!registered()}
  i.waitForIdleSync()
 }
 private fun inject(stream:String,pcm:ShortArray,frames:Int,firstSequence:Long=0){
  check(pcm.size==frames*AudioWire.FRAME_SAMPLES)
  val header=ByteArrayOutputStream().also{AudioWire.writeHeader(it,stream)}.toByteArray()
  check(AudioWire.readHeader(ByteArrayInputStream(header))==stream)
  val queueField=WatchAudioInput::class.java.getDeclaredField("queue").apply{isAccessible=true}
  val epochField=WatchAudioInput::class.java.getDeclaredField("generation").apply{isAccessible=true}
  @Suppress("UNCHECKED_CAST") val queue=queueField.get(WatchAudioInput) as LinkedBlockingDeque<Any>
  val ctor=WatchAudioInput.Block::class.java.getDeclaredConstructor(String::class.java,java.lang.Long.TYPE,ShortArray::class.java,java.lang.Long.TYPE)
  for(index in 0 until frames){
   val sequence=firstSequence+index
   val raw=ByteArrayOutputStream().also{AudioWire.writeFrame(it,sequence,pcm.copyOfRange(index*4000,(index+1)*4000))}.toByteArray()
   val decoded=AudioWire.readFrame(ByteArrayInputStream(raw),sequence)
   val packet=synchronized(WatchAudioInput){ctor.newInstance(stream,sequence,decoded,epochField.getLong(WatchAudioInput))}
   check(queue.offerLast(packet,2,TimeUnit.SECONDS)){"Synthetic watch frame queue overflow"}
   SystemClock.sleep(500)
  }
 }
 private fun normalize(value:String)=java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFD)
  .replace(Regex("\\p{M}+"),"").lowercase(java.util.Locale.ROOT).trim().replace(Regex("\\s+")," ")
 private fun lineCount(file:File)=if(file.isFile)file.readLines().size else 0
 private fun readEvents(file:File,from:Int)=if(file.isFile)file.readLines().drop(from).mapNotNull{runCatching{JSONObject(it)}.getOrNull()} else emptyList()
 private fun await(label:String,timeout:Long,predicate:()->Boolean){
  val deadline=SystemClock.elapsedRealtime()+timeout
  while(!predicate()){check(SystemClock.elapsedRealtime()<deadline){"Timed out: $label; state=${LiveState.current}"};SystemClock.sleep(100)}
 }
 private fun snapshot(prefs:SharedPreferences):Map<String,Any?> = prefs.all.mapValues{(_,v)->if(v is Set<*>)v.toSet() else v}
 private fun restore(prefs:SharedPreferences,values:Map<String,Any?>){
  val editor=prefs.edit().clear()
  for((key,value)in values)when(value){
   is String->editor.putString(key,value);is Boolean->editor.putBoolean(key,value);is Long->editor.putLong(key,value)
   is Int->editor.putInt(key,value);is Float->editor.putFloat(key,value);is Set<*>->editor.putStringSet(key,value.filterIsInstance<String>().toSet())
  }
  check(editor.commit()){"Could not restore validation preferences"}
 }
}
