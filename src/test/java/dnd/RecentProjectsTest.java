package dnd;

import dnd.ui.AppSettings;
import dnd.ui.RecentProjects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecentProjectsTest {
    @BeforeEach
    void useTempFile(@TempDir Path tmp) {
        AppSettings.useFile(tmp.resolve("settings.properties"));
    }

    @AfterEach
    void back() {
        AppSettings.useFile(null);
    }

    @Test
    void theLatestOpenedCampaignComesFirstAndTheRestKeepAlphabeticalOrder() {
        List<String> names = List.of("Zed", "alpha", "Beta", "Gamma");
        assertEquals(List.of("alpha", "Beta", "Gamma", "Zed"), RecentProjects.sorted(names), "nothing opened yet: alphabetical");
        RecentProjects.opened("Gamma");
        assertEquals("Gamma", RecentProjects.sorted(names).get(0));
        assertEquals(List.of("Gamma", "alpha", "Beta", "Zed"), RecentProjects.sorted(names));
        RecentProjects.opened("Zed");
        assertEquals(List.of("Zed", "Gamma", "alpha", "Beta"), RecentProjects.sorted(names));
    }

    @Test
    void campaignsAreOrderedByWhenTheyWereOpened() {
        AppSettings.setString("internal.opened.A", "100");
        AppSettings.setString("internal.opened.B", "300");
        AppSettings.setString("internal.opened.C", "200");
        assertEquals(List.of("B", "C", "A", "D"), RecentProjects.sorted(List.of("A", "D", "C", "B")));
        AppSettings.setString("internal.opened.D", "garbage");
        assertEquals(0, RecentProjects.lastOpened("D"), "a damaged value counts as never opened");
    }

    @Test
    void aDeletedCampaignIsForgottenAndTheNotesAreNotExportedOrReset() throws Exception {
        RecentProjects.opened("Gone");
        assertTrue(RecentProjects.lastOpened("Gone") > 0);
        AppSettings.resetAll();
        assertTrue(RecentProjects.lastOpened("Gone") > 0, "a settings reset keeps the order of campaigns");
        RecentProjects.forget("Gone");
        assertEquals(0, RecentProjects.lastOpened("Gone"));
    }
}
