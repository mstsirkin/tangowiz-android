package org.tangowiz.wear
import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.media.*
import android.os.*
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import org.tangowiz.link.AudioWire
import org.tangowiz.link.WearMessages
import java.io.*
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

/** Streaming, anti-aliased 8 kHz mono conversion. Hardware 8 kHz takes the exact bypass path. */
class PcmResampler(private val rate:Int){
 private var buffer=DoubleArray(32768);private var size=0;private var next=0.0
 private val step=rate/8000.0;private val weights=Array(128){phase->val frac=phase/128.0;val fc=.45*8000/rate;DoubleArray(64){n->val x=n-31-frac;val sinc=if(abs(x)<1e-12)2*fc else sin(2*PI*fc*x)/(PI*x);sinc*(.5+.5*cos(PI*x/33))}.let{w->val sum=w.sum();DoubleArray(64){w[it]/sum}}}
 fun append(input:ShortArray,count:Int):ShortArray{if(rate==8000)return input.copyOf(count);if(size+count>buffer.size)buffer=buffer.copyOf(maxOf(buffer.size*2,size+count));for(i in 0 until count)buffer[size+i]=input[i].toDouble();size+=count;val result=ShortArray(ceil(count/step).toInt()+64);var used=0
  while(next+32<size){val center=floor(next).toInt();val phase=((next-center)*128).toInt().coerceIn(0,127);val w=weights[phase];var value=0.0;for(n in 0 until 64){val at=center+n-31;if(at in 0 until size)value+=buffer[at]*w[n]};result[used++]=value.roundToInt().coerceIn(-32768,32767).toShort();next+=step};val trim=(floor(next).toInt()-32).coerceAtLeast(0);if(trim>0){System.arraycopy(buffer,trim,buffer,0,size-trim);size-=trim;next-=trim};return result.copyOf(used)
 }
}
class WatchMicService:Service(){
 companion object{private val ownerLock=Any();private var issued=0L;private var owner=0L}
 @Volatile private var activeRun=0L
 private fun active(token:Long)=running.get()&&synchronized(ownerLock){owner==token&&activeRun==token}
 private fun publish(token:Long,block:()->Unit){synchronized(ownerLock){if(issued==token)block()}}
 private fun publishActive(token:Long,block:()->Unit){synchronized(ownerLock){if(owner==token&&activeRun==token&&running.get())block()}}
 private fun status(token:Long,capturing:Boolean,error:String="",extra:JSONObject=JSONObject()){WatchLink.status(capturing,error,extra,valid={synchronized(ownerLock){issued==token&&(!capturing||(owner==token&&running.get()))}})}
 private val running=AtomicBoolean(false);private val main=Handler(Looper.getMainLooper());private var destroyed=false;private var restartPending=false;private var latestStart=0;private var worker:Thread?=null;private var capture:Thread?=null;@Volatile private var recorder:AudioRecord?=null;@Volatile private var channel:ChannelClient.Channel?=null;private var wake:PowerManager.WakeLock?=null;private val wakeLock=Any();private val captureLock=Any()
 private fun releaseWake(){synchronized(wakeLock){val held=wake;wake=null;if(held?.isHeld==true)held.release()}}
 private fun channelEnded(token:Long,reason:Int){if(active(token)&&running.getAndSet(false)){publish(token){WatchCache.error="Watch audio channel closed ($reason)";WatchCache.connected=false;WatchCache.capturing=false;WatchCache.changed()};status(token,false,"Watch audio channel closed ($reason)");runCatching{recorder?.stop()};stopSelf()}}
 override fun onBind(intent:Intent?)=null
 override fun onCreate(){super.onCreate();WatchCache.init(this);getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("watch-mic","Watch microphone",NotificationManager.IMPORTANCE_LOW))}
 private fun notice(text:String):Notification{val stop=PendingIntent.getService(this,12,Intent(this,WatchMicService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT);return Notification.Builder(this,"watch-mic").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("TangoWiz microphone").setContentText(text).setOngoing(true).addAction(Notification.Action.Builder(null,"Stop",stop).build()).build()}
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{latestStart=startId;if(intent?.action=="STOP"){restartPending=false;WatchLink.set(JSONObject().put("listening",false));stopSelf();return START_NOT_STICKY};if(destroyed)return START_NOT_STICKY;if(worker!=null){if(!running.get()){restartPending=true;startForeground(12,notice("Restarting microphone…"))};return START_NOT_STICKY};begin();return START_NOT_STICKY
 }
 private fun begin(){val token=synchronized(ownerLock){issued++;owner=issued;activeRun=issued;issued};running.set(true);startForeground(12,notice("Waiting for phone readiness…"));worker=Thread({runStream(token)},"watch-audio").apply{start()}}
 private fun runStream(token:Long){val client=Wearable.getChannelClient(this);val queue=ArrayBlockingQueue<ShortArray>(4);var output:OutputStream?=null;var callback:ChannelClient.ChannelCallback?=null
  try{check(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){"Microphone permission is missing"};val deadline=SystemClock.elapsedRealtime()+60000
   while(active(token)&&!(WatchCache.state.optBoolean("ready")&&WatchCache.state.optBoolean("listening")&&WatchCache.state.optString("microphone")=="watch")){check(SystemClock.elapsedRealtime()<deadline){"Phone did not become ready"};Thread.sleep(200)};if(!active(token))return
   val node=WatchLink.nearbyNode()?:error("No nearby phone; watch capture was not started");val opening=client.openChannel(node.id,WearMessages.AUDIO_PATH);opening.addOnSuccessListener{if(!active(token))client.close(it)};val opened=Tasks.await(opening,8,TimeUnit.SECONDS);channel=opened;if(!active(token))return
   callback=object:ChannelClient.ChannelCallback(){override fun onChannelClosed(ch:ChannelClient.Channel,reason:Int,appError:Int){if(channel==ch)channelEnded(token,reason)};override fun onOutputClosed(ch:ChannelClient.Channel,reason:Int,appError:Int){if(channel==ch)channelEnded(token,reason)}};client.registerChannelCallback(opened,callback)
   output=BufferedOutputStream(Tasks.await(client.getOutputStream(opened),8,TimeUnit.SECONDS),16384);val stream=UUID.randomUUID().toString();AudioWire.writeHeader(output,stream);output.flush()
   val (ar,rate)=createRecorder();recorder=ar;synchronized(captureLock){if(!active(token))return;wake=(getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"TangoWiz:WatchMic").apply{acquire(6*60*60*1000L)};ar.startRecording();check(ar.recordingState==AudioRecord.RECORDSTATE_RECORDING){"Watch microphone did not start"};publishActive(token){WatchCache.capturing=true;WatchCache.pendingCapture=false}};if(!active(token))return;publishActive(token){WatchCache.error="";WatchCache.changed()};status(token,true,extra=JSONObject().put("streamId",stream).put("captureRate",rate));publishActive(token){getSystemService(NotificationManager::class.java).notify(12,notice("Streaming watch microphone"))}
   capture=Thread({try{val resampler=PcmResampler(rate);val input=ShortArray(maxOf(4096,rate/4));var block=ShortArray(4000);var n=0
     while(active(token)){val read=ar.read(input,0,input.size,AudioRecord.READ_BLOCKING);check(read>0){"Audio capture failed ($read)"};val converted=resampler.append(input,read);for(sample in converted){block[n++]=sample;if(n==4000){check(queue.offer(block,100,TimeUnit.MILLISECONDS)){"Audio channel is too slow; old audio was discarded"};block=ShortArray(4000);n=0}}}
    }catch(e:Exception){if(active(token)){publishActive(token){WatchCache.error=e.message?:"Audio capture failed";running.set(false);WatchCache.changed()};client.close(opened)}}},"watch-capture").apply{start()}
   var sequence=0L;while(active(token)){val block=queue.poll(1,TimeUnit.SECONDS)?:continue;AudioWire.writeFrame(output,sequence++,block);output.flush();if(sequence%10==0L)status(token,true,extra=JSONObject().put("streamId",stream).put("sequence",sequence).put("sentSamples",sequence*4000))}
  }catch(e:Exception){if(active(token)){publishActive(token){WatchCache.error=e.message?:"Watch audio connection failed"};status(token,false,e.message?:"Watch audio connection failed")}}
  finally{running.set(false);channel?.let{client.close(it)};runCatching{output?.close()};capture?.interrupt();runCatching{recorder?.stop()};runCatching{capture?.join(1500)};recorder?.release();recorder=null;callback?.let{client.unregisterChannelCallback(it)};channel=null;releaseWake();publish(token){WatchCache.capturing=false;WatchCache.changed()};status(token,false,WatchCache.error);synchronized(ownerLock){if(owner==token)owner=0};main.post{worker=null;if(restartPending&&!destroyed){restartPending=false;begin()}else{publish(token){stopForeground(STOP_FOREGROUND_REMOVE)};stopSelf(latestStart)}}}
 }
 private fun createRecorder():Pair<AudioRecord,Int>{val unprocessed=getSystemService(AudioManager::class.java).getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)=="true";for(rate in listOf(8000,44100,48000)){val min=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);if(min<=0)continue;for(source in if(unprocessed)listOf(MediaRecorder.AudioSource.UNPROCESSED,MediaRecorder.AudioSource.VOICE_RECOGNITION)else listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION)){val ar=runCatching{AudioRecord(source,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(min*2,rate))}.getOrNull()?:continue;if(ar.state==AudioRecord.STATE_INITIALIZED)return ar to rate;ar.release()}};error("No supported microphone format")}
 override fun onDestroy(){destroyed=true;restartPending=false;running.set(false);publish(activeRun){WatchCache.capturing=false;WatchCache.changed()};synchronized(ownerLock){if(owner==activeRun)owner=0};releaseWake();channel?.let{Wearable.getChannelClient(this).close(it)};synchronized(captureLock){runCatching{recorder?.stop()};releaseWake()};worker?.interrupt();capture?.interrupt();super.onDestroy()}
}
