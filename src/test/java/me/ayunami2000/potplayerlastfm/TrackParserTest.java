package me.ayunami2000.potplayerlastfm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrackParserTest {

    private static final String PLAYER = "PotPlayer";

    @Test
    void parseStandardFormat() {
        var result = TrackParser.parseFromWindowTitle("Norah Jones - Lonestar - PotPlayer", PLAYER);
        assertNotNull(result);
        assertEquals("Norah Jones", result.artist);
        assertEquals("Lonestar", result.track);
    }

    @Test
    void parseWithFileExtension() {
        var result = TrackParser.parseFromWindowTitle("Artist_Name - Song.mp3 - PotPlayer", PLAYER);
        assertNotNull(result);
        assertEquals("Artist Name", result.artist);
        assertEquals("Song", result.track);
    }

    @Test
    void parseWithDateBrackets() {
        var result = TrackParser.parseFromWindowTitle("Artist - Track (2024-01-15) - PotPlayer", PLAYER);
        assertNotNull(result);
        assertEquals("Artist", result.artist);
        assertEquals("Track", result.track);
    }

    @Test
    void parseUnparseableNoSeparator() {
        var result = TrackParser.parseFromWindowTitle("SomeFile.mp3 - PotPlayer", PLAYER);
        assertNull(result);
    }

    @Test
    void parseMultipleDashes() {
        var result = TrackParser.parseFromWindowTitle("AC/DC - Back in Black - PotPlayer", PLAYER);
        assertNotNull(result);
        assertEquals("AC/DC", result.artist);
        assertEquals("Back in Black", result.track);
    }

    @Test
    void parsePotPlayerOnly() {
        var result = TrackParser.parseFromWindowTitle("PotPlayer", PLAYER);
        assertNull(result);
    }

    @Test
    void parseNullInput() {
        assertNull(TrackParser.parseFromWindowTitle(null, PLAYER));
        assertNull(TrackParser.parseFromWindowTitle("test", null));
    }

    @Test
    void parseWrongSuffix() {
        var result = TrackParser.parseFromWindowTitle("Artist - Track - VLC", PLAYER);
        assertNull(result);
    }

    @Test
    void extractSongTitleStandard() {
        assertEquals("Artist - Track", TrackParser.extractSongTitle("Artist - Track - PotPlayer", PLAYER));
    }

    @Test
    void extractSongTitlePlayerOnly() {
        assertNull(TrackParser.extractSongTitle("PotPlayer", PLAYER));
    }

    @Test
    void parseTrackWithUnderscoresAndExtension() {
        var result = TrackParser.parseFromWindowTitle("Some_Artist - Some_Track.flac - PotPlayer", PLAYER);
        assertNotNull(result);
        assertEquals("Some Artist", result.artist);
        assertEquals("Some Track", result.track);
    }
}
