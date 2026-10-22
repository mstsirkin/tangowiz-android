package org.tangowiz.app

/** Reject digital silence, without normalizing noise into a confident fingerprint. */
object SignalGate {
 const val MIN_RMS=1e-4
 fun rms(pcm:FloatArray):Double {if(pcm.isEmpty())return 0.0;var energy=0.0;for(x in pcm)energy+=x.toDouble()*x;return kotlin.math.sqrt(energy/pcm.size)}
 fun audible(pcm:FloatArray)=rms(pcm)>=MIN_RMS
}
