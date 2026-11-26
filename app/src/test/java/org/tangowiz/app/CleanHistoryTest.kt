package org.tangowiz.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CleanHistoryTest {
 private fun row(at:Double,id:String="a",title:String="Nostalgias",orchestra:String="Example",score:Double=.8,
                 origin:Double?=0.0,confirmed:Boolean=false,session:String="one",date:String="") =
  CleanHistory.Observation(session,at,score,origin,confirmed,DisplayLabelKey.normalizeWorkTitle(title),
   orchestra,"",date,setOf(id))

 @Test fun keepsConfirmedAndSustainedTracksAndRejectsShortGuesses(){
  val rows=listOf(row(0.0),row(1.0,"noise",title="Noise"),row(5.0),row(10.0),
   row(40.0,"b",title="Uno",confirmed=true))
  assertEquals(listOf(listOf(0,2,3),listOf(4)),CleanHistory.plays(rows))
 }

 @Test fun acceptsAlignedPairsButRejectsUnalignedPairs(){
  val rows=listOf(row(0.0),row(9.0),row(60.0,"b",title="Uno",origin=30.0),
   row(69.0,"b",title="Uno",origin=34.0))
  assertEquals(listOf(listOf(0,1)),CleanHistory.plays(rows))
 }

 @Test fun joinsAdjacentAcceptedWorkAndOrchestraDespitePositionJump(){
  val rows=listOf(row(0.0,title="El Nostalgias (Lochness)",confirmed=true),
   row(70.0,"b",title="Nostalgias [Encadenados]",origin=60.0,confirmed=true))
  assertEquals(listOf(listOf(0,1)),CleanHistory.plays(rows))
 }

 @Test fun keepsOrchestrasAndSessionsApart(){
  val rows=listOf(row(0.0,confirmed=true),row(5.0,"b",orchestra="Other",confirmed=true),
   row(0.0,"b",orchestra="Other",confirmed=true,session="two"))
  assertEquals(listOf(listOf(0),listOf(1),listOf(2)),CleanHistory.plays(rows))
 }

 @Test fun acceptsOnlyCoherentLongWeakEvidence(){
  val rows=(0..5).map{row(it*5.0,score=.6)} + (0..5).map{row(60.0+it*5,"b",title="Uno",score=.6,origin=it*4.0)}
  assertEquals(listOf((0..5).toList()),CleanHistory.plays(rows))
 }

 @Test fun legacyWithoutPositionCanStillSustainAPlay(){
  assertEquals(listOf(listOf(0,1,2)),CleanHistory.plays((0..2).map{row(it*5.0,origin=null)}))
 }

 @Test fun conflictingRecordingDatesAreWithheld(){
  assertEquals("",CleanHistory.displayDate(listOf("1942", "1943"),"1942"))
  assertEquals("1942-03-05",CleanHistory.displayDate(listOf("1942", "1942-03-05"),"1942"))
 }
}
