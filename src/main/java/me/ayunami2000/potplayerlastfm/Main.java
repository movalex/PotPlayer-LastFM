package me.ayunami2000.potplayerlastfm;

import com.del.potplayercontrol.api.JNAPotPlayerHelper;
import com.del.potplayercontrol.api.PlayStatus;
import com.del.potplayercontrol.api.PotPlayer;
import com.del.potplayercontrol.impl.JNAPotPlayer;
import com.del.potplayercontrol.impl.Window;
import com.sun.jna.platform.WindowUtils;
import com.sun.jna.platform.win32.User32;
import de.umass.lastfm.Authenticator;
import de.umass.lastfm.Caller;
import de.umass.lastfm.Session;
import de.umass.lastfm.Track;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.List;

import javax.swing.*;

public class Main {

    // Last.fm API application credentials (app-level, not user secrets)
    // Replace with your own from https://www.last.fm/api/account/create
    private static final String API_KEY = "YOUR_API_KEY";
    private static final String API_SECRET = "YOUR_API_SECRET";

    private static MenuItem statusItem;
    private static MenuItem nowPlayingItem;
    private static MenuItem scrobbleProgressItem;
    private static MenuItem lastScrobbleItem;
    private static TrayIcon trayIcon;

    // Track last set label values to avoid redundant setLabel calls
    private static String lastStatusLabel = "";
    private static String lastNowPlayingLabel = "";
    private static String lastScrobbleLabel = "";
    private static String lastTooltipText = "";

