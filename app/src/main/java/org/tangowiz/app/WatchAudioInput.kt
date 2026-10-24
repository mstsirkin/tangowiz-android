package org.tangowiz.app

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import org.tangowiz.link.AudioWire
import org.tangowiz.link.WearMessages
import java.io.InputStream
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

/** Bounded, live-only audio. Validate candidate streams before replacing any input. */
object WatchAudioInput {
 data class Block(val stream:String,val sequence:Long,val pcm:ShortArray,val epoch:Long)
 private val queue=LinkedBlockingDeque<Block>(8)
 private var input:InputStream?=null
 private var channel:ChannelClient.Channel?=null
 private var context:Context?=null
 @Volatile private var generation=0L
 @Volatile var connected=false;private set
 private val pending=java.util.concurrent.Semaphore(2)
 private val deadlines=java.util.concurrent.Executors.newSingleThreadScheduledExecutor{r->Thread(r,"watch-channel-deadline").apply{isDaemon=true}}
 fun take():Block?{val packet=queue.poll(1,TimeUnit.SECONDS)?:return null;return synchronized(this){packet.takeIf{it.epoch==generation}}}
 @Synchronized fun returnFirst(block:Block){if(block.epoch==generation)check(queue.offerFirst(block)){"Watch audio queue overflow"}}
 @Synchronized fun close(){generation++;connected=false;runCatching{input?.close()};input=null;val ctx=context;val old=channel;channel=null;if(ctx!=null&&old!=null)Wearable.getChannelClient(ctx).close(old);queue.clear()}
 fun open(ctx:Context,newChannel:ChannelClient.Channel){
  val client=Wearable.getChannelClient(ctx)
  if(newChannel.path!=WearMessages.AUDIO_PATH)return
  if(synchronized(this){connected&&channel==newChannel})return
  if(Store(ctx).microphone!="watch"||!LiveState.current.listening||!pending.tryAcquire()){client.close(newChannel);return}
  val accepted=java.util.concurrent.atomic.AtomicBoolean(false)
  val timeout=deadlines.schedule({if(!accepted.get())client.close(newChannel)},15,TimeUnit.SECONDS)
  Thread{
   var token=-1L;var candidate:InputStream?=null;var slotHeld=true;var watchdog:java.util.concurrent.ScheduledFuture<*>?=null
   val lastFrame=java.util.concurrent.atomic.AtomicLong(android.os.SystemClock.elapsedRealtime())
   try{
    val node=Tasks.await(Wearable.getNodeClient(ctx).connectedNodes,10,TimeUnit.SECONDS).firstOrNull{it.id==newChannel.nodeId&&it.isNearby}
    check(node!=null){"Watch audio requires a nearby connection"}
    val stream=Tasks.await(client.getInputStream(newChannel),10,TimeUnit.SECONDS);candidate=stream
    val streamId=AudioWire.readHeader(stream)
    // An obsolete open/empty channel never displaces a good live stream.
    val until=android.os.SystemClock.elapsedRealtime()+2000
    while(connected&&android.os.SystemClock.elapsedRealtime()<until)Thread.sleep(50)
    synchronized(this){
     check(Store(ctx).microphone=="watch"&&LiveState.current.listening){"Watch input is no longer selected"}
     check(!connected){"Another watch audio stream is active"}
     close();context=ctx.applicationContext;channel=newChannel;input=stream;token=generation;connected=true;accepted.set(true)
    }
    timeout.cancel(false);pending.release();slotHeld=false
    PhoneWatch.changed();var sequence=0L
    lastFrame.set(android.os.SystemClock.elapsedRealtime())
    watchdog=deadlines.scheduleAtFixedRate({val expired=synchronized(this){generation==token&&android.os.SystemClock.elapsedRealtime()-lastFrame.get()>10000};if(expired)client.close(newChannel)},2,2,TimeUnit.SECONDS)
    while(generation==token){
     val samples=AudioWire.readFrame(stream,sequence);lastFrame.set(android.os.SystemClock.elapsedRealtime())
     synchronized(this){if(generation!=token)return@Thread;if(!queue.offerLast(Block(streamId,sequence,samples,token)))throw java.io.IOException("Watch audio backlog; restart watch microphone")}
     sequence++
    }
   }catch(e:Exception){
    val lost=synchronized(this){if(token>=0&&generation==token){close();true}else false}
    if(lost)PhoneWatch.inputError("Watch audio disconnected: ${e.message?:"connection lost"}")
   }finally{watchdog?.cancel(false);timeout.cancel(false);if(slotHeld)pending.release();runCatching{candidate?.close()};client.close(newChannel)}
  }.apply{name="watch-audio-input";isDaemon=true;start()}
 }
}
