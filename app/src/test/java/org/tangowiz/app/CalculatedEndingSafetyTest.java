package org.tangowiz.app;
import org.junit.Test;
import static org.junit.Assert.*;
/** Physical vector bounds and semantic musical endings are independent. */
public class CalculatedEndingSafetyTest {
 @Test public void retainedVectorsPastMusicalEndCannotArmNegativePrediction(){
  SongEndPredictor p=new SongEndPredictor();
  double end=MusicalEndpoint.resolve(200,150,200);
  for(double reference:new double[]{150,151,175,199.5})p.observe(100,"source",.9,reference,end,60,1);
  assertNull(p.prediction());assertNull(p.takeReset(110));
 }
 @Test public void longMatchingSequenceCrossingEndDoesNotArmPastBoundary(){
  SongEndPredictor p=new SongEndPredictor();
  // Its first reference frame precedes the end, but its latest frame is past it.
  for(double t:new double[]{100,105,110})p.observe(t,"source",.9,140,MusicalEndpoint.resolve(200,150,200),60,1);
  assertNull(p.prediction());assertNull(p.takeReset(120));
 }
 @Test public void musicalEndDoesNotReplaceIndexedMatcherResetTiming(){
  assertEquals(150,MusicalEndpoint.resolve(200,150,200,201),0);
  SongEndPredictor p=new SongEndPredictor();
  for(double t=20;t<=90;t+=5)p.observe(t,"source",.9,100+t-20,200,60,1);
  assertNotNull(p.prediction());assertEquals(90.5,p.prediction().endSeconds,1e-8);
  assertNull(p.takeReset(90));assertNotNull(p.takeReset(90.5));assertNull(p.takeReset(91));
 }
 @Test public void wrongIndexBindingPreservesOriginalTiming(){
  assertEquals(200,MusicalEndpoint.resolve(200,150,201),0);
 }
}
