package org.tangowiz.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayLabelNormalizationTest {
 private fun labels(a:String,b:String)=MetadataLabels.resolve(
  Candidate("recording", "Orchestra", a, metadataAlternatives=mapOf("title" to listOf(a,b))),"title")

 @Test fun equivalentDisplayFormattingCollapses() {
  for((a,b) in listOf("C. T. V." to "CTV", "No te quiero más" to "Notequieromas",
   "María Remedios" to "MARIA REMEDIOS", "Cardo\u00a0azul" to "Cardo azul")) {
   assertEquals("Expected equivalent display labels: $a / $b",1,labels(a,b).size)
  }
 }
 @Test fun wordsAndNumbersRemainDistinct() {
  for((a,b) in listOf("Anoche a las 4" to "Anoche a las cuatro", "De 6 a 7" to "De seis a siete",
   "De 6 a 7" to "De 6 a 8", "No te quiero más" to "Te quiero más")) {
   assertEquals("Must retain distinct labels: $a / $b",2,labels(a,b).size)
  }
 }
 @Test fun otherPunctuationAndAnnotationsRemainDistinct() {
  for((a,b) in listOf("A-B" to "A B", "A, B" to "A B", "A/B" to "A B",
   "Pasional (1951)" to "Pasional", "Desde el alma (Vals 1970)" to "Desde el alma")) {
   assertEquals("Must retain distinct labels: $a / $b",2,labels(a,b).size)
  }
 }
 @Test fun displayNormalizationPreservesRecordingIdentityAndOriginalMetadata() {
  val a=Candidate("one","Orchestra","C. T. V.",metadataAlternatives=mapOf("title" to listOf("CTV")))
  val b=Candidate("two","Orchestra","CTV")
  a.display("title")
  assertEquals("C. T. V.",a.title)
  assertEquals(listOf("CTV"),a.metadataAlternatives["title"])
  assertEquals(false,a.sameRecording(b))
 }
}
