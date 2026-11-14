package dnd;

import dnd.model.Card;
import dnd.model.Quest;
import dnd.model.TreeNode;
import dnd.storage.ProjectArchive;
import dnd.storage.ProjectStore;
import dnd.storage.TrashBin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class TrashBinTest {
    private static Card card(ProjectStore s, TreeNode parent, String name) {
        Card c = new Card();
        c.name = name;
        return s.createCard(parent, c);
    }

    @Test
    void aDeletedItemGoesToTheBinAndCanBeBroughtBackWithItsContents(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "Camp");
        Card keep = card(s, s.project.root, "Keep");
        Card mira = card(s, s.project.root, "Mira");
        mira.hpMax = 77;
        s.markCardDirty(mira);
        TreeNode node = s.project.root.find(mira.id);
        int placeBefore = s.project.root.children.indexOf(node);
        s.deleteNode(node);

        assertNull(s.project.root.find(mira.id));
        assertFalse(Files.exists(s.dir.resolve("cards").resolve(mira.id + ".card")), "it is out of the project's cards");
        assertEquals(1, s.trash().entries().size());
        TrashBin.Entry e = s.trash().entries().get(0);
        assertEquals("Mira", e.node.name);
        assertEquals(1, e.itemCount());
        assertTrue(Files.isRegularFile(s.dir.resolve("trash").resolve("files").resolve("cards").resolve(mira.id + ".card")));

        TreeNode back = s.restoreFromTrash(e.id);
        assertNotNull(back);
        assertTrue(s.trash().isEmpty());
        assertEquals(placeBefore, s.project.root.children.indexOf(s.project.root.find(mira.id)), "back in its old place");
        s.flush();
        Card reopened = ProjectStore.open(tmp, "Camp").card(mira.id);
        assertNotNull(reopened);
        assertEquals(77, reopened.hpMax);
        assertNotNull(s.card(keep.id));
    }

    @Test
    void aDeletedFolderComesBackWithEverythingInIt(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "Camp");
        TreeNode folder = s.createFolder(s.project.root, "Cast");
        Card a = card(s, folder, "A");
        Quest q = new Quest();
        q.name = "Lighthouse";
        s.createQuest(folder, q);
        s.flush();
        s.deleteNode(folder);
        assertEquals(2, s.trash().entries().get(0).itemCount());
        assertNull(s.project.root.find(a.id));
        assertNull(s.project.root.find(q.id));

        assertNotNull(s.restoreFromTrash(s.trash().entries().get(0).id));
        assertEquals(List.of("A", "Lighthouse"), s.project.root.find(folder.id).children.stream().map(n -> n.name).toList());
        assertNotNull(s.card(a.id));
        assertNotNull(s.quest(q.id));
    }

    @Test
    void anItemWhoseFolderWasDeletedToo_ComesBackAtTheTop(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "Camp");
        TreeNode folder = s.createFolder(s.project.root, "Cast");
        Card a = card(s, folder, "A");
        s.deleteNode(s.project.root.find(a.id));
        s.deleteNode(folder);
        TrashBin.Entry cardEntry = s.trash().entries().stream().filter(x -> x.node.name.equals("A")).findFirst().orElseThrow();
        s.restoreFromTrash(cardEntry.id);
        assertTrue(s.project.root.children.stream().anyMatch(n -> n.id.equals(a.id)), "the old folder is gone, so it goes to the top level");
    }

    @Test
    void theBinSurvivesClosingTheProjectAndOldItemsAreErasedForGood(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "Camp");
        Card a = card(s, s.project.root, "Recent");
        Card b = card(s, s.project.root, "Old");
        s.deleteNode(s.project.root.find(a.id));
        s.deleteNode(s.project.root.find(b.id));
        assertEquals(2, ProjectStore.open(tmp, "Camp").trash().entries().size(), "the bin is saved with the project");

        // pretend the second one was deleted 40 days ago
        TrashBin bin = s.trash();
        TrashBin.Entry old = bin.entries().stream().filter(x -> x.node.name.equals("Old")).findFirst().orElseThrow();
        old.deletedAt = System.currentTimeMillis() - 40L * 24 * 60 * 60 * 1000;
        assertEquals(1, bin.purgeOlderThan(TrashBin.KEEP_DAYS, System.currentTimeMillis()));
        assertEquals(List.of("Recent"), bin.entries().stream().map(x -> x.node.name).toList());
        assertFalse(Files.exists(s.dir.resolve("trash").resolve("files").resolve("cards").resolve(b.id + ".card")));
    }

    @Test
    void deletingForeverAndEmptyingTheBinErasesTheFiles(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "Camp");
        Card a = card(s, s.project.root, "A");
        Card b = card(s, s.project.root, "B");
        s.deleteNode(s.project.root.find(a.id));
        s.deleteNode(s.project.root.find(b.id));
        s.trash().deleteForever(s.trash().entries().get(0));
        assertEquals(1, s.trash().entries().size());
        s.trash().empty();
        assertTrue(s.trash().isEmpty());
        assertFalse(Files.exists(s.dir.resolve("trash").resolve("files").resolve("cards").resolve(a.id + ".card")));
        assertFalse(Files.exists(s.dir.resolve("trash").resolve("files").resolve("cards").resolve(b.id + ".card")));
        assertNull(s.restoreFromTrash("nothing"));
    }

    @Test
    void deletedItemsAreLeftOutOfBackupsAndSharedCopies(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "Camp");
        Card a = card(s, s.project.root, "Secret");
        s.deleteNode(s.project.root.find(a.id));
        Path zip = tmp.resolve("out.zip");
        ProjectArchive.export(s.dir, zip);
        try (ZipFile z = new ZipFile(zip.toFile())) {
            z.stream().forEach(e -> assertFalse(e.getName().startsWith("trash"), e.getName()));
        }
    }

    @Test
    void theBinSaysHowLongAgoAndHowLongIsLeft() {
        long now = 1_000_000_000_000L;
        assertEquals("just now", dnd.ui.TrashDialog.ago(now - 20_000, now));
        assertEquals("1 minute ago", dnd.ui.TrashDialog.ago(now - 61_000, now));
        assertEquals("5 minutes ago", dnd.ui.TrashDialog.ago(now - 5 * 60_000, now));
        assertEquals("3 hours ago", dnd.ui.TrashDialog.ago(now - 3 * 3_600_000L, now));
        assertEquals("1 day ago", dnd.ui.TrashDialog.ago(now - 25 * 3_600_000L, now));
        assertEquals("12 days ago", dnd.ui.TrashDialog.ago(now - 12 * 86_400_000L, now));
        assertEquals(30, dnd.ui.TrashDialog.daysLeft(now, now));
        assertEquals(18, dnd.ui.TrashDialog.daysLeft(now - 12 * 86_400_000L, now));
        assertEquals(0, dnd.ui.TrashDialog.daysLeft(now - 99 * 86_400_000L, now));
    }
}
