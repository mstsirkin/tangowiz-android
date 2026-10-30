package org.tangowiz.app

import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Two bounded workers with independent native/model state; live recognition never waits. */
class FallbackMatcher(private val bundle:BundleSnapshot,private val record:(Float,Float,FloatArray)->Unit):AutoCloseable {
 private val busy=AtomicBoolean(false)
 private val stopping=AtomicBoolean(false)
 private var worker:Thread?=null
 private val pool=Executors.newFixedThreadPool(2){task->Thread(task,"tango-parallel-speed").apply{priority=Thread.MIN_PRIORITY}}
 private data class Trial(val index:Int,val result:JSONObject?,val score:Double)
 private data class Batch(val trials:List<Trial>,val milliseconds:Double)
 private inner class State {
  var handle=0L
  var model:Interpreter?=null
  val input=ByteBuffer.allocateDirect(8448*4).order(ByteOrder.nativeOrder())
  val output=ByteBuffer.allocateDirect(512).order(ByteOrder.nativeOrder())
  fun run(speeds:DoubleArray,indices:List<Int>,pcm:FloatArray,sourceOffset:Double):Batch {
   android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
   val began=android.os.SystemClock.elapsedRealtimeNanos()
   if(indices.isEmpty()||stopping.get())return Batch(emptyList(),0.0)
   if(model==null)model=FileInputStream(bundle.model).channel.use{ch->Interpreter(ch.map(java.nio.channels.FileChannel.MapMode.READ_ONLY,0,ch.size()),Interpreter.Options().setNumThreads(1))}
   if(handle==0L)handle=NativeEngine.initFile(bundle.catalog.absolutePath)
   val trials=mutableListOf<Trial>()
   for(index in indices){
    if(stopping.get())break
    val speed=speeds[index];NativeEngine.reset(handle);var frame=0;var result:JSONObject?=null
    while(frame<10&&frame*4000.0*speed+7999*speed+33<pcm.size&&!stopping.get()){
     val offset=frame*4000.0*speed
     val window=NativeEngine.resample(pcm,offset,speed)
     if(!SignalGate.audible(window)){result=null;break}
     val mel=NativeEngine.features(window)
     input.rewind();input.asFloatBuffer().put(mel);output.rewind();model!!.run(input,output)
     val fp=FloatArray(128);output.rewind();output.asFloatBuffer().get(fp)
     record((sourceOffset+offset/8000).toFloat(),speed.toFloat(),fp)
     result=JSONObject(NativeEngine.search(handle,fp,1,speed.toFloat(),(sourceOffset+offset/8000).toFloat()));frame++
    }
    trials.add(Trial(index,result,result?.optJSONArray("candidates")?.optJSONObject(0)?.optDouble("score")?:-1.0))
   }
   return Batch(trials,(android.os.SystemClock.elapsedRealtimeNanos()-began)/1e6)
  }
  fun close(){model?.close();model=null;if(handle!=0L)NativeEngine.close(handle);handle=0L}
 }
 private val states=Array(2){State()}
 fun start(pcm:FloatArray,sourceOffset:Double,speedFactors:DoubleArray,onDone:(JSONObject,Long)->Unit){
  val speeds=speedFactors.copyOf()
  if(speeds.isEmpty()||stopping.get()||busy.getAndSet(true))return
  worker=Thread({
   android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
   val began=android.os.SystemClock.elapsedRealtimeNanos()
   try{
    val futures=(0 until 2).map{index->pool.submit(Callable{states[index].run(speeds,speeds.indices.filter{it%2==index},pcm,sourceOffset)})}
    // Drain every task even if one fails before permitting another snapshot.
    val completed=futures.map{runCatching{it.get()}}
    completed.firstOrNull{it.isFailure}?.getOrThrow()
    val batches=completed.map{it.getOrThrow()}
    val best=batches.flatMap{it.trials}.sortedWith(compareByDescending<Trial>{it.score}.thenBy{it.index}).firstOrNull{it.result!=null&&it.score> -1.0}
    if(!stopping.get()&&best!=null){
     val result=best.result!!.put("parallel_worker_count",2).put("tested_speed_factors",JSONArray(speeds.toList())).put("parallel_worker_ms",JSONArray(batches.map{it.milliseconds}))
     onDone(result,began)
    }
   }catch(e:Throwable){if(!stopping.get())onDone(JSONObject().put("error",e.stackTraceToString()),began)}
   finally{busy.set(false)}
  },"tango-speed-coordinator").apply{priority=Thread.MIN_PRIORITY;start()}
 }
 override fun close(){
  stopping.set(true);worker?.interrupt();pool.shutdownNow()
  val terminated=pool.awaitTermination(60,TimeUnit.SECONDS)
  worker?.join()
  check(terminated){"Parallel speed workers did not stop"}
  states.forEach{it.close()}
 }
}
