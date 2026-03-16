package me.ayunami2000.potplayerlastfm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SessionStoreTest {

    private SessionStore store;

    /**
     * In-memory Preferences implementation for testing (no filesystem/registry).
     */
    private static class InMemoryPreferences extends AbstractPreferences {
        private final Map<String, String> data = new HashMap<>();

        InMemoryPreferences() {
            super(null, "");
        }

        @Override protected void putSpi(String key, String value) { data.put(key, value); }
        @Override protected String getSpi(String key) { return data.get(key); }
        @Override protected void removeSpi(String key) { data.remove(key); }
        @Override protected void removeNodeSpi() { data.clear(); }
        @Override protected String[] keysSpi() { return data.keySet().toArray(new String[0]); }
        @Override protected String[] childrenNamesSpi() { return new String[0]; }
        @Override protected AbstractPreferences childSpi(String name) { return new InMemoryPreferences(); }
        @Override protected void syncSpi() {}
        @Override protected void flushSpi() {}
    }

    @BeforeEach
    void setUp() {
        store = new SessionStore(new InMemoryPreferences());
    }

    @Test
    void saveAndLoadRoundtrip() {
        store.saveSession("testuser", "abc123sessionkey");
        var loaded = store.loadSession();
        assertNotNull(loaded);
        assertEquals("testuser", loaded.username);
        assertEquals("abc123sessionkey", loaded.sessionKey);
    }

    @Test
    void loadWhenEmpty() {
        assertNull(store.loadSession());
    }

    @Test
    void clearRemovesSession() {
        store.saveSession("testuser", "abc123");
        store.clear();
        assertNull(store.loadSession());
    }

    @Test
    void overwriteExistingSession() {
        store.saveSession("user1", "key1");
        store.saveSession("user2", "key2");
        var loaded = store.loadSession();
        assertNotNull(loaded);
        assertEquals("user2", loaded.username);
        assertEquals("key2", loaded.sessionKey);
    }
}
