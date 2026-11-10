package dnd;

import dnd.ui.AppSettings;
import dnd.ui.IconMenuModel;
import dnd.ui.Panels;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PanelsTest {
    @BeforeEach
    void useTempFile(@TempDir Path tmp) {
        AppSettings.useFile(tmp.resolve("settings.properties"));
    }

    @AfterEach
    void back() {
        AppSettings.useFile(null);
    }

    @Test
    void savedPositionsAreKeptInsideAUsableRange() {
        assertEquals(0.3, Panels.saved("a", 0.3), 1e-9, "nothing saved gives the standard");
        AppSettings.setDouble("layout.a", 0.42);
        assertEquals(0.42, Panels.saved("a", 0.3), 1e-9);
        AppSettings.setDouble("layout.a", 0.0);
        assertEquals(0.3, Panels.saved("a", 0.3), 1e-9, "zero would hide a panel");
        AppSettings.setDouble("layout.a", 7);
        assertEquals(0.95, Panels.saved("a", 0.3), 1e-9);
        AppSettings.setString("layout.a", "wide");
        assertEquals(0.3, Panels.saved("a", 0.3), 1e-9, "a damaged value gives the standard");
        assertEquals(0.3, Panels.clampFraction(Double.NaN, 0.3), 1e-9);
    }

    @Test
    void resettingForgetsPanelSizesButNotOtherSettings() {
        AppSettings.setDouble("layout.a", 0.4);
        AppSettings.setDouble("layout.b", 0.6);
        AppSettings.setString("theme", "dark");
        Panels.resetAll();
        assertFalse(AppSettings.has("layout.a"));
        assertFalse(AppSettings.has("layout.b"));
        assertEquals("dark", AppSettings.getString("theme", ""));
    }

    @Test
    void dropdownRowsShowTheirKeyOnlyWhenTheyHaveOne() {
        assertEquals("Rules", IconMenuModel.rowText("Rules", null));
        assertEquals("Rules", IconMenuModel.rowText("Rules", ""));
        assertEquals("Rules  (Ctrl+R)", IconMenuModel.rowText("Rules", "Ctrl+R"));
        assertTrue(IconMenuModel.rowTextFor("Loot", "global.loot").startsWith("Loot  ("));
        assertEquals("Anything", IconMenuModel.rowTextFor("Anything", null));
    }
}
