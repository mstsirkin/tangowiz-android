package org.tangowiz.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class RecentAudioWindowTest {
 @Test public void retainsOnlyMostRecentSamplesInOrder(){RecentAudioWindow w=new RecentAudioWindow(4);w.append(new short[]{1,2,3});assertArrayEquals(new short[]{1,2,3},w.snapshot());w.append(new short[]{4,5,6});assertArrayEquals(new short[]{3,4,5,6},w.snapshot());}
 @Test public void snapshotIsImmutableAcrossFurtherCapture(){RecentAudioWindow w=new RecentAudioWindow(2);w.append(new short[]{1,2});short[] saved=w.snapshot();w.append(new short[]{3,4,5});assertArrayEquals(new short[]{1,2},saved);assertArrayEquals(new short[]{4,5},w.snapshot());}
 @Test public void twoSecondsAtEightKhz(){RecentAudioWindow w=new RecentAudioWindow(16000);for(int block=0;block<10;block++){short[] pcm=new short[4000];java.util.Arrays.fill(pcm,(short)block);w.append(pcm);}short[] s=w.snapshot();assertEquals(16000,s.length);assertEquals(6,s[0]);assertEquals(9,s[15999]);}
}
