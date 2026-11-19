package org.tangowiz.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class MusicalEndpointTest {
 @Test public void removesKnownTrailingSilence(){assertEquals(180,MusicalEndpoint.resolve(195,180,195),0);}
 @Test public void ignoresWrongReferenceOrInvalidMeasurements(){
  for(double[] v:new double[][]{{195,180,196},{195,196,195},{195,0,195},{195,Double.NaN,195},{195,180,Double.NaN}})assertEquals(195,MusicalEndpoint.resolve(v[0],v[1],v[2]),0);
 }

 @Test public void finalPartialFingerprintDoesNotTruncateCalculatedAudioEnd(){
  assertEquals(200.8,MusicalEndpoint.resolve(200,200.8,200,201),0);
  assertEquals(200,MusicalEndpoint.resolve(200,201.2,200,201),0);
  assertEquals(200,MusicalEndpoint.resolve(200,200.8,201,201),0);
 }
}
