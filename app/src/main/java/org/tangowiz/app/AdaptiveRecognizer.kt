package org.tangowiz.app

import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Wide-range recognition; live state belongs to the recognition thread. */
class AdaptiveRecognizer(private val bundle:BundleSnapshot, private val interval:Double, private val log:(JSONObject)->Unit):AutoCloseable {
 private class State(bundle:BundleSnapshot):AutoCloseable {
  val handle=NativeEngine.initFile(bundle.catalog.absolutePath)
  val model=FileInputStream(bundle.model).channel.use{Interpreter(it.map(java.nio.channels.FileChannel.MapMode.READ_ONLY,0,it.size()),Interpreter.Options().setNumThreads(1))}
  fun fp(pcm:FloatArray):FloatArray {
   val input=ByteBuffer.allocateDirect(8448*4).order(ByteOrder.nativeOrder());input.asFloatBuffer().put(NativeEngine.features(pcm))
   val output=ByteBuffer.allocateDirect(512).order(ByteOrder.nativeOrder());model.run(input,output)
   return FingerprintOutput.read(output)
  }
  override fun close(){model.close();NativeEngine.close(handle)}
 }
 private data class Trial(val speed:Double,val start:Double,val vectors:List<FloatArray>,val result:JSONObject,val generation:Long,val began:Long,val recent:Double)
 private val pool=Executors.newFixedThreadPool(2)
 private val coordinator=Executors.newSingleThreadExecutor()
 private val workers=Array(2){State(bundle)}
 private val live=State(bundle)
 private val busy=AtomicBoolean(false)
 private val pending=AtomicReference<Trial?>(null)
 private var selected=1.0;private var next=0.0;private var result:JSONObject?=null
 private var rotation=0;private var nextSearch=-30.0;private var generation=Long.MIN_VALUE
 private fun top(r:JSONObject?)=r?.optJSONArray("candidates")?.optJSONObject(0)
 private fun score(r:JSONObject?)=top(r)?.optDouble("score")?:0.0
 private fun batches():List<Double> {
  val budget=if(selected==1.0)5 else 4
  val near=(if(budget==5)listOf(-3,-2,-1,1,2) else listOf(-2,-1,1,2)).map{1+it*.005}.filter{kotlin.math.abs(it-selected)>1e-6}
  val remaining=(-12..10).filter{it!=0}.map{1+it*.005}.filter{s->kotlin.math.abs(s-selected)>1e-6&&near.none{kotlin.math.abs(it-s)<1e-6}}
  val outer=remaining.filter{it<1}.chunked(budget)+remaining.filter{it>1}.chunked(budget)
  val batch=if(rotation%2==0)near else outer[(rotation/2)%outer.size];rotation++;return batch
 }
 fun update(audio:FloatArray,size:Int,base:Long,samples:Long,t:Double,normal:JSONObject?,normalHandle:Long,gen:Long,earliest:Long,audible:Boolean):JSONObject? {
  if(gen!=generation){generation=gen;NativeEngine.reset(live.handle);result=null;next=t;pending.set(null)}
  val promotion=pending.getAndSet(null)
  if(promotion!=null){
   val valid=promotion.generation==gen&&System.nanoTime()-promotion.began<15_000_000_000L&&score(promotion.result)>=.65&&score(promotion.result)>=promotion.recent+.08&&score(promotion.result)>maxOf(score(normal),score(result))+.08
   log(JSONObject().put("event","completion").put("accepted",valid).put("elapsed_ms",(System.nanoTime()-promotion.began)/1e6).put("snapshot_end",promotion.start+6).put("speed",promotion.speed))
   if(valid){selected=promotion.speed;NativeEngine.reset(live.handle)
    promotion.vectors.forEachIndexed{j,v->result=JSONObject(NativeEngine.search(live.handle,v,1,selected.toFloat(),(promotion.start+j*.5*selected).toFloat()))}
    next=promotion.start+promotion.vectors.size*.5*selected;rotation=0
   }
  }
  if(selected!=1.0){
   if(next*8000<base){NativeEngine.reset(live.handle);result=null;next=t}
   while(next*8000+7999*selected+33<samples){
    val pcm=NativeEngine.resample(audio,next*8000-base,selected)
    if(SignalGate.audible(pcm))result=JSONObject(NativeEngine.search(live.handle,live.fp(pcm),1,selected.toFloat(),next.toFloat())) else {NativeEngine.reset(live.handle);result=null}
    next+=.5*selected
   }
  }
  val best=if(score(result)>score(normal))result else normal
  val clock=samples/8000.0
  if(audible&&t+.5>10&&clock>=nextSearch&&score(best)<.60&&samples-48000>=earliest&&size>=48000){
   val started=busy.compareAndSet(false,true)
   nextSearch=clock+FallbackTiming.delay(interval,!started)
   if(started){
    val speeds=batches();val snapshot=audio.copyOfRange(size-48000,size);val start=(samples-48000)/8000.0;val recent=NativeEngine.recent(normalHandle,10);val began=System.nanoTime()
    log(JSONObject().put("event","start").put("audio_seconds",clock).put("speeds",org.json.JSONArray(speeds)))
    coordinator.submit{try{
     val futures=(0..1).map{worker->pool.submit<List<Trial>>{speeds.filterIndexed{i,_->i%2==worker}.map{speed->
      val state=workers[worker];NativeEngine.reset(state.handle);val vectors=mutableListOf<FloatArray>();var r=JSONObject();var j=0
      while(j<10&&j*4000*speed+7999*speed+33<snapshot.size){val pcm=NativeEngine.resample(snapshot,j*4000*speed,speed);if(!SignalGate.audible(pcm)){r=JSONObject();break};val v=state.fp(pcm);vectors.add(v);r=JSONObject(NativeEngine.search(state.handle,v,1,speed.toFloat(),(start+j*.5*speed).toFloat()));j++}
      Trial(speed,start,vectors,r,gen,began,recent)
     }}}
     val results=futures.map{runCatching{it.get()}};results.firstOrNull{it.isFailure}?.getOrThrow()
     results.flatMap{it.getOrThrow()}.maxByOrNull{score(it.result)}?.let{pending.set(it)}
    }catch(e:Throwable){log(JSONObject().put("event","error").put("message",e.toString()))}finally{busy.set(false)}}
   }else log(JSONObject().put("event","busy_skip").put("audio_seconds",clock))
  }
  if(best===result&&best!=null){val copy=JSONObject(best.toString());val c=top(copy)!!;c.put("referenceSeconds",c.optDouble("referenceSeconds")+(t-(next-.5*selected))/selected);return copy}
  return best
 }
 override fun close(){coordinator.shutdown();check(coordinator.awaitTermination(120,java.util.concurrent.TimeUnit.SECONDS));pool.shutdown();check(pool.awaitTermination(120,java.util.concurrent.TimeUnit.SECONDS));workers.forEach{it.close()};live.close()}
}
