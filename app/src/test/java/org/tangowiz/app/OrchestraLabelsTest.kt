package org.tangowiz.app

import org.junit.Assert.assertEquals
import org.junit.Test

class OrchestraLabelsTest {
 @Test fun shortNamesAndEquivalentCredits() {
  val c=Candidate("1", "Fulvio Salamanca y su Orq. Tip.", "", metadataAlternatives=mapOf("orchestra" to listOf("Fulvio Salamanca", "Orquesta Fulvio Salamanca")))
  assertEquals("Fulvio Salamanca", c.display("orchestra"))
  assertEquals("fulvio salamanca", MetadataLabels.orchestraKey(c))
  assertEquals("Fulvio Salamanca y su Orq. Tip.", c.orchestra)
 }
 @Test fun fullSuffixAndDistinctFormations() {
  assertEquals("Juan D’Arienzo", Candidate("1", "Juan D’Arienzo y su Orquesta Típica", "").display("orchestra"))
  assertEquals("Sexteto Fulvio Salamanca", Candidate("2", "Sexteto Fulvio Salamanca", "").display("orchestra"))
  assertEquals("Fulvio Salamanca y su Trío", Candidate("3", "Fulvio Salamanca y su Trío", "").display("orchestra"))
 }
}