    public static void main(String[] args) throws IOException, InterruptedException {
        Caller.getInstance().setUserAgent("PotPlayer-LastFM");
        SessionStore sessionStore = new SessionStore();

        // Set up system tray
        PopupMenu popup = null;
        MenuItem logoutItem = null;
        if (SystemTray.isSupported()) {
            popup = new PopupMenu();
            trayIcon = new TrayIcon(ImageIO.read(Objects.requireNonNull(Main.class.getResource("/icon.png"))));
            final SystemTray tray = SystemTray.getSystemTray();

            statusItem = new MenuItem("Status: Starting...");
            statusItem.setEnabled(false);
            popup.add(statusItem);

            popup.addSeparator();

            nowPlayingItem = new MenuItem("Now Playing: --");
            nowPlayingItem.setEnabled(false);
            popup.add(nowPlayingItem);

            scrobbleProgressItem = new MenuItem("Scrobble: --");
            scrobbleProgressItem.setEnabled(false);
            popup.add(scrobbleProgressItem);

            lastScrobbleItem = new MenuItem("Last Scrobble: --");
            lastScrobbleItem.setEnabled(false);
            popup.add(lastScrobbleItem);

            popup.addSeparator();

            logoutItem = new MenuItem("Logout");
            final SessionStore storeRef = sessionStore;
            logoutItem.addActionListener(e -> {
                storeRef.clear();
                updateStatus("Status: Logged out");
                updateTooltip("PotPlayer LastFM - Logged out");
                JOptionPane.showMessageDialog(null, "Logged out. Restart the app to log in with a different account.");
                System.exit(0);
            });
            popup.add(logoutItem);

            MenuItem exitItem = new MenuItem("Exit PotPlayer LastFM");
            exitItem.addActionListener(e -> System.exit(0));
            popup.add(exitItem);

            trayIcon.setPopupMenu(popup);
            trayIcon.setToolTip("PotPlayer LastFM - Starting...");

            try {
                tray.add(trayIcon);
            } catch (AWTException e) {
                System.err.println("TrayIcon could not be added.");
            }
        } else {
            System.err.println("SystemTray is not supported");
        }

        // Authenticate
        Session session = authenticate(args, sessionStore);
        if (session == null) {
            System.err.println("Error authenticating with LastFM.");
            updateStatus("Status: Authentication FAILED");
            updateTooltip("PotPlayer LastFM - Auth Failed");
            if (trayIcon != null) {
                trayIcon.displayMessage("PotPlayer LastFM", "Authentication failed! Check credentials.", TrayIcon.MessageType.ERROR);
            }
            System.exit(-1);
            return;
        }

        String username = session.getUsername() != null ? session.getUsername() : "unknown";
        updateStatus("Status: Connected as " + username);
        updateTooltip("PotPlayer LastFM - Waiting for PotPlayer...");

        // Main scrobbling loop
        String playerName = "PotPlayer";
        String end = " - " + playerName;
        PotPlayer player;

        while (true) {
            List<Window> windows = JNAPotPlayerHelper.getAllPlayerWindows(
                    window -> window.getWindowText().endsWith(end) || window.getWindowText().equals(playerName));
            if (!windows.isEmpty()) {
                player = new JNAPotPlayer(windows.get(0));
                if (player.getPlayStatus() == PlayStatus.Undefined) {
                    Thread.sleep(160);
                    continue;
                }

                Instant startTime = Instant.now();
                Instant startTime2 = Instant.now();
                boolean scrobbled = false;
                String currSong = null;
                TrackParser.ParsedTrack currentParsed = null;
                long lastCt = 0;
                PlayStatus lastPs = null;

                while (User32.INSTANCE.IsWindow(player.getWindow().getHwnd())) {
                    String tmp = WindowUtils.getWindowTitle(player.getWindow().getHwnd());
                    if (!tmp.endsWith(end) && !tmp.equals(playerName)) break;

                    PlayStatus ps = player.getPlayStatus();
                    long ct = player.getCurrentTime();

                    // Parse song from window title
                    String songTitle = TrackParser.extractSongTitle(tmp, playerName);

                    // Detect song change
                    if (songTitle != null && !songTitle.equals(currSong)) {
                        scrobbled = false;
                        startTime = Instant.now();
                        startTime2 = Instant.now();
                        currSong = songTitle;
                        currentParsed = TrackParser.parseFromWindowTitle(tmp, playerName);

                        if (currentParsed == null) {
                            updateNowPlaying(currSong + " (can't parse artist)");
                            updateScrobbleProgress("Scrobble: skipped");
                        } else {
                            updateNowPlaying(currentParsed.artist + " - " + currentParsed.track);
                            updateScrobbleProgress("Scrobble: waiting...");

                            if (ps == PlayStatus.Running || ps == PlayStatus.Paused) {
                                try {
                                    Track.updateNowPlaying(currentParsed.artist, currentParsed.track, session);
                                } catch (Exception e) {
                                    System.err.println("Error updating now playing: " + e.getMessage());
                                }
                            }
                        }
                        updateTooltip(truncate("PotPlayer LastFM: " +
                                (currentParsed != null ? currentParsed.artist + " - " + currentParsed.track : currSong), 127));
                    }

                    if (ps == PlayStatus.Running) {
                        // Detect same-song replay: playback position jumped backwards significantly
                        if (ct < 1000 && lastCt > 5000) {
                            startTime = Instant.now();
                            startTime2 = Instant.now();
                            scrobbled = false;
                            updateScrobbleProgress("Scrobble: waiting...");
                        }

                        if (!scrobbled && currentParsed != null) {
                            long elapsedSec = Duration.between(startTime, Instant.now()).getSeconds();
                            if (ScrobbleRule.shouldScrobble(elapsedSec, player.getTotalTime())) {
                                try {
                                    Track.scrobble(currentParsed.artist, currentParsed.track,
                                            (int) (System.currentTimeMillis() / 1000), session);
                                    scrobbled = true;
                                    updateScrobbleProgress("Scrobble: done");
                                    updateLastScrobble(currentParsed.artist + " - " + currentParsed.track);
                                } catch (Exception e) {
                                    System.err.println("Error scrobbling: " + e.getMessage());
                                    updateLastScrobble("FAILED: " + currentParsed.artist + " - " + currentParsed.track);
                                }
                            }
                        }
                    } else if (ps == PlayStatus.Paused) {
                        startTime = startTime.plus(Duration.between(startTime2, Instant.now()));
                        if (lastPs != PlayStatus.Paused && !scrobbled) {
                            updateScrobbleProgress("Scrobble: paused");
                        }
                    }

                    // Update tooltip on play/pause state change
                    if (ps != lastPs && currentParsed != null) {
                        String state = (ps == PlayStatus.Paused) ? " (Paused)" : "";
                        updateTooltip(truncate("PotPlayer LastFM: " +
                                currentParsed.artist + " - " + currentParsed.track + state, 127));
                    }

                    // Resume from pause
                    if (ps == PlayStatus.Running && lastPs == PlayStatus.Paused && !scrobbled) {
                        updateScrobbleProgress("Scrobble: waiting...");
                    }

                    lastPs = ps;
                    lastCt = ct;
                    startTime2 = Instant.now();
                    Thread.sleep(16);
                }

                // Player window closed
                updateNowPlaying("--");
                updateScrobbleProgress("Scrobble: --");
                updateTooltip("PotPlayer LastFM - Waiting for PotPlayer...");
            }
            Thread.sleep(1600);
        }
    }

