package org.tangowiz.app
import android.content.res.AssetManager
object NativeEngine {
 init{System.loadLibrary("tangowiz")}
 external fun init(assets:AssetManager):Long
 external fun initFile(path:String):Long
 external fun search(handle:Long,embeddings:FloatArray,count:Int,speed:Float,offset:Float):String
 external fun recent(handle:Long,count:Int):Double
 external fun reset(handle:Long)
 external fun close(handle:Long)
 external fun resample(history:FloatArray,start:Double,speed:Double):FloatArray
 external fun features(pcm:FloatArray):FloatArray
}
