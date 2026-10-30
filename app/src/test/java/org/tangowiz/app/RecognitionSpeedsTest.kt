package org.tangowiz.app

import org.junit.Assert.*
import org.junit.Test

class RecognitionSpeedsTest {
 @Test fun defaultIncludesNormalAndFiveTestedFallbacks(){
  assertEquals(6,RecognitionSpeeds.defaults.size)
  assertArrayEquals(doubleArrayOf(.975,.985,.995,1.015,1.02),RecognitionSpeeds.factors(RecognitionSpeeds.defaults),1e-12)
 }
 @Test fun normalOnlyDisablesFallback(){assertEquals(0,RecognitionSpeeds.factors(listOf(0.0)).size)}
 @Test fun currentFiveSpeedRangeCanBeRestored(){
  assertArrayEquals(doubleArrayOf(.97,.985,1.015,1.03),RecognitionSpeeds.factors(RecognitionSpeeds.parse("-3,-1.5,0,1.5,3")),1e-12)
 }
 @Test fun invalidSettingsAreRejected(){
  for(value in listOf("", "0,0", "1,2", "0,NaN", "0,Infinity", "0,3.1", "0,-3.1", "0,no", "0,1,1.00000001", "-3,-2.5,-2,-1.5,-1,0,1,2,2.5,3")){
   assertTrue(value,runCatching{RecognitionSpeeds.parse(value)}.isFailure)
  }
 }
}
