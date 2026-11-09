package dnd;

import dnd.model.Card;
import dnd.model.MagicItem;
import dnd.model.Quest;
import dnd.storage.ProjectArchive;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ProjectArchiveTest {
    private static ProjectStore sampleProject(Path base) throws Exception {
        ProjectStore store = ProjectStore.create(base, "Lost Mine");
        Card c = new Card();
        c.name = "Mira";
        store.createCard(store.project.root, c);
        store.createBattleMap(store.project.root, "Crypt", dnd.model.BattleMap.GridType.SQUARE, 10, 10, 50);
        MagicItem m = new MagicItem();
        m.name = "Ring of Ash";
        store.createMagicItem(store.project.root, m);
        Quest q = new Quest();
        q.name = "Find the keeper";
        store.createQuest(store.project.root, q);
        Files.writeString(store.imagePath("map.png"), "not really a png");
        store.flush();
        return store;
    }

    private static void zipWith(Path zip, String... namesAndText) throws Exception {
        try (OutputStream out = Files.newOutputStream(zip); ZipOutputStream z = new ZipOutputStream(out)) {
            for (int i = 0; i < namesAndText.length; i += 2) {
                z.putNextEntry(new ZipEntry(namesAndText[i]));
                z.write(namesAndText[i + 1].getBytes());
                z.closeEntry();
            }
        }
    }

    @Test
    void anExportedProjectComesBackWithEverythingInIt(@TempDir Path tmp) throws Exception {
        Path base = tmp.resolve("projects");
        ProjectStore store = sampleProject(base);
        Files.writeString(store.dir.resolve("cards").resolve("half-written.json.tmp"), "junk");
        Path zip = tmp.resolve("backup.zip");
        int files = ProjectArchive.export(store.dir, zip);
        assertTrue(files >= 6, "files: " + files);
        assertTrue(Files.size(zip) > 0);
        assertFalse(Files.exists(tmp.resolve("backup.zip.tmp")));

        Path other = tmp.resolve("other-computer");
        ProjectArchive.Imported in = ProjectArchive.importZip(zip, other);
        assertEquals("Lost Mine", in.folderName());
        assertEquals(files, in.files());

        ProjectStore back = ProjectStore.open(other, in.folderName());
        assertEquals(1, back.allCardIds().size());
        assertEquals("Mira", back.card(back.allCardIds().get(0)).name);
        assertEquals(1, back.allMagicItemIds().size());
        assertEquals(1, back.allQuestIds().size());
        assertEquals(1, back.allMapIds().size());
        assertEquals("not really a png", Files.readString(back.imagePath("map.png")));
        assertFalse(Files.exists(back.dir.resolve("cards").resolve("half-written.json.tmp")));
    }

    @Test
    void importingTwiceMakesANewFolderAndNeverOverwrites(@TempDir Path tmp) throws Exception {
        ProjectStore store = sampleProject(tmp.resolve("projects"));
        Path zip = tmp.resolve("b.zip");
        ProjectArchive.export(store.dir, zip);
        Path base = tmp.resolve("base");
        String first = ProjectArchive.importZip(zip, base).folderName();
        Files.writeString(base.resolve(first).resolve("marker.txt"), "mine");
        String second = ProjectArchive.importZip(zip, base).folderName();
        assertEquals("Lost Mine", first);
        assertEquals("Lost Mine (2)", second);
        assertEquals("mine", Files.readString(base.resolve(first).resolve("marker.txt")));
        assertEquals(List.of("Lost Mine", "Lost Mine (2)"), ProjectStore.listProjects(base));
    }

    @Test
    void anArchiveWithTheProjectInsideOneFolderIsAccepted(@TempDir Path tmp) throws Exception {
        Path zip = tmp.resolve("zipped-folder.zip");
        zipWith(zip, "My Camp/project.json", "{\"name\":\"My Camp\",\"root\":{\"id\":\"r\",\"name\":\"My Camp\",\"folder\":true}}",
                "My Camp/cards/x.card", "{}");
        ProjectArchive.Imported in = ProjectArchive.importZip(zip, tmp.resolve("base"));
        assertEquals("My Camp", in.folderName());
        assertTrue(Files.isRegularFile(tmp.resolve("base").resolve("My Camp").resolve("cards").resolve("x.card")));
        assertTrue(Files.isRegularFile(tmp.resolve("base").resolve("My Camp").resolve("project.json")));
    }

    @Test
    void pathsThatEscapeTheNewFolderAreRefusedAndNothingIsLeftBehind(@TempDir Path tmp) throws Exception {
        Path zip = tmp.resolve("evil.zip");
        zipWith(zip, "project.json", "{\"name\":\"Evil\"}", "../../outside.txt", "gotcha");
        Path base = tmp.resolve("base");
        assertThrows(java.io.IOException.class, () -> ProjectArchive.importZip(zip, base));
        assertFalse(Files.exists(tmp.resolve("outside.txt")));
        assertFalse(Files.exists(tmp.getParent().resolve("outside.txt")));
        assertTrue(ProjectStore.listProjects(base).isEmpty());
        try (var s = Files.list(base)) {
            assertEquals(0, s.count(), "the half-made folder was removed");
        }
    }

    @Test
    void somethingThatIsNotABackupAndAnOversizedOneAreRefused(@TempDir Path tmp) throws Exception {
        Path notBackup = tmp.resolve("random.zip");
        zipWith(notBackup, "readme.txt", "hello");
        java.io.IOException e = assertThrows(java.io.IOException.class, () -> ProjectArchive.importZip(notBackup, tmp.resolve("base")));
        assertTrue(e.getMessage().contains("project.json"));

        Path big = tmp.resolve("big.zip");
        zipWith(big, "project.json", "{\"name\":\"Big\"}", "images/huge.bin", "x".repeat(5000));
        assertThrows(java.io.IOException.class, () -> ProjectArchive.importZip(big, tmp.resolve("base2"), 1000));
        try (var s = Files.list(tmp.resolve("base2"))) {
            assertEquals(0, s.count());
        }
    }

    @Test
    void exportingAFolderThatIsNotAProjectFails(@TempDir Path tmp) {
        assertThrows(java.io.IOException.class, () -> ProjectArchive.export(tmp, tmp.resolve("x.zip")));
    }
}
