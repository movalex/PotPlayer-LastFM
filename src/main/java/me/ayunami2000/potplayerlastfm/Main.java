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
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.List;

import javax.swing.*;

public class Main {
    private static MenuItem statusItem;
    private static MenuItem nowPlayingItem;
    private static MenuItem scrobbleProgressItem;
    private static MenuItem lastScrobbleItem;
    private static TrayIcon trayIcon;

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 4) {
            if (System.console() == null) {
                args = new String[4];
                args[0] = JOptionPane.showInputDialog("Please enter username");
                if (args[0] == null) return;
                args[1] = JOptionPane.showInputDialog("Please enter password (or md5 of password)");
                if (args[1] == null) return;
                args[2] = JOptionPane.showInputDialog("Please enter api key");
                if (args[2] == null) return;
                args[3] = JOptionPane.showInputDialog("Please enter secret");
                if (args[3] == null) return;
            } else {
                System.err.println("Required arguments: username, password (or md5 of password), api key, secret\nRun with javaw (or double click the jar file) for GUI-based entry");
                return;
            }
        }

        if (SystemTray.isSupported()) {
            final PopupMenu popup = new PopupMenu();
            trayIcon = new TrayIcon(ImageIO.read(Objects.requireNonNull(Main.class.getResource("/icon.png"))));
            final SystemTray tray = SystemTray.getSystemTray();

            statusItem = new MenuItem("Status: Authenticating...");
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

            MenuItem exitItem = new MenuItem("Exit PotPlayer LastFM");
            exitItem.addActionListener(e -> System.exit(0));
            popup.add(exitItem);

            trayIcon.setPopupMenu(popup);
            trayIcon.setToolTip("PotPlayer LastFM - Authenticating...");

            try {
                tray.add(trayIcon);
            } catch (AWTException e) {
                System.err.println("TrayIcon could not be added.");
            }
        } else {
            System.err.println("SystemTray is not supported");
        }

        String name = "PotPlayer";
        String end = " - " + name;
        Caller.getInstance().setUserAgent("PotPlayer-LastFM");

        PotPlayer player;
        Session session = Authenticator.getMobileSession(args[0], args[1], args[2], args[3]);
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

        String username = args[0];
        updateStatus("Status: Connected as " + username);
        updateTooltip("PotPlayer LastFM - Waiting for PotPlayer...");

        while (true) {
            List<Window> windows = JNAPotPlayerHelper.getAllPlayerWindows(window -> window.getWindowText().endsWith(end) || window.getWindowText().equals(name));
            if (!windows.isEmpty()) {
                player = new JNAPotPlayer(windows.get(0));
                if (player.getPlayStatus() == PlayStatus.Undefined) {
                    Thread.sleep(160);
                    continue;
                }
                String t;
                PlayStatus ps;
                long ct;
                Instant startTime = Instant.now();
                Instant startTime2 = Instant.now();
                boolean scrobbled = false;
                String currSong = null;
                boolean currSongParseable = false;
                String currentArtist = "";
                String currentTrack = "";
                long lastUiUpdate = 0;
                while (User32.INSTANCE.IsWindow(player.getWindow().getHwnd())) {
                    String tmp = WindowUtils.getWindowTitle(player.getWindow().getHwnd());
                    if (!tmp.endsWith(end) && !tmp.equals(name)) break;
                    PlayStatus tmpps = player.getPlayStatus();
					ct = player.getCurrentTime();
                    t = tmp;
                    ps = tmpps;
                    String song = t;
                    if (song.endsWith(end)) song = song.substring(0, song.lastIndexOf(end));
                    if (song.matches("\\.[a-zA-Z0-9]+$")) {
                        song = song.substring(0, song.lastIndexOf('.'));
                        song = song.replace('_', ' ');
                    } else {
                        song = song.replaceFirst(" \\([0-9-]+\\)$", "");
                    }
                    if (!song.equals(currSong)) {
                        scrobbled = false;
                        startTime = Instant.now();
                        startTime2 = Instant.now();
                        currSong = song;

                        int sep = currSong.indexOf(" - ");
                        if (sep < 0) {
                            currSongParseable = false;
                            currentArtist = "";
                            currentTrack = currSong;
                            updateNowPlaying(currSong + " (can't parse artist)");
                            updateScrobbleProgress("Scrobble: skipped (unparseable title)");
                        } else {
                            currSongParseable = true;
                            currentArtist = currSong.substring(0, sep);
                            currentTrack = currSong.substring(sep + 3);
                            updateNowPlaying(currentArtist + " - " + currentTrack);

                            if (ps == PlayStatus.Running || ps == PlayStatus.Paused) {
                                try {
                                    Track.updateNowPlaying(currentArtist, currentTrack, session);
                                } catch (Exception e) {
                                    System.err.println("Error updating now playing: " + e.getMessage());
                                    updateNowPlaying(currentArtist + " - " + currentTrack + " (now playing error)");
                                }
                            }
                        }
                    }
                    if (ps == PlayStatus.Running) {
                        if (ct < 1000) {
                            startTime = Instant.now();
                            scrobbled = false;
                            currSong = null;
                        }
                        if (!scrobbled && currSongParseable) {
                            long thresholdSec = Math.min(3 * 60, player.getTotalTime() / 2000);
                            long elapsedSec = Duration.between(startTime, Instant.now()).getSeconds();
                            if (elapsedSec > thresholdSec) {
                                try {
                                    Track.scrobble(currentArtist, currentTrack, (int) (System.currentTimeMillis() / 1000), session);
                                    scrobbled = true;
                                    updateLastScrobble(currentArtist + " - " + currentTrack);
                                } catch (Exception e) {
                                    System.err.println("Error scrobbling: " + e.getMessage());
                                    updateLastScrobble("FAILED: " + currentArtist + " - " + currentTrack);
                                }
                            }
                        }
                    } else if (ps == PlayStatus.Paused) {
                        startTime = startTime.plus(Duration.between(startTime2, Instant.now()));
                    }
                    startTime2 = Instant.now();

                    // Throttled UI updates (once per second)
                    long now = System.currentTimeMillis();
                    if (now - lastUiUpdate > 1000) {
                        lastUiUpdate = now;
                        if (currSongParseable && !scrobbled) {
                            long thresholdSec = Math.min(3 * 60, player.getTotalTime() / 2000);
                            long elapsedSec = Duration.between(startTime, Instant.now()).getSeconds();
                            String pauseTag = (ps == PlayStatus.Paused) ? " (paused)" : "";
                            updateScrobbleProgress(String.format("Scrobble: %d:%02d / %d:%02d%s",
                                    elapsedSec / 60, elapsedSec % 60, thresholdSec / 60, thresholdSec % 60, pauseTag));
                        } else if (scrobbled) {
                            String pauseTag = (ps == PlayStatus.Paused) ? " (paused)" : "";
                            updateScrobbleProgress("Scrobble: done" + pauseTag);
                        }
                        String tooltipTrack = currSongParseable ? (currentArtist + " - " + currentTrack) : (currSong != null ? currSong : "");
                        String tooltipStatus = (ps == PlayStatus.Paused) ? " (Paused)" : "";
                        updateTooltip(truncate("PotPlayer LastFM: " + tooltipTrack + tooltipStatus, 127));
                    }

                    Thread.sleep(16);
                }
                // Player window closed
                updateNowPlaying("Now Playing: --");
                updateScrobbleProgress("Scrobble: --");
                updateTooltip("PotPlayer LastFM - Waiting for PotPlayer...");
            }
            Thread.sleep(1600);
        }
    }

    private static void updateStatus(String text) {
        if (statusItem != null) statusItem.setLabel(text);
    }

    private static void updateNowPlaying(String text) {
        if (nowPlayingItem != null) nowPlayingItem.setLabel("Now Playing: " + text);
    }

    private static void updateScrobbleProgress(String text) {
        if (scrobbleProgressItem != null) scrobbleProgressItem.setLabel(text);
    }

    private static void updateLastScrobble(String text) {
        if (lastScrobbleItem != null) lastScrobbleItem.setLabel("Last Scrobble: " + text);
    }

    private static void updateTooltip(String text) {
        if (trayIcon != null) trayIcon.setToolTip(text);
    }

    private static String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 3) + "...";
    }
}
