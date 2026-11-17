package org.tangowiz.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class EndingVerificationStreamTest {
 @Test public void promotedRecordingUsesItsOwnRecentAudio(){assertEquals(EndingVerificationStream.Stream.ADAPTIVE,EndingVerificationStream.select(.94,.94,true));}
 @Test public void unavailableSpeedMustAbstain(){assertEquals(EndingVerificationStream.Stream.UNAVAILABLE,EndingVerificationStream.select(.94,1,true));assertEquals(EndingVerificationStream.Stream.UNAVAILABLE,EndingVerificationStream.select(.94,.94,false));}
 @Test public void ordinarySpeedUsesNormalStream(){assertEquals(EndingVerificationStream.Stream.NORMAL,EndingVerificationStream.select(1,.94,true));}
 @Test public void invalidSpeedMustAbstain(){assertEquals(EndingVerificationStream.Stream.UNAVAILABLE,EndingVerificationStream.select(Double.NaN,.94,true));}
}
