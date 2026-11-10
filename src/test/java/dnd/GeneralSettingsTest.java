package dnd;

import dnd.map.world.WorldTravel;
import dnd.storage.ProjectBackups;
import dnd.storage.ProjectStore;
import dnd.ui.AppSettings;
import dnd.ui.GeneralSettings;
import dnd.ui.ToolState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GeneralSettingsTest {
    @BeforeEach
    void freshSettings(@TempDir Path tmp) {
        AppSettings.useFile(tmp.resolve("settings.properties"));
    }

    @AfterEach
    void back() {
        AppSettings.useFile(null);
    }

    @Test
    void everyBehaviourSettingHasItsOldDefaultAndSafeLimits() {
        assertEquals(1000, GeneralSettings.autosaveMs());
        assertEquals(200, GeneralSettings.undoDepth(200));
        assertEquals(100, GeneralSettings.undoDepth(100), "each editor keeps its own usual depth");
        assertEquals(450, GeneralSettings.hoverDelayMs(450));
        assertTrue(GeneralSettings.confirmDelete());
        assertFalse(GeneralSettings.reopenLast());
        assertTrue(GeneralSettings.updateCheck());
        assertTrue(GeneralSettings.backupOnClose());
        assertEquals(5, GeneralSettings.backupKeep());

        AppSettings.setInt(GeneralSettings.AUTOSAVE_MS, 3000);
        AppSettings.setInt(GeneralSettings.UNDO_DEPTH, 50);
        AppSettings.setInt(GeneralSettings.HOVER_DELAY, 800);
        AppSettings.setInt(GeneralSettings.BACKUP_KEEP, 12);
        AppSettings.setBoolean(GeneralSettings.CONFIRM_DELETE, false);
        assertEquals(3000, GeneralSettings.autosaveMs());
        assertEquals(50, GeneralSettings.undoDepth(200));
        assertEquals(50, GeneralSettings.undoDepth(100), "one number for every editor once chosen");
        assertEquals(800, GeneralSettings.hoverDelayMs(450));
        assertEquals(12, GeneralSettings.backupKeep());
        assertFalse(GeneralSettings.confirmDelete());

        AppSettings.setInt(GeneralSettings.AUTOSAVE_MS, 1);
        assertEquals(200, GeneralSettings.autosaveMs(), "never so fast that it saves on every key");
        AppSettings.setInt(GeneralSettings.AUTOSAVE_MS, 9_999_999);
        assertEquals(60_000, GeneralSettings.autosaveMs());
        AppSettings.setInt(GeneralSettings.UNDO_DEPTH, 5);
        assertEquals(10, GeneralSettings.undoDepth(200));
        AppSettings.setInt(GeneralSettings.BACKUP_KEEP, 0);
        assertEquals(1, GeneralSettings.backupKeep(), "at least one backup is kept");
        AppSettings.setInt(GeneralSettings.BACKUP_KEEP, 5000);
        assertEquals(50, GeneralSettings.backupKeep());
    }

    @Test
    void theNumberOfBackupsKeptFollowsTheSetting(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        AppSettings.setInt(GeneralSettings.BACKUP_KEEP, 2);
        for (int i = 0; i < 5; i++) ProjectBackups.backup(store, true);
        assertEquals(2, ProjectBackups.list(store).size());
        AppSettings.setInt(GeneralSettings.BACKUP_KEEP, 4);
        for (int i = 0; i < 6; i++) ProjectBackups.backup(store, true);
        assertEquals(4, ProjectBackups.list(store).size());
    }

    @Test
    void distancesCanBeShownInKilometresAndTravelDaysFollowTheHoursSetting() {
        assertEquals("12 mi", WorldTravel.distance(12));
        assertEquals("0.4 mi", WorldTravel.distance(0.4));
        assertEquals("24 mi/day", WorldTravel.paceText(24));
        AppSettings.setString("units.distance", "km");
        assertEquals("19 km", WorldTravel.distance(12));
        assertEquals("0.6 km", WorldTravel.distance(0.4));
        assertEquals("39 km/day", WorldTravel.paceText(24));
        assertEquals(1609.344, WorldTravel.shown(1000), 1e-9);
        double nice = WorldTravel.niceLength(500); // miles; a round number of kilometres
        assertEquals(0, (nice * WorldTravel.KM_PER_MILE) % 100, 1e-6, "a round number of km: " + nice * WorldTravel.KM_PER_MILE);
        assertTrue(nice * WorldTravel.KM_PER_MILE <= 500 * WorldTravel.KM_PER_MILE + 1e-9);
        AppSettings.remove("units.distance");
        assertEquals(500, WorldTravel.niceLength(500), 1e-9);

        // 30 miles at 24 a day is 1.25 days: 10 hours of an 8-hour day
        assertEquals("1 day 2 hr", WorldTravel.duration(30, 24));
        AppSettings.setDouble("units.hoursPerDay", 12);
        assertEquals("1 day 3 hr", WorldTravel.duration(30, 24), "with 12-hour days the same trip is 15 hours: a day and 3 hours");
        assertEquals("under an hour", WorldTravel.duration(0.5, 24));
        AppSettings.setDouble("units.hoursPerDay", 99);
        assertEquals(24, WorldTravel.hoursPerDay(), 1e-9);
    }

    @Test
    void newWhiteboardTextStartsAsChosen() {
        ToolState def = new ToolState();
        assertEquals(22, def.fontSize.get());
        assertEquals("0x222222ff", def.color.get().toString());
        AppSettings.setInt("new.board.fontSize", 30);
        AppSettings.setString("new.board.color", "#112233");
        AppSettings.setString("new.board.fill", "nonsense");
        ToolState chosen = new ToolState();
        assertEquals(30, chosen.fontSize.get());
        assertEquals("0x112233ff", chosen.color.get().toString());
        assertEquals("0xfff2a8ff", chosen.fill.get().toString(), "a damaged colour means the usual one");
        AppSettings.setInt("new.board.fontSize", 9999);
        assertEquals(120, new ToolState().fontSize.get());
    }
}
