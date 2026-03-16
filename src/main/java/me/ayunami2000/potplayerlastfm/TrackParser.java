package me.ayunami2000.potplayerlastfm;

public class TrackParser {

    public static class ParsedTrack {
        public final String artist;
        public final String track;

        public ParsedTrack(String artist, String track) {
            this.artist = artist;
            this.track = track;
        }
    }

    /**
     * Parse artist and track name from a PotPlayer window title.
     *
     * Expected format: "Artist - Track - PotPlayer"
     * Also handles file extensions (e.g., "Artist - Track.mp3 - PotPlayer")
     * and date suffixes (e.g., "Artist - Track (2024-01-15) - PotPlayer").
     *
     * @return ParsedTrack with artist and track, or null if unparseable
     */
    public static ParsedTrack parseFromWindowTitle(String windowTitle, String playerName) {
        if (windowTitle == null || playerName == null) return null;

        String end = " - " + playerName;
        String song;

        if (windowTitle.equals(playerName)) {
            return null;
        } else if (windowTitle.endsWith(end)) {
            song = windowTitle.substring(0, windowTitle.lastIndexOf(end));
        } else {
            return null;
        }

        if (song.isEmpty()) return null;

        // Remove file extension if present
        if (song.matches(".*\\.[a-zA-Z0-9]+$")) {
            song = song.substring(0, song.lastIndexOf('.'));
            song = song.replace('_', ' ');
        } else {
            // Remove trailing date in parentheses like (2024-01-15)
            song = song.replaceFirst(" \\([0-9-]+\\)$", "");
        }

        int sep = song.indexOf(" - ");
        if (sep < 0) return null;

        String artist = song.substring(0, sep);
        String track = song.substring(sep + 3);

        if (artist.isEmpty() || track.isEmpty()) return null;

        return new ParsedTrack(artist, track);
    }

    /**
     * Extract the song portion from a window title (without the player name suffix).
     * Used for display purposes when the title can't be parsed into artist/track.
     */
    public static String extractSongTitle(String windowTitle, String playerName) {
        if (windowTitle == null || playerName == null) return null;
        String end = " - " + playerName;
        if (windowTitle.equals(playerName)) return null;
        if (windowTitle.endsWith(end)) {
            return windowTitle.substring(0, windowTitle.lastIndexOf(end));
        }
        return null;
    }
}
