package org.tangowiz.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayLabelNormalizationTest {
 private fun watchTitle(vararg titles:String)=Candidate(
  "recording","Orchestra",titles.first(),metadataAlternatives=mapOf("title" to titles.toList())
 ).watchTitle()

 @Test fun phoneUsesTheWatchTitleRule() {
  for (titles in listOf(
   listOf("Gran Hotel Victoria", "Gran Hotel Victoria (Hotel Victoria)"),
   listOf("Cacareando [Milonga]", "Cacareando"),
   listOf("Desde el alma", "Desde el Alma (En Vivo)")
  )) {
   val candidate = Candidate(
    "recording", "Orchestra", titles.first(), metadataAlternatives = mapOf("title" to titles)
   )
   assertEquals(candidate.watchTitle(), candidate.display("title"))
  }
 }

 @Test fun displayHidesStandaloneLiveNotesAndPreservesTheSourceTitle() {
  val original = "Desde el Alma (En Vivo)"
  val candidate = Candidate(
   "recording", "Orchestra", original,
   metadataAlternatives = mapOf("title" to listOf("Desde el alma", original))
  )
  assertEquals("Desde el alma", candidate.display("title"))
  assertEquals("Desde el Alma", watchTitle("Desde el Alma [En Vivo]"))
  assertEquals(original, candidate.title)
  assertEquals("Desde el Alma (En Vivo 2008)", watchTitle("Desde el Alma (En Vivo 2008)"))
 }

 @Test fun trailingGenreWordsCollapseWhenTheBaseTitleIsPresent() {
  for (genre in listOf("Milonga", "Tango", "Vals", "Tango Vals")) {
   assertEquals("Cacareando", watchTitle("Cacareando ${genre}", "Cacareando"))
  }
 }

 @Test fun trailingGenreWordsRemainInStandaloneTitles() {
  assertEquals("El último tango", watchTitle("El último tango"))
  assertEquals("Milonga", watchTitle("Milonga"))
 }

 @Test fun standaloneAlternateNameCollapsesIntoTheAnnotatedTitle() {
  assertEquals(
   "Gran Hotel Victoria (Hotel Victoria)",
   watchTitle("Hotel Victoria", "Gran Hotel Victoria", "Gran Hotel Victoria (Hotel Victoria)")
  )
  assertEquals("Title [Alternate]", watchTitle("Title [Alternate]", "Alternate"))
 }

 @Test fun sharedAlternateNamesAndVersionNotesDoNotMergeDistinctLabels() {
  assertEquals("Shared / First (Shared) / Second (Shared) ?", watchTitle("Shared", "First (Shared)", "Second (Shared)"))
  assertEquals("1951 / Pasional (1951) ?", watchTitle("1951", "Pasional (1951)"))
 }

 @Test fun watchShowsOneTitleForAnAlternateNameAndDropsGenreTags() {
  assertEquals("Argañaraz (Aquellas farras)",watchTitle("Argañaraz","Argañaraz (Aquellas farras)"))
  assertEquals("Más allá",watchTitle("Más allá (tango)"))
  assertEquals("Más allá",watchTitle("Más allá [TANGO]","Más allá"))
 }

 @Test fun watchKeepsRecordingVersionsAndDistinctWorkClaims() {
  assertEquals("Pasional (1951)",watchTitle("Pasional (1951)"))
  assertEquals("Desde el alma (Vals 1970) / Desde el alma ?",watchTitle("Desde el alma (Vals 1970)","Desde el alma"))
  assertEquals("Argañaraz (Aquellas farras) / Sombras ?",watchTitle("Argañaraz (Aquellas farras)","Sombras"))
 }

 @Test fun watchKeepsTitleOrderWhenAnAlternateNameAppearsLater() {
  assertEquals("Argañaraz (Aquellas farras) / Sombras ?",watchTitle("Argañaraz","Sombras","Argañaraz (Aquellas farras)"))
 }

 private fun labels(a:String,b:String)=MetadataLabels.resolve(
  Candidate("recording", "Orchestra", a, metadataAlternatives=mapOf("title" to listOf(a,b))),"title")

 @Test fun equivalentDisplayFormattingCollapses() {
  for((a,b) in listOf("C. T. V." to "CTV", "No te quiero más" to "Notequieromas",
   "María Remedios" to "MARIA REMEDIOS", "Cardo\u00a0azul" to "Cardo azul", "A, B" to "A B")) {
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
  for((a,b) in listOf("A-B" to "A B", "Tema 1,2" to "Tema 12", "A/B" to "A B",
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
