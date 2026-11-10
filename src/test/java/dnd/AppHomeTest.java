package dnd;

import dnd.util.AppHome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AppHomeTest {
    @Test
    void anOldFolderIsMovedToTheNewNameWithEverythingInIt(@TempDir Path home) throws Exception {
        Path old = home.resolve(AppHome.OLD_NAME);
        Files.createDirectories(old.resolve("My Campaign"));
        Files.writeString(old.resolve("My Campaign").resolve("project.json"), "{}");
        Files.writeString(old.resolve("settings.properties"), "dark=true");
        Path dir = AppHome.resolve(home);
        assertEquals(home.resolve(AppHome.NAME), dir);
        assertFalse(Files.exists(old), "the old folder is gone");
        assertEquals("{}", Files.readString(dir.resolve("My Campaign").resolve("project.json")));
        assertEquals("dark=true", Files.readString(dir.resolve("settings.properties")));
        assertEquals(dir, AppHome.resolve(home), "asking again changes nothing");
    }

    @Test
    void aNewFolderIsLeftAloneAndNothingIsCreatedWhenThereIsNoOldOne(@TempDir Path home) throws Exception {
        assertEquals(home.resolve(AppHome.NAME), AppHome.resolve(home));
        assertFalse(Files.exists(home.resolve(AppHome.NAME)), "resolving alone creates nothing");
        Files.createDirectories(home.resolve(AppHome.NAME));
        Files.createDirectories(home.resolve(AppHome.OLD_NAME));
        Files.writeString(home.resolve(AppHome.OLD_NAME).resolve("keep.txt"), "x");
        assertEquals(home.resolve(AppHome.NAME), AppHome.resolve(home));
        assertTrue(Files.exists(home.resolve(AppHome.OLD_NAME).resolve("keep.txt")), "when both exist the old one is not touched");
    }
}
