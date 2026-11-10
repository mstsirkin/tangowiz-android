package org.tangowiz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogKeywordLookupTest {
 private val table=mapOf(
  "orquesta tipica de pedro laurenz" to CatalogKeywords.Hint("Laurenz",false),
  "pedro laurenz" to CatalogKeywords.Hint("Laurenz",false),
  "de angelis" to CatalogKeywords.Hint("De Angelis",false),
  "juan d arienzo" to CatalogKeywords.Hint("D’Arienzo",false)
 )
 @Test fun fullCreditWinsBeforePrefixFallback() {
  assertEquals("Laurenz",CatalogKeywords.findHint(table,"Orquesta Típica de Pedro Laurenz")?.text)
  assertEquals("De Angelis",CatalogKeywords.findHint(table,"Orquesta Típica De Angelis")?.text)
  assertEquals("D’Arienzo",CatalogKeywords.findHint(table,"Orq. Típ. de Juan D’Arienzo")?.text)
 }
 @Test fun unknownCreditDoesNotLoseItsDeOrGainGuess() {
  assertNull(CatalogKeywords.findHint(table,"Orquesta Típica de Persona Desconocida"))
 }
}
