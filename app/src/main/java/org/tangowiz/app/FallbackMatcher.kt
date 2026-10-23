package org.tangowiz.app
import android.content.res.AssetManager
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

// Separate bounded snapshot worker; live baseline never waits for it.
class FallbackMatcher(private val bundle:BundleSnapshot,private val record:(Float,Float,FloatArray)->Unit):AutoCloseable {
 private val busy=AtomicBoolean(false)
 private val stopping=AtomicBoolean(false)
 private var worker:Thread?=null
 private var handle=0L
 private var model:Interpreter?=null
 fun start(pcm:FloatArray,sourceOffset:Double,onDone:(JSONObject,Long)->Unit){
  if(stopping.get()||busy.getAndSet(true))return
  worker=Thread({
   android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
   val began=android.os.SystemClock.elapsedRealtimeNanos()
   try{
    if(model==null)model=FileInputStream(bundle.model).channel.use{ch->Interpreter(ch.map(java.nio.channels.FileChannel.MapMode.READ_ONLY,0,ch.size()),Interpreter.Options().setNumThreads(1))}
    if(handle==0L)handle=NativeEngine.initFile(bundle.catalog.absolutePath)
    val input=ByteBuffer.allocateDirect(8448*4).order(ByteOrder.nativeOrder());val output=ByteBuffer.allocateDirect(512).order(ByteOrder.nativeOrder())
    var best:JSONObject?=null;var bestScore=-1.0
    for(speed in doubleArrayOf(.97,.985,1.015,1.03)){
     if(stopping.get())break
     NativeEngine.reset(handle);var frame=0;var result:JSONObject?=null
     while(frame<10&&frame*4000.0*speed+7999*speed+33<pcm.size&&!stopping.get()){
      val offset=frame*4000.0*speed
      val window=NativeEngine.resample(pcm,offset,speed);if(!SignalGate.audible(window)){result=null;break};val mel=NativeEngine.features(window)
      input.rewind();input.asFloatBuffer().put(mel);output.rewind();model!!.run(input,output)
      val fp=FloatArray(128);output.rewind();output.asFloatBuffer().get(fp)
      record((sourceOffset+offset/8000).toFloat(),speed.toFloat(),fp)
      result=JSONObject(NativeEngine.search(handle,fp,1,speed.toFloat(),(sourceOffset+offset/8000).toFloat()));frame++
     }
     val score=result?.optJSONArray("candidates")?.optJSONObject(0)?.optDouble("score")?:-1.0
     if(score>bestScore){bestScore=score;best=result}
    }
    if(!stopping.get()&&best!=null)onDone(best!!,began)
   }catch(e:Throwable){if(!stopping.get())onDone(JSONObject().put("error",e.stackTraceToString()),began)}
   finally{if(stopping.get())release();busy.set(false)}
  },"tango-speed-fallback").apply{priority=Thread.MIN_PRIORITY;start()}
 }
 private fun release(){model?.close();model=null;if(handle!=0L)NativeEngine.close(handle);handle=0L}
 override fun close(){stopping.set(true);worker?.interrupt();worker?.join();if(worker?.isAlive!=true)release()}
}
