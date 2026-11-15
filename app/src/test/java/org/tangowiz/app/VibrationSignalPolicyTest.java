package org.tangowiz.app;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.tangowiz.app.VibrationSignalPolicy.Signal.*;

public class VibrationSignalPolicyTest {
    private SongEndPredictor predictor(double speed) {
        SongEndPredictor p = new SongEndPredictor();
        for (double t : new double[]{80,85,90})
            p.observe(t,"song",.8,150-(100-t)/speed-29.5,150,60,speed);
        return p;
    }
    @Test public void stableIdentificationOnceAndEightSecondWarningAtBothSpeeds() {
        for (double speed : new double[]{.97,1,1.03}) {
            VibrationSignalPolicy p = new VibrationSignalPolicy();
            SongEndPredictor end = predictor(speed);
            assertEquals(NONE,p.observe(80,"song",80,true,null));
            assertEquals(IDENTIFIED,p.observe(82,"song",80,true,null));
            assertEquals(NONE,p.observe(91.5,"song",80,true,end.prediction()));
            assertEquals(ENDING,p.observe(92,"song",80,true,end.prediction()));
            assertEquals(NONE,p.observe(92.5,"song",80,true,end.prediction()));
            assertNotNull(end.takeReset(100));
        }
    }
    @Test public void weakAmbiguousAndFlickeringMatchesDoNotBuzz() {
        VibrationSignalPolicy p = new VibrationSignalPolicy();
        assertEquals(NONE,p.observe(0,"song",69,true,null));
        assertEquals(NONE,p.observe(3,"song",90,false,null));
        assertEquals(NONE,p.observe(4,"song",90,true,null));
        assertEquals(NONE,p.observe(5,"rival",90,true,null));
        assertEquals(NONE,p.observe(6,"song",90,true,null));
        assertEquals(IDENTIFIED,p.observe(8,"song",90,true,null));
        assertEquals(NONE,p.observe(9,"",0,false,null));
        assertEquals(NONE,p.observe(10,"song",90,true,null));
        assertEquals(NONE,p.observe(12,"song",90,true,null));
    }
    @Test public void staleWrongAndLateEndingsDoNotBuzz() {
        VibrationSignalPolicy p = new VibrationSignalPolicy();
        p.observe(60,"song",90,true,null);p.observe(62,"song",90,true,null);
        assertEquals(NONE,p.observe(93,"song",90,true,predictor(1).prediction()));
        p.clear();p.observe(80,"rival",90,true,null);p.observe(82,"rival",90,true,null);
        assertEquals(NONE,p.observe(92,"rival",90,true,predictor(1).prediction()));
    }
    @Test public void backloggedRecognitionDoesNotConsumeTheIdentificationSignal() {
        VibrationSignalPolicy p = new VibrationSignalPolicy();
        assertEquals(NONE,p.observe(80,"song",90,true,null,true));
        assertEquals(NONE,p.observe(82,"song",90,true,null,false));
        assertEquals(IDENTIFIED,p.observe(83,"song",90,true,null,true));
        assertEquals(NONE,p.observe(84,"song",90,true,null,true));
    }
    @Test public void aBriefConfidenceDipAtTheDeadlineDoesNotLoseAnArmedEnding() {
        VibrationSignalPolicy p=new VibrationSignalPolicy();
        p.observe(80,"song",90,true,null);p.observe(82,"song",90,true,null);
        assertEquals(NONE,p.observe(91.5,"song",90,true,predictor(1).prediction()));
        assertEquals(ENDING,p.observe(92,"song",60,false,predictor(1).prediction()));
    }
    @Test public void aSkippedWindowCanCrossTheDeadlineWithoutLosingTheWarning() {
        VibrationSignalPolicy p=new VibrationSignalPolicy();
        p.observe(80,"song",90,true,null);p.observe(82,"song",90,true,null);
        assertEquals(NONE,p.observe(91.5,"song",90,true,predictor(1).prediction()));
        assertEquals(ENDING,p.observe(93.1,"song",90,true,predictor(1).prediction()));
    }
    @Test public void clearAllowsSameRecordingAgain() {
        VibrationSignalPolicy p = new VibrationSignalPolicy();
        p.observe(1,"song",90,true,null);
        assertEquals(IDENTIFIED,p.observe(3,"song",90,true,null));
        p.clear();p.observe(4,"song",90,true,null);
        assertEquals(IDENTIFIED,p.observe(6,"song",90,true,null));
    }
}
