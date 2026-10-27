package org.tangowiz.app

import android.app.*
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.*
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** One engine owner thread; capture sessions are disposable, the engine is resident. */
class RecognitionService:Service(){
 companion object {
  const val RESUME="RESUME"
  const val RESTART="RESTART"
  const val RESET="RESET"
  const val PAUSE="PAUSE"
  const val EXIT="EXIT"
  const val EXIT_ACTIVITY="org.tangowiz.app.EXIT_ACTIVITY"
 }
 private val stateLock=Object()
 @Volatile private var desired=false
 @Volatile private var exiting=false
 @Volatile private var generation=0L
 @Volatile private var recorder:AudioRecord?=null
 @Volatile private var captureThread:Thread?=null
 private var owner:Thread?=null
 @Volatile private var recentAudio:RecentAudioWindow?=null
 private var resetLookback:ShortArray?=null
 private var wake:PowerManager.WakeLock?=null
 private val io=Executors.newSingleThreadExecutor()
 private lateinit var store:Store
 private var model:Interpreter?=null
 private var bundle:BundleSnapshot?=null
 @Volatile private var reloadPending=false
 private var native=0L
 private var modelLoads=0
 private var indexLoads=0
 private val main=Handler(Looper.getMainLooper())
 override fun onBind(intent:Intent?)=null
 override fun onCreate(){super.onCreate();store=Store(this);PhoneWatch.init(this);getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("recognition","Recognition",NotificationManager.IMPORTANCE_LOW))}
 private fun event(name:String,extra:JSONObject=JSONObject()){store.append(File(store.root,"lifecycle.jsonl"),extra.put("event",name).put("time",Instant.now().toString()).put("generation",generation).put("model_loads",modelLoads).put("index_loads",indexLoads))}
 private fun notification():Notification {
  fun command(action:String,id:Int,foreground:Boolean=false):PendingIntent {
   val intent=Intent(this,RecognitionService::class.java).setAction(action)
   return if(foreground)PendingIntent.getForegroundService(this,id,intent,PendingIntent.FLAG_IMMUTABLE) else PendingIntent.getService(this,id,intent,PendingIntent.FLAG_IMMUTABLE)
  }
  val open=PendingIntent.getActivity(this,4,Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_IMMUTABLE)
  return Notification.Builder(this,"recognition").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("TangoWiz")
   .setContentText(if(exiting)"Exiting…" else if(desired)"Listening" else "Identification paused")
   .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
   .addAction(Notification.Action.Builder(null,if(desired)"Pause" else "Resume",command(if(desired)PAUSE else RESUME,if(desired)1 else 2,!desired)).build())
   .addAction(Notification.Action.Builder(null,"Open",open).build())
   .addAction(Notification.Action.Builder(null,"Exit",command(EXIT,3)).build()).build()
 }
 private fun startRecognitionForeground(){
  if(Build.VERSION.SDK_INT>=29)startForeground(1,notification(),if(store.microphone=="watch")android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(1,notification())
 }
 private fun refreshNotification(){main.post{if(!exiting)getSystemService(NotificationManager::class.java).notify(1,notification())}}
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
  if(exiting)return START_NOT_STICKY
  when(intent?.action){
   EXIT->{requestExit();return START_NOT_STICKY}
   "RELOAD"->{PhoneWatch.engineReady=false;synchronized(stateLock){desired=false;generation++;reloadPending=true;stateLock.notifyAll()};stopCapture();releaseWake();LiveState.publish(RecognitionState(resident=true,phase="Ⅱ"));event("resource_reload_requested")}
   RESET->{
    synchronized(stateLock){
     if(!desired||exiting)return START_NOT_STICKY
     resetLookback=recentAudio?.snapshot()?:ShortArray(0)
     generation++;stopCapture();stateLock.notifyAll()
     WatchAudioInput.discardBuffered()
     LiveState.publish(RecognitionState(listening=true,resident=true,phase="…"))
     event("manual_reset_requested",JSONObject().put("retained_samples",resetLookback!!.size))
    }
    PhoneWatch.changed()
   }
   RESTART->{if(intent.getBooleanExtra("automatic_input_restart",false)&&!desired)return START_NOT_STICKY;synchronized(stateLock){desired=true;generation++;stateLock.notifyAll()};stopCapture();LiveState.publish(RecognitionState(listening=true,resident=true,phase="…",error=intent.getStringExtra("input_error")?:""));event("input_restart_requested")}
   PAUSE,"STOP"->{
    synchronized(stateLock){desired=false;generation++;resetLookback=null;recentAudio=null;stateLock.notifyAll()}
    stopCapture();WatchAudioInput.close();PhoneWatch.stopRemoteCapture();releaseWake();LiveState.publish(RecognitionState(resident=true,phase="Ⅱ"));event("pause_requested")
   }
   else->{
    if(exiting)return START_NOT_STICKY
    if(ResourceStore(this).current()==null){LiveState.publish(RecognitionState(error="Download resources first"));stopSelf();return START_NOT_STICKY}
    synchronized(stateLock){if(!desired){desired=true;generation++};stateLock.notifyAll()}
    LiveState.publish(RecognitionState(listening=true,resident=true,phase="…"));event("resume_requested")
   }
  }
  startRecognitionForeground()
  if(owner==null)owner=Thread({engineLoop()},"tango-engine-owner").apply{start()}
  return START_NOT_STICKY
 }
 private fun active(token:Long)=desired&&!exiting&&generation==token
 private fun stopCapture(){runCatching{recorder?.stop()};captureThread?.interrupt()}
 private fun releaseWake(){synchronized(stateLock){if(wake?.isHeld==true)wake?.release()}}
 private fun requestExit(){
  synchronized(stateLock){if(exiting)return;exiting=true;desired=false;generation++;stateLock.notifyAll()}
  event("exit_requested");stopCapture();WatchAudioInput.close();PhoneWatch.stopRemoteCapture();releaseWake();LiveState.publish(RecognitionState(resident=true,phase="…"))
  if(owner==null){io.shutdown();finishExit()} else getSystemService(NotificationManager::class.java).notify(1,notification())
 }
 override fun onDestroy(){synchronized(stateLock){exiting=true;desired=false;generation++;stateLock.notifyAll()};stopCapture();WatchAudioInput.close();PhoneWatch.engineReady=false;releaseWake();super.onDestroy()}
 private fun finishExit(){main.post{
  LiveState.publish(RecognitionState(exited=true));sendBroadcast(Intent(EXIT_ACTIVITY).setPackage(packageName))
  stopForeground(STOP_FOREGROUND_REMOVE);getSystemService(NotificationManager::class.java).cancel(1);stopSelf()
 }}
 private fun engineLoop(){
  try{
   while(true){
    val token=synchronized(stateLock){while(!desired&&!exiting&&!reloadPending)stateLock.wait();if(exiting)null else if(reloadPending){reloadPending=false;-1L}else generation}
    if(token==null)break
    if(token==-1L){model?.close();model=null;if(native!=0L)NativeEngine.close(native);native=0L;bundle?.let{ResourcePins.release(it)};bundle=null;ResourceStore(this).cleanupUnused();event("resources_released");continue}
    runSession(token)
    synchronized(stateLock){if(!desired&&!exiting){LiveState.publish(LiveState.current.copy(listening=false,resident=true,phase="Ⅱ",best=null,second=null));event("pause_complete");refreshNotification()}}
   }
  }finally{
   stopCapture();releaseWake();PhoneWatch.engineReady=false;model?.close();model=null;if(native!=0L)NativeEngine.close(native);native=0L
   bundle?.let{ResourcePins.release(it)};bundle=null;ResourceStore(this).cleanupUnused()
   io.shutdown();runCatching{io.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS)}
   event("engine_closed");finishExit()
  }
 }
 private fun runSession(token:Long){
  val session=Instant.now().toString().replace(":","-");val dir=SessionFiles.begin(store.root,session)
  var audio:WavWriter?=null;var fingerprintLog:DataOutputStream?=null;var fallbackWorker:FallbackMatcher?=null
  val start=SystemClock.elapsedRealtimeNanos();val timings=File(dir,"timing.jsonl")
  fun timing(stage:String,t0:Long,cpu0:Long=0,extra:JSONObject=JSONObject(),process0:Long=0){if(store.timing){extra.put("stage",stage).put("time",Instant.now().toString()).put("elapsed_ms",(SystemClock.elapsedRealtimeNanos()-t0)/1e6);if(cpu0>0)extra.put("caller_thread_cpu_ms",(android.os.Debug.threadCpuTimeNanos()-cpu0)/1e6);if(process0>0)extra.put("process_cpu_ms",android.os.Process.getElapsedCpuTime()-process0);io.execute{store.append(timings,extra)}}}
  try{
   val usingWatch=store.microphone=="watch"
   val ar=if(usingWatch)null else {
    val size=maxOf(AudioRecord.getMinBufferSize(8000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT),16000)
    AudioRecord(MediaRecorder.AudioSource.UNPROCESSED,8000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size).also{check(it.state==AudioRecord.STATE_INITIALIZED){"Microphone initialization failed"}}
   }
   recorder=ar
   val queue=ArrayBlockingQueue<ShortArray>(if(usingWatch)8 else 240)
   val recent=RecentAudioWindow(16000)
   val retained=synchronized(stateLock){if(!active(token))return;recentAudio=recent;resetLookback.also{resetLookback=null}}?:ShortArray(0)
   for(offset in retained.indices step 4000){val block=retained.copyOfRange(offset,minOf(offset+4000,retained.size));queue.add(block);recent.append(block)}
   event("manual_reset_seed",JSONObject().put("retained_samples",retained.size))
   synchronized(stateLock){
    if(!active(token)){ar?.release();recorder=null;return}
    ar?.startRecording();wake=(getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"TangoWiz:Recognition").apply{acquire()}
   }
   event("capture_started",JSONObject().put("source",if(usingWatch)"watch" else "phone"));val captured=java.util.concurrent.atomic.AtomicLong(retained.size.toLong())
   captureThread=Thread({
    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
    try{
     var watchStream:String?=null
     while(active(token)){
      val block:ShortArray
      if(usingWatch){
       val packet=WatchAudioInput.take()?:continue
       if(!active(token))break
       if(watchStream!=null&&watchStream!=packet.stream){WatchAudioInput.returnFirst(packet);PhoneWatch.restartInput();break}
       watchStream=packet.stream;block=packet.pcm
      }else{
       block=ShortArray(4000);var n=0
       while(n<block.size&&active(token)){val got=ar!!.read(block,n,block.size-n,AudioRecord.READ_BLOCKING);if(got<0)throw IOException("Microphone error $got");n+=got}
       if(n!=block.size)continue
      }
      if(!active(token))break
      recent.append(block);queue.put(block);captured.addAndGet(block.size.toLong())
     }
    }catch(e:Exception){if(active(token)){synchronized(stateLock){if(active(token)){desired=false;generation++}};LiveState.publish(RecognitionState(resident=true,error=e.message?:"Audio capture failed"))}}
   },"tango-audio").apply{start()}
   var t=SystemClock.elapsedRealtimeNanos()
   if(model==null){if(bundle==null)bundle=ResourceStore(this).pinCurrent()?:throw IOException("Download resources first");FileInputStream(bundle!!.model).channel.use{ch->model=Interpreter(ch.map(java.nio.channels.FileChannel.MapMode.READ_ONLY,0,ch.size()),Interpreter.Options().setNumThreads(2))}
   modelLoads++;event("model_loaded");timing("model_load",t)}
   if(exiting)return
   if(native==0L){t=SystemClock.elapsedRealtimeNanos();native=NativeEngine.initFile(bundle!!.catalog.absolutePath);check(native!=0L){"Index initialization failed"};indexLoads++;event("index_loaded");timing("index_load",t)}
   if(!active(token))return
   PhoneWatch.engineReady=true;PhoneWatch.changed()
   NativeEngine.reset(native)
   val input=ByteBuffer.allocateDirect(256*33*4).order(ByteOrder.nativeOrder());val output=ByteBuffer.allocateDirect(128*4).order(ByteOrder.nativeOrder())
   val pcmHistory=FloatArray(131072);var pcmSize=0;val nextFrame=IntArray(13);var baseSample=0L;var samples=0L;var lastLogged:Candidate?=null;var logSecond=-60.0;var lastFallback=-30.0;var hadSignal=false
   val fallbackResult=java.util.concurrent.atomic.AtomicReference<Triple<JSONObject,Long,Long>?>(null)
   val signalGeneration=java.util.concurrent.atomic.AtomicLong(0)
   val songEnd=SongEndPredictor()
   var fallbackEarliestSample=0L
   val fp=DataOutputStream(BufferedOutputStream(File(dir,"fingerprints.bin").outputStream()));fingerprintLog=fp
   fallbackWorker=FallbackMatcher(bundle!!){offset,speed,embedding->if(active(token))io.execute{if(active(token)){fp.writeDouble(offset.toDouble());fp.writeFloat(speed);embedding.forEach{fp.writeFloat(it)}}}}
   File(dir,"session.json").writeText(JSONObject().put("start",Instant.now().toString()).put("sample_rate",8000).put("input_source",if(usingWatch)"watch" else "phone").put("fingerprint_format","big-endian: float64 query_offset_seconds, float32 speed, 128 float32 embedding; repeated records").put("confidence","Heuristic score, not calibrated probability").put("resource_format",bundle!!.formatVersion).put("bundle_version",bundle!!.version).put("archive_sha256",bundle!!.archiveHash).put("app_version",packageManager.getPackageInfo(packageName,0).versionName).toString(2))
   while(active(token)){
    val block=queue.poll(1,java.util.concurrent.TimeUnit.SECONDS)?:continue
    val saveAudio=store.audio;val audioSample=samples
    io.execute{if(saveAudio){if(audio==null)audio=WavWriter(File(dir,"microphone-${audioSample}.wav"));audio!!.write(block)}else{audio?.close();audio=null}}
    check(pcmSize+block.size<=pcmHistory.size){"PCM buffer overflow"}
    for(i in block.indices)pcmHistory[pcmSize+i]=block[i]/32768f;pcmSize+=block.size;samples+=4000
    val offset=(samples-8000)/8000.0
    if(samples<8240)continue
    var result:JSONObject?=null;var audibleWindow=false
    // Each speed stream advances by half a second of adjusted audio.
    for(si in listOf(0)){if(!active(token))break;val speed=1f+si*.005f
    while(active(token)){
     val frame=nextFrame[si+6];val sourceStart=frame*4000.0*speed
     if(sourceStart+7999*speed+33>=samples)break
     val pcm=NativeEngine.resample(pcmHistory,sourceStart-baseSample,speed.toDouble())
     nextFrame[si+6]++
     val queryOffset=(sourceStart/8000.0).toFloat()
     val rms=SignalGate.rms(pcm)
     if(rms<SignalGate.MIN_RMS){
      if(hadSignal){NativeEngine.reset(native);signalGeneration.incrementAndGet()};songEnd.clear();fallbackEarliestSample=sourceStart.toLong();hadSignal=false;audibleWindow=false;result=null;fallbackResult.set(null)
      if(store.timing)io.execute{store.append(timings,JSONObject().put("stage","silence_gate").put("query_offset",queryOffset).put("rms",rms).put("threshold",SignalGate.MIN_RMS))}
      continue
     }
     val boundary=songEnd.takeReset(queryOffset.toDouble())
     if(boundary!=null){
      NativeEngine.reset(native);signalGeneration.incrementAndGet();fallbackResult.set(null)
      fallbackEarliestSample=sourceStart.toLong();lastLogged=null;logSecond=-60.0
      val transition=JSONObject().put("query_offset",queryOffset).put("predicted_end",boundary.endSeconds).put("group_id",boundary.identity).put("score",boundary.score).put("observations",boundary.observations).put("support_span_seconds",boundary.spanSeconds).put("last_confirmation_age_seconds",queryOffset-boundary.lastConfirmedSeconds)
      io.execute{store.append(File(dir,"transitions.jsonl"),transition.put("time",Instant.now().toString()))}
     }
     audibleWindow=true;hadSignal=true
     t=SystemClock.elapsedRealtimeNanos();val cpu=android.os.Debug.threadCpuTimeNanos();val features=NativeEngine.features(pcm);timing("frontend",t,cpu)
     require(features.size==8448){"Invalid model frontend shape ${features.size}"};input.rewind();input.asFloatBuffer().put(features);output.rewind()
     t=SystemClock.elapsedRealtimeNanos();val inferenceCpu=android.os.Debug.threadCpuTimeNanos();val inferenceProcess=android.os.Process.getElapsedCpuTime();model!!.run(input,output);timing("fingerprint",t,inferenceCpu,process0=inferenceProcess)
     val embedding=FloatArray(128);output.rewind();output.asFloatBuffer().get(embedding);check(embedding.all{it.isFinite()}){"Invalid neural fingerprint"}
     io.execute{fp.writeDouble(queryOffset.toDouble());fp.writeFloat(speed);embedding.forEach{fp.writeFloat(it)};if(frame%4==0)fp.flush()}
     if(!active(token))break
     t=SystemClock.elapsedRealtimeNanos();val matchCpu=android.os.Debug.threadCpuTimeNanos();val matchProcess=android.os.Process.getElapsedCpuTime();val raw=NativeEngine.search(native,embedding,1,speed,queryOffset);timing("lookup_and_aggregation",t,matchCpu,JSONObject().put("query_offset",offset).put("speed",speed),process0=matchProcess);result=JSONObject(raw)
     val matched=result!!.optJSONArray("candidates")?.optJSONObject(0)
     if(matched!=null)songEnd.observe(queryOffset.toDouble(),matched.optString("groupId",matched.optString("id")),matched.optDouble("score"),matched.optDouble("referenceSeconds"),matched.optDouble("referenceEndSeconds"),result!!.optInt("frames"),matched.optDouble("speed",1.0))
    }
    }
    val earliest=(nextFrame[6]*4000.0).toLong()-80064
    val trim=(earliest-baseSample).toInt().coerceAtLeast(0);if(trim>16000){System.arraycopy(pcmHistory,trim,pcmHistory,0,pcmSize-trim);pcmSize-=trim;baseSample+=trim}
    // Buffered fingerprint stream is flushed at session close.
    if(!active(token))break
    val candidates=OrchestraPresentation.candidates(result)
    var presentationPool=candidates
    val nativeBest=candidates.firstOrNull();var selectedResult=result
    if(audibleWindow&&offset>10&&offset-lastFallback>=30&&(nativeBest?.score?:0.0)<.60&&pcmSize>=48000&&samples-48000>=fallbackEarliestSample){
     lastFallback=offset;val fallbackGeneration=signalGeneration.get();val matchedBaseline=NativeEngine.recent(native,10);val snapshot=pcmHistory.copyOfRange(pcmSize-48000,pcmSize)
     fallbackWorker!!.start(snapshot,(samples-48000)/8000.0){r,t0->val variantScore=r.optJSONArray("candidates")?.optJSONObject(0)?.optDouble("score")?:0.0;r.put("matched_duration_baseline_score",matchedBaseline);val allowed=matchedBaseline.isFinite()&&variantScore>=matchedBaseline+.08&&signalGeneration.get()==fallbackGeneration&&active(token);r.put("matched_duration_guard",allowed).put("same_signal_generation",signalGeneration.get()==fallbackGeneration);if(allowed)fallbackResult.set(Triple(r,t0,fallbackGeneration));io.execute{val row=JSONObject().put("stage","speed_fallback").put("elapsed_ms",(SystemClock.elapsedRealtimeNanos()-t0)/1e6).put("result",r);store.append(File(dir,"fallback.jsonl"),row);if(store.timing)store.append(timings,row)}}
    }
    val fallback=fallbackResult.get()
    if(audibleWindow&&fallback!=null&&fallback.third==signalGeneration.get()&&(SystemClock.elapsedRealtimeNanos()-fallback.second)<15_000_000_000L){
     val f=fallback.first.optJSONArray("candidates")?.optJSONObject(0)
     if(f!=null&&f.optDouble("score")>=.65&&f.optDouble("score")>(nativeBest?.score?:0.0)+.08){selectedResult=fallback.first;presentationPool=OrchestraPresentation.candidates(fallback.first)+candidates}
    }
    val presented=OrchestraPresentation.merge(presentationPool);val best=presented.firstOrNull();val second=presented.getOrNull(1)
    if(best!=null&&(lastLogged==null||!best.sameRecording(lastLogged!!)||offset-logSecond>=5)){val logged=best;val raw=selectedResult;io.execute{store.result(logged,second,offset+1,session,raw)};lastLogged=best;logSecond=offset}
    synchronized(stateLock){if(active(token))LiveState.publish(RecognitionState(listening=true,resident=true,phase="♪",best=best,second=second,seconds=offset+1))}
    timing("end_to_end",start,extra=JSONObject().put("audio_seconds",samples/8000.0).put("audio_to_result_lag_ms",(SystemClock.elapsedRealtimeNanos()-start)/1e6-samples/8.0).put("backlog_seconds",queue.size*.5).put("captured_seconds",captured.get()/8000.0))
   }
   captureThread?.interrupt()
  }catch(e:Throwable){
   if(active(token)){val error=e.message?:e.javaClass.simpleName;File(dir,"error.txt").writeText(e.stackTraceToString());synchronized(stateLock){desired=false;generation++};LiveState.publish(RecognitionState(resident=true,error=error));event("session_error",JSONObject().put("message",error));refreshNotification()}
  }finally{
   stopCapture();runCatching{captureThread?.join(2000)};captureThread=null;recorder?.release();recorder=null
   runCatching{fallbackWorker?.close()}
   io.execute{runCatching{audio?.close()};audio=null;runCatching{fingerprintLog?.close()}}
   val writesClosed=runCatching{io.submit{}.get(10,java.util.concurrent.TimeUnit.SECONDS)}.isSuccess
   releaseWake();if(native!=0L)NativeEngine.reset(native);event("session_closed",JSONObject().put("session",session));if(writesClosed)SessionFiles.end(store.root,session)
  }
 }
}

class WavWriter(val file:File):Closeable {
 private val out=RandomAccessFile(file,"rw");private var bytes=0L
 init{out.write(ByteArray(44))}
 fun write(samples:ShortArray){val b=ByteBuffer.allocate(samples.size*2).order(ByteOrder.LITTLE_ENDIAN);samples.forEach{b.putShort(it)};out.write(b.array());bytes+=b.capacity();if(bytes%32000L==0L)header()}
 private fun header(){val pos=out.filePointer;val h=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);h.put("RIFF".toByteArray()).putInt((bytes+36).toInt()).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16).put("data".toByteArray()).putInt(bytes.toInt());out.seek(0);out.write(h.array());out.seek(pos)}
 override fun close(){header();out.close()}
}
