package dnd;

import dnd.ui.AppSettings;
import dnd.ui.SettingsMigration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

class AppSettingsTest {
    private Path file;

    @BeforeEach
    void useTempFile(@TempDir Path tmp) {
        file = tmp.resolve("settings.properties");
        AppSettings.useFile(file);
    }

    @AfterEach
    void back() {
        AppSettings.useFile(null);
    }

    @Test
    void valuesComeBackTypedAndSurviveBeingReadAgain() {
        AppSettings.setBoolean("a.flag", true);
        AppSettings.setInt("a.count", 7);
        AppSettings.setDouble("a.scale", 1.25);
        AppSettings.setString("a.text", "hello world");
        assertTrue(AppSettings.getBoolean("a.flag", false));
        assertEquals(7, AppSettings.getInt("a.count", 0));
        assertEquals(1.25, AppSettings.getDouble("a.scale", 0), 1e-9);
        assertEquals("hello world", AppSettings.getString("a.text", ""));
        AppSettings.useFile(file); // forget the cache: read it back from disk
        assertTrue(AppSettings.getBoolean("a.flag", false));
        assertEquals(7, AppSettings.getInt("a.count", 0));
        assertEquals(1.25, AppSettings.getDouble("a.scale", 0), 1e-9);
        assertEquals("hello world", AppSettings.getString("a.text", ""));
    }

    @Test
    void missingOrDamagedValuesGiveTheDefault() throws Exception {
        assertFalse(AppSettings.has("nothing"));
        assertEquals(5, AppSettings.getInt("nothing", 5));
        assertTrue(AppSettings.getBoolean("nothing", true));
        Files.writeString(file, "bad.int=abc\nbad.bool=maybe\nbad.double=NaN\nok=1\n");
        AppSettings.useFile(file);
        assertEquals(9, AppSettings.getInt("bad.int", 9));
        assertTrue(AppSettings.getBoolean("bad.bool", true));
        assertEquals(2.5, AppSettings.getDouble("bad.double", 2.5), 1e-9);
        assertEquals(1, AppSettings.getInt("ok", 0));
        Files.write(file, new byte[]{'\\', 'u', 'x'}); // a damaged file is just empty
        AppSettings.useFile(file);
        assertEquals(3, AppSettings.getInt("ok", 3));
    }

    @Test
    void existingKeysFromOlderVersionsStillWork() throws Exception {
        Files.writeString(file, "theme=dark\nnameHints=false\nlinkPreviews=true\nworldSnap=false\n");
        AppSettings.useFile(file);
        assertEquals("dark", AppSettings.getString("theme", "light"));
        assertFalse(AppSettings.getBoolean("nameHints", true));
        assertTrue(AppSettings.getBoolean("linkPreviews", false));
        assertFalse(AppSettings.getBoolean("worldSnap", true));
    }

    @Test
    void listenersHearChangesButNotRepeatsOfTheSameValue() {
        List<String> heard = new ArrayList<>();
        Runnable stop = AppSettings.addListener((k, v) -> heard.add(k + "=" + v));
        AppSettings.setInt("x", 1);
        AppSettings.setInt("x", 1);
        AppSettings.setInt("x", 2);
        AppSettings.remove("x");
        AppSettings.remove("x");
        assertEquals(List.of("x=1", "x=2", "x=null"), heard);
        stop.run();
        AppSettings.setInt("y", 1);
        assertEquals(3, heard.size());
    }

    @Test
    void aFailingListenerDoesNotStopTheChange() {
        Runnable stop = AppSettings.addListener((k, v) -> {
            throw new IllegalStateException("boom");
        });
        AppSettings.setString("k", "v");
        assertEquals("v", AppSettings.getString("k", ""));
        stop.run();
    }

    @Test
    void resettingForgetsEverythingExceptTheAppsOwnNotes() {
        AppSettings.setString("key.item.link", "Ctrl+K");
        AppSettings.setString("key.item.undo", "Ctrl+Z");
        AppSettings.setString("scale", "1.5");
        AppSettings.setBoolean("internal.migratedPrefs", true);
        assertEquals(List.of("key.item.link", "key.item.undo"), AppSettings.keys("key."));
        AppSettings.resetPrefix("key.");
        assertTrue(AppSettings.keys("key.").isEmpty());
        assertTrue(AppSettings.has("scale"));
        AppSettings.resetAll();
        assertFalse(AppSettings.has("scale"));
        assertTrue(AppSettings.getBoolean("internal.migratedPrefs", false), "the migration note stays so it does not run twice");
    }

    @Test
    void settingsCanBeSavedToAFileAndLoadedOnAnotherComputer(@TempDir Path other) throws Exception {
        AppSettings.setString("theme", "dark");
        AppSettings.setDouble("ui.scale", 1.5);
        AppSettings.setBoolean("internal.migratedPrefs", true);
        Path copy = other.resolve("my-settings.properties");
        AppSettings.exportTo(copy);
        String text = Files.readString(copy);
        assertTrue(text.contains("ui.scale=1.5"));
        assertFalse(text.contains("internal."), "the app's own notes are not exported");

        AppSettings.setString("theme", "light");
        AppSettings.setString("leftover", "x");
        int n = AppSettings.importFrom(copy);
        assertEquals(2, n);
        assertEquals("dark", AppSettings.getString("theme", ""));
        assertEquals(1.5, AppSettings.getDouble("ui.scale", 0), 1e-9);
        assertFalse(AppSettings.has("leftover"), "importing replaces the old settings");

        Path empty = other.resolve("empty.properties");
        Files.writeString(empty, "");
        assertThrows(java.io.IOException.class, () -> AppSettings.importFrom(empty));
        assertEquals("dark", AppSettings.getString("theme", ""), "a bad file changes nothing");
    }

    @Test
    void oldRememberedChoicesAreCopiedOnceFromThePreferences() throws Exception {
        String name = "dnd-test-" + System.nanoTime();
        Preferences root = Preferences.userRoot().node(name);
        try {
            root.put("dismissedUpdate", "1.2.3");
            root.node("worldExport").putBoolean("players", true);
            root.node("worldExport").putInt("width", 4096);
            root.node("worldExport").put("dir", "C:\\maps");
            root.node("battleExport").putInt("width", 1024);
            root.node("itemExport").put("dir", "C:\\notes");
            SettingsMigration.run(root);
            assertEquals("1.2.3", AppSettings.getString("update.dismissed", ""));
            assertTrue(AppSettings.getBoolean("export.world.players", false));
            assertEquals(4096, AppSettings.getInt("export.world.width", 0));
            assertEquals("C:\\maps", AppSettings.getString("export.world.dir", ""));
            assertEquals(1024, AppSettings.getInt("export.battle.width", 0));
            assertEquals("C:\\notes", AppSettings.getString("export.item.dir", ""));
            assertTrue(AppSettings.getBoolean("internal.migratedPrefs", false));

            AppSettings.setInt("export.world.width", 2048); // the user changes it afterwards
            root.node("worldExport").putInt("width", 8192);
            SettingsMigration.run(root);
            assertEquals(2048, AppSettings.getInt("export.world.width", 0), "it is not copied a second time");
            // nothing at all in the old store is fine
            AppSettings.resetAll();
            AppSettings.remove("internal.migratedPrefs");
            SettingsMigration.run(Preferences.userRoot().node(name + "-none"));
            assertTrue(AppSettings.getBoolean("internal.migratedPrefs", false));
        } finally {
            root.removeNode();
            Preferences.userRoot().node(name + "-none").removeNode();
        }
    }
}
