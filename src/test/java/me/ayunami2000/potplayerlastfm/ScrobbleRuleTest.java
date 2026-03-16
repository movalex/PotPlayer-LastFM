package me.ayunami2000.potplayerlastfm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScrobbleRuleTest {

    @Test
    void shortTrackThresholdIs50Percent() {
        // 2 minute track (120,000 ms) → threshold = 60s (50%)
        assertEquals(60, ScrobbleRule.getThresholdSeconds(120_000));
    }

    @Test
    void longTrackThresholdCapsAt3Minutes() {
        // 10 minute track (600,000 ms) → threshold = 180s (3 min cap, not 300s)
        assertEquals(180, ScrobbleRule.getThresholdSeconds(600_000));
    }

    @Test
    void exactBoundaryTrack() {
        // 6 minute track (360,000 ms) → 50% = 180s = 3 min cap
        assertEquals(180, ScrobbleRule.getThresholdSeconds(360_000));
    }

    @Test
    void veryShortTrack() {
        // 30 second track (30,000 ms) → threshold = 15s
        assertEquals(15, ScrobbleRule.getThresholdSeconds(30_000));
    }

    @Test
    void shouldScrobbleBeforeThreshold() {
        // 2 min track, 30s elapsed → should NOT scrobble (threshold is 60s)
        assertFalse(ScrobbleRule.shouldScrobble(30, 120_000));
    }

    @Test
    void shouldScrobbleAtThreshold() {
        // 2 min track, 60s elapsed → should NOT scrobble (needs to be >threshold)
        assertFalse(ScrobbleRule.shouldScrobble(60, 120_000));
    }

    @Test
    void shouldScrobbleAfterThreshold() {
        // 2 min track, 61s elapsed → should scrobble
        assertTrue(ScrobbleRule.shouldScrobble(61, 120_000));
    }

    @Test
    void zeroLengthTrack() {
        assertEquals(0, ScrobbleRule.getThresholdSeconds(0));
    }
}