    private static Session authenticate(String[] args, SessionStore sessionStore) {
        // Try stored session first
        SessionStore.StoredSession stored = sessionStore.loadSession();
        if (stored != null) {
            return Session.createSession(API_KEY, API_SECRET, stored.sessionKey, stored.username, false);
        }

        // If CLI args provided (legacy support), use mobile auth
        if (args.length >= 4) {
            Session session = Authenticator.getMobileSession(args[0], args[1], args[2], args[3]);
            if (session != null) {
                sessionStore.saveSession(args[0], session.getKey());
            }
            return session;
        }

        // Web auth flow
        try {
            String token = Authenticator.getToken(API_KEY);
            if (token == null) {
                System.err.println("Failed to get auth token from Last.fm");
                return null;
            }

            String authUrl = "https://www.last.fm/api/auth/?api_key=" + API_KEY + "&token=" + token;

            // Open browser
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(authUrl));
            } else {
                JOptionPane.showMessageDialog(null,
                        "Open this URL in your browser to authorize:\n" + authUrl);
            }

            // Wait for user to authorize
            JOptionPane.showMessageDialog(null,
                    "Authorize PotPlayer LastFM in your browser, then click OK.",
                    "PotPlayer LastFM - Authorization",
                    JOptionPane.INFORMATION_MESSAGE);

            Session session = Authenticator.getSession(token, API_KEY, API_SECRET);
            if (session != null) {
                sessionStore.saveSession(session.getUsername(), session.getKey());
            }
            return session;
        } catch (Exception e) {
            System.err.println("Web auth failed: " + e.getMessage());
            return null;
        }
    }

    private static void updateStatus(String text) {
        if (statusItem != null && !text.equals(lastStatusLabel)) {
            statusItem.setLabel(text);
            lastStatusLabel = text;
        }
    }

    private static void updateNowPlaying(String text) {
        String label = "Now Playing: " + text;
        if (nowPlayingItem != null && !label.equals(lastNowPlayingLabel)) {
            nowPlayingItem.setLabel(label);
            lastNowPlayingLabel = label;
        }
    }

    private static void updateScrobbleProgress(String text) {
        if (scrobbleProgressItem != null && !text.equals(lastScrobbleLabel)) {
            scrobbleProgressItem.setLabel(text);
            lastScrobbleLabel = text;
        }
    }

    private static void updateLastScrobble(String text) {
        if (lastScrobbleItem != null) lastScrobbleItem.setLabel("Last Scrobble: " + text);
    }

    private static void updateTooltip(String text) {
        if (trayIcon != null && !text.equals(lastTooltipText)) {
            trayIcon.setToolTip(text);
            lastTooltipText = text;
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 3) + "...";
    }
}
