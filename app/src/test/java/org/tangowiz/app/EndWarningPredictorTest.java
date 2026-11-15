package org.tangowiz.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class EndWarningPredictorTest {
 @Test public void confidentIdentificationDoesNotNeedTheMatcherResetScore(){
  for(double speed:new double[]{.94,1,1.03}){
   EndWarningPredictor end=new EndWarningPredictor();
   for(double t:new double[]{70,75,80,85,90})end.observe(t,"song",.55,92,true,150-(100-t)/speed-29.5,150,60,speed);
   assertNotNull("A repeatedly identified 92% recording needs an ending estimate",end.prediction());
   assertEquals(100,end.prediction().endSeconds,1e-6);
  }
 }
 @Test public void shorterIdentifiedContextCanWarnWithoutWaitingThirtySeconds(){
  EndWarningPredictor end=new EndWarningPredictor();
  for(double t:new double[]{70,75,80})end.observe(t,"song",.8,99,true,150-(100-t)-4.5,150,10,1);
  assertNotNull(end.prediction());
 }
}
