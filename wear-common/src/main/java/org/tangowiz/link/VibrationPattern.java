package org.tangowiz.link;

/** A subtle local tick. Never posts a notification or wakes the display. */
public final class VibrationPattern {
    public static final float TICK_SCALE = .10f;
    public static final int AMPLITUDE = 20;
    private VibrationPattern() {}
    public static long[] timings(int pulses) {
        if (pulses == 1) return new long[]{0,12};
        if (pulses == 2) return new long[]{0,12,100,12};
        throw new IllegalArgumentException("Unknown vibration signal");
    }
    public static int[] amplitudes(int pulses) {
        timings(pulses);
        return pulses==1 ? new int[]{0,AMPLITUDE} : new int[]{0,AMPLITUDE,0,AMPLITUDE};
    }
    public static void clearLegacyNotifications(android.content.Context context) {
        android.app.NotificationManager manager=context.getSystemService(android.app.NotificationManager.class);
        if(manager!=null){manager.cancel(301);manager.cancel(302);}
    }
    public static boolean play(android.content.Context context,int pulses) {
        timings(pulses);clearLegacyNotifications(context);
        android.os.Vibrator vibrator=context.getSystemService(android.os.Vibrator.class);
        if(vibrator==null||!vibrator.hasVibrator())return false;
        android.os.VibrationEffect effect;
        if(android.os.Build.VERSION.SDK_INT>=31&&vibrator.arePrimitivesSupported(android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK)[0]){
            android.os.VibrationEffect.Composition composition=android.os.VibrationEffect.startComposition();
            composition.addPrimitive(android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK,TICK_SCALE,0);
            if(pulses==2)composition.addPrimitive(android.os.VibrationEffect.Composition.PRIMITIVE_LOW_TICK,TICK_SCALE,100);
            effect=composition.compose();
        }else if(vibrator.hasAmplitudeControl()){
            effect=android.os.VibrationEffect.createWaveform(timings(pulses),amplitudes(pulses),-1);
        }else{
            // Never substitute an uncontrolled full-strength buzz on unsupported hardware.
            return false;
        }
        vibrator.vibrate(effect,new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build());
        return true;
    }
}
