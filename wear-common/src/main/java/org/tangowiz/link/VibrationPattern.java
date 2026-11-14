package org.tangowiz.link;

/** Shared, deliberately short recognition signals. */
public final class VibrationPattern {
    private VibrationPattern() {}
    public static long[] timings(int pulses) {
        if (pulses == 1) return new long[]{0, 180};
        if (pulses == 2) return new long[]{0, 180, 140, 180};
        throw new IllegalArgumentException("Unknown vibration signal");
    }
    public static void play(android.content.Context context, int pulses) {
        long[] pattern = timings(pulses);
        android.app.NotificationManager manager = context.getSystemService(android.app.NotificationManager.class);
        String channelId = "vibration-signal-" + pulses;
        android.app.NotificationChannel channel = new android.app.NotificationChannel(channelId,
                pulses == 1 ? "Recording identified" : "Song ending", android.app.NotificationManager.IMPORTANCE_DEFAULT);
        channel.setSound(null, null); channel.enableVibration(true); channel.setVibrationPattern(pattern);
        manager.createNotificationChannel(channel);
        // Wear OS routes background attentional haptics through its notification collector.
        // Local-only prevents the phone alert from duplicating the independent watch alert.
        manager.notify(300 + pulses, new android.app.Notification.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(pulses == 1 ? "Recording identified" : "Song ending")
                .setContentText(pulses == 1 ? "TangoWiz" : "About 8 seconds remaining")
                .setVibrate(pattern).setLocalOnly(true).setTimeoutAfter(5000).build());
    }
}
