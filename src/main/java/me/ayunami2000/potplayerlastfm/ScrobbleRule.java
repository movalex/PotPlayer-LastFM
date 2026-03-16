package me.ayunami2000.potplayerlastfm;

public class ScrobbleRule {

    private static final long MAX_THRESHOLD_SECONDS = 3 * 60; // 3 minutes

    /**
     * Calculate the scrobble threshold in seconds.
     * Per Last.fm rules: scrobble after min(3 minutes, 50% of track duration).
     *
     * @param trackDurationMs total track duration in milliseconds
     * @return threshold in seconds after which the track should be scrobbled
     */
    public static long getThresholdSeconds(long trackDurationMs) {
        long halfDurationSec = trackDurationMs / 2000;
        return Math.min(MAX_THRESHOLD_SECONDS, halfDurationSec);
    }

    /**
     * Check whether a track should be scrobbled based on elapsed play time.
     *
     * @param elapsedSeconds seconds the track has been playing
     * @param trackDurationMs total track duration in milliseconds
     * @return true if the track has been playing long enough to scrobble
     */
    public static boolean shouldScrobble(long elapsedSeconds, long trackDurationMs) {
        return elapsedSeconds > getThresholdSeconds(trackDurationMs);
    }
}
