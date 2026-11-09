package dnd;

import dnd.model.Quest;
import dnd.model.TreeNode;
import dnd.search.SearchService;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuestTest {
    private static Quest sample() {
        Quest q = new Quest();
        q.name = "The Missing Lighthouse Keeper";
        q.status = Quest.Status.ACTIVE;
        q.giver = "Mira the innkeeper";
        q.reward = "200 gp and a favour";
        q.summary = "Old Tobias has not lit the lamp for three nights.";
        q.steps.add(new Quest.Step("Search the lighthouse cellar", true));
        q.steps.add(new Quest.Step("Find the smuggler's ledger", false));
        q.steps.add(new Quest.Step("Confront Captain Vane", false));
        q.notes = "Tobias is hiding in the sea caves.";
        return q;
    }

    @Test
    void aQuestIsSavedAndLoadedAgain(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        Quest made = sample();
        made.related.add("some-card-id");
        store.createQuest(store.project.root, made);
        store.flush();
        assertTrue(Files.isRegularFile(store.dir.resolve("quests").resolve(made.id + ".quest")));

        Quest back = ProjectStore.open(tmp, "Camp").quest(made.id);
        assertNotNull(back);
        assertEquals(Quest.Status.ACTIVE, back.status);
        assertEquals(3, back.steps.size());
        assertTrue(back.steps.get(0).done);
        assertEquals(List.of("some-card-id"), back.related);
        assertEquals("Mira the innkeeper", back.giver);
    }

    @Test
    void progressCountsTickedSteps() {
        Quest q = sample();
        assertEquals(1, q.stepsDone());
        assertEquals("1 of 3 steps", q.progressText());
        q.steps.clear();
        assertEquals("", q.progressText());
        q.steps.add(new Quest.Step("Only one", false));
        assertEquals("0 of 1 step", q.progressText());
        assertFalse(q.isFinished());
        q.status = Quest.Status.FAILED;
        assertTrue(q.isFinished());
    }

    @Test
    void questsAreItemsInTheTreeAndCanBeRenamedAndDeleted(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        Quest made = store.createQuest(store.project.root, sample());
        TreeNode node = store.project.root.find(made.id);
        assertTrue(node.isQuest());
        assertFalse(node.isBoard());
        assertTrue(store.itemExists(made.id));
        assertEquals(List.of(made.id), store.allQuestIds());

        store.renameNode(node, "The Lamp Stays Dark");
        assertEquals("The Lamp Stays Dark", store.quest(made.id).name);
        store.flush();
        store.deleteNode(node);
        assertNull(store.quest(made.id));
        assertFalse(Files.exists(store.dir.resolve("quests").resolve(made.id + ".quest")));
        assertTrue(store.allQuestIds().isEmpty());
    }

    @Test
    void searchFindsQuestsByNameStepGiverAndNotes(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        store.createQuest(store.project.root, sample());
        assertEquals("Quest", SearchService.search(store, "lighthouse keeper", 10).get(0).snippet);
        assertTrue(SearchService.search(store, "smuggler's ledger", 10).get(0).snippet.startsWith("Quest - step:"));
        assertTrue(SearchService.search(store, "mira the", 10).stream().anyMatch(r -> r.snippet.contains("given by")));
        assertTrue(SearchService.search(store, "sea caves", 10).get(0).snippet.contains("notes:"));
    }

    @Test
    void theQuestBoardFilterMatchesAnyField() {
        Quest q = sample();
        assertTrue(dnd.ui.QuestBoardDialog.matches(q, ""));
        assertTrue(dnd.ui.QuestBoardDialog.matches(q, "captain vane"));
        assertTrue(dnd.ui.QuestBoardDialog.matches(q, "200 gp"));
        assertTrue(dnd.ui.QuestBoardDialog.matches(q, "innkeeper"));
        assertFalse(dnd.ui.QuestBoardDialog.matches(q, "dragon"));
    }

    @Test
    void everyStatusHasADistinctColour() {
        java.util.Set<String> colours = new java.util.HashSet<>();
        for (Quest.Status s : Quest.Status.values()) colours.add(dnd.ui.QuestCardView.statusColor(s));
        assertEquals(Quest.Status.values().length, colours.size());
    }

    @Test
    void theGiverIsAChosenCharactersCurrentNameOrTheTypedName(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        dnd.model.Card mira = new dnd.model.Card();
        mira.name = "Mira";
        store.createCard(store.project.root, mira);
        Quest q = sample();
        q.giverId = mira.id;
        q.giver = "Mira";
        store.createQuest(store.project.root, q);

        assertEquals("Mira", q.giverName(store::itemNameOrNull));
        store.renameNode(store.project.root.find(mira.id), "Mira Stonehelm");
        assertEquals("Mira Stonehelm", q.giverName(store::itemNameOrNull), "a renamed character updates");
        assertTrue(SearchService.search(store, "stonehelm", 10).stream().anyMatch(r -> r.snippet.contains("given by: Mira Stonehelm")));
        assertTrue(dnd.ui.QuestBoardDialog.matches(q, "stonehelm", store::itemNameOrNull));
        assertFalse(dnd.ui.QuestBoardDialog.matches(q, "stonehelm"), "without a name lookup only the typed name matches");

        store.deleteNode(store.project.root.find(mira.id));
        assertEquals("Mira", q.giverName(store::itemNameOrNull), "falls back to the last known name");

        Quest typed = sample();
        assertNull(typed.giverId);
        assertEquals("Mira the innkeeper", typed.giverName(store::itemNameOrNull));
        assertEquals("", new Quest().giverName(id -> null));

        store.flush();
        Quest back = ProjectStore.open(tmp, "Camp").quest(q.id);
        assertEquals(mira.id, back.giverId);
        assertEquals(mira.id, back.copy().giverId);
    }
}
