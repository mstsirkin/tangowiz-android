package org.tangowiz.link;
import org.junit.Test;
import static org.junit.Assert.*;
public class VibrationPatternTest {
 @Test public void individualTicksAreVeryShort(){
  for(int pulses:new int[]{1,2}){long[] t=VibrationPattern.timings(pulses);for(int i=1;i<t.length;i+=2)assertTrue("A tick must be at most 15 ms",t[i]>0&&t[i]<=15);}
 }
 @Test public void doubleTickIsStillBrief(){long total=0;for(long t:VibrationPattern.timings(2))total+=t;assertTrue("Two ticks should finish within 160 ms",total<=160);}
}
