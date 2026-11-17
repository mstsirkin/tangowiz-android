package org.tangowiz.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class EndingConsensusTest {
 private EndingConsensus.Candidate c(String f,String s,double score,double now,double end){return new EndingConsensus.Candidate(f,s,score,200-(end-now)-14.5,200,1);}
 @Test public void stableLowerScoreCanWarnWithRecentConfirmation(){
  EndingConsensus p=new EndingConsensus();EndingConsensus.Warning w=null;
  for(int t=70;t<=92;t++)w=p.observe(t,t,30,true,c("A","copy",.55,t,100),null,()->c("A","short",.6,92,100));
  assertNotNull(w);assertEquals(100,w.end,0);
 }
 @Test public void rejectsWrongRecentAudioAndBacklog(){
  for(boolean backlog:new boolean[]{true,false}){EndingConsensus p=new EndingConsensus();
   for(int t=70;t<=94;t++)assertNull(p.observe(t,t,30,!backlog,c("A","copy",.55,t,100),null,()->c("B","short",.9,92,100)));
  }
 }
 @Test public void noShortQueryBeforeDeadlineOrAfterDuplicate(){
  EndingConsensus p=new EndingConsensus();int[] calls={0};for(int t=70;t<=94;t++)p.observe(t,t,30,true,c("A","copy",.55,t,100),null,()->{calls[0]++;return c("A","short",.6,92,100);});assertEquals(1,calls[0]);
 }
 @Test public void competingSongBlocksWarning(){
  EndingConsensus p=new EndingConsensus();for(int t=70;t<=94;t++)assertNull(p.observe(t,t,30,true,c("A","one",.55,t,100),c("B","two",.54,t,100),()->c("A","short",.6,92,100)));
 }
 @Test public void earliestStableCopyWins(){
  EndingConsensus p=new EndingConsensus();EndingConsensus.Warning found=null;
  for(int t=70;t<=92;t++){EndingConsensus.Warning w=p.observe(t,t,30,true,c("A","long",.57,t,112),c("A","short",.55,t,100),()->c("A","recent",.6,92,100));if(w!=null)found=w;}
  assertNotNull(found);assertEquals(100,found.end,0);
 }
}
