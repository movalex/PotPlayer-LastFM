package me.ayunami2000.potplayerlastfm;

import java.util.prefs.Preferences;

public class SessionStore {

    private static final String PREF_SESSION_KEY = "sessionKey";
    private static final String PREF_USERNAME = "username";

    private final Preferences prefs;

    public SessionStore() {
        this.prefs = Preferences.userRoot().node("me/ayunami2000/potplayerlastfm");
    }

    // Package-private constructor for testing
    SessionStore(Preferences prefs) {
        this.prefs = prefs;
    }

    public static class StoredSession {
        public final String username;
        public final String sessionKey;

        public StoredSession(String username, String sessionKey) {
            this.username = username;
            this.sessionKey = sessionKey;
        }
    }

    /**
     * Save a session key and username to persistent storage.
     */
    public void saveSession(String username, String sessionKey) {
        prefs.put(PREF_USERNAME, username);
        prefs.put(PREF_SESSION_KEY, sessionKey);
    }

    /**
     * Load a previously saved session.
     * @return StoredSession if one exists, null otherwise
     */
    public StoredSession loadSession() {
        String key = prefs.get(PREF_SESSION_KEY, null);
        String user = prefs.get(PREF_USERNAME, null);
        if (key == null || user == null) return null;
        return new StoredSession(user, key);
    }

    /**
     * Clear the stored session (logout).
     */
    public void clear() {
        prefs.remove(PREF_SESSION_KEY);
        prefs.remove(PREF_USERNAME);
    }
}
