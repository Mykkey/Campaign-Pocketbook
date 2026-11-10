package dnd;

import dnd.model.Card;
import dnd.storage.ProjectBackups;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectBackupsTest {
    @org.junit.jupiter.api.BeforeEach
    void ownSettings(@org.junit.jupiter.api.io.TempDir java.nio.file.Path settingsDir) {
        dnd.ui.AppSettings.useFile(settingsDir.resolve("settings.properties")); // never the real settings of whoever runs the tests
    }

    @org.junit.jupiter.api.AfterEach
    void realSettings() {
        dnd.ui.AppSettings.useFile(null);
    }

    @Test
    void closingAfterChangesWritesABackupThatCanBeImportedAndOldOnesArePruned(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        Card c = new Card();
        c.name = "Mira";
        store.createCard(store.project.root, c);

        Path zip = ProjectBackups.backup(store, false);
        assertNotNull(zip);
        assertTrue(Files.isRegularFile(zip));
        assertEquals(tmp.resolve(".backups").resolve("Camp"), zip.getParent());
        assertTrue(ProjectStore.listProjects(tmp).equals(java.util.List.of("Camp")), "the backups folder is not a project");

        String restored = dnd.storage.ProjectArchive.importZip(zip, tmp).folderName();
        assertEquals("Mira", ProjectStore.open(tmp, restored).card(c.id).name);

        for (int i = 0; i < 8; i++) ProjectBackups.backup(store, true);
        assertEquals(ProjectBackups.KEEP, ProjectBackups.list(store).size());
    }

    @Test
    void nothingIsBackedUpWhenNothingWasSaved(@TempDir Path tmp) throws Exception {
        ProjectStore.create(tmp, "Camp");
        ProjectStore reopened = ProjectStore.open(tmp, "Camp");
        assertNull(ProjectBackups.backup(reopened, false));
        assertFalse(Files.exists(tmp.resolve(".backups")));
        assertNotNull(ProjectBackups.beforeDelete(reopened), "but a delete always gets one");
        assertNull(ProjectBackups.beforeDelete(reopened), "and only one every ten minutes");
    }

    @Test
    void aDamagedFileIsReportedAndLeftUntouched(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        String id = store.allBoardIds().get(0);
        Path file = store.dir.resolve("boards").resolve(id + ".json");
        Files.writeString(file, "{ this is not json");
        ProjectStore reopened = ProjectStore.open(tmp, "Camp");

        assertNull(reopened.board(id));
        assertNotNull(reopened.loadError(id));
        assertTrue(reopened.loadError(id).contains(id));
        reopened.markProjectDirty();
        reopened.flush();
        assertEquals("{ this is not json", Files.readString(file), "a later save must not overwrite the damaged file");
    }
}
