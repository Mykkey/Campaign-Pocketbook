package dnd;

import dnd.export.MarkdownExport;
import dnd.model.Card;
import dnd.model.Organisation;
import dnd.model.TreeNode;
import dnd.search.SearchService;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrganisationTest {
    private static Card card(ProjectStore store, String name, Card.Kind kind) {
        Card c = new Card();
        c.name = name;
        c.kind = kind;
        return store.createCard(store.project.root, c);
    }

    @Test
    void anOrganisationIsSavedWithItsMembersAndLoadedAgain(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        Card mira = card(store, "Mira", Card.Kind.NPC);
        Card dan = card(store, "Dan", Card.Kind.PLAYER);
        Organisation o = new Organisation();
        o.name = "The Silver Hand";
        o.type = "Thieves guild";
        o.base = "Dockside";
        o.goals = "Control the harbour";
        store.createOrganisation(store.project.root, o);
        assertTrue(o.addMember(mira.id, "Leader"));
        assertTrue(o.addMember(dan.id, "Spy"));
        store.markOrganisationDirty(o);
        store.flush();
        assertTrue(Files.isRegularFile(store.dir.resolve("organisations").resolve(o.id + ".organisation")));

        Organisation back = ProjectStore.open(tmp, "Camp").organisation(o.id);
        assertNotNull(back);
        assertEquals("Thieves guild", back.type);
        assertEquals(2, back.members.size());
        assertEquals(mira.id, back.members.get(0).id);
        assertEquals("Leader", back.members.get(0).role);
        assertEquals("2 members", back.sizeText());
    }

    @Test
    void aMemberIsAddedOnceAndNeverTheGroupItself() {
        Organisation o = new Organisation();
        o.id = "org";
        assertFalse(o.addMember("org", ""), "a group cannot contain itself");
        assertFalse(o.addMember("", ""));
        assertFalse(o.addMember(null, ""));
        assertTrue(o.addMember("a", null));
        assertFalse(o.addMember("a", "Again"), "already a member");
        assertEquals("", o.members.get(0).role);
        assertEquals("1 member", o.sizeText());
        assertTrue(o.removeMember("a"));
        assertFalse(o.removeMember("a"));
        assertEquals("No members yet", o.sizeText());
    }

    @Test
    void organisationsAreTreeItemsThatRenameDuplicateAndDelete(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        Card mira = card(store, "Mira", Card.Kind.NPC);
        Organisation o = new Organisation();
        o.name = "Council";
        store.createOrganisation(store.project.root, o);
        o.addMember(mira.id, "Chair");
        TreeNode node = store.project.root.find(o.id);
        assertTrue(node.isOrganisation());
        assertFalse(node.isBoard());
        assertTrue(store.isOrganisation(o.id));
        assertEquals(List.of(o.id), store.allOrganisationIds());

        store.renameNode(node, "High Council");
        assertEquals("High Council", store.organisation(o.id).name);

        Organisation copy = store.duplicateOrganisation(node);
        assertNotEquals(o.id, copy.id);
        assertEquals("High Council (copy)", copy.name);
        assertEquals(1, copy.members.size());
        copy.members.get(0).role = "Changed";
        assertEquals("Chair", o.members.get(0).role, "a copy has its own members");

        store.flush();
        store.deleteNode(node);
        store.flush();
        assertFalse(Files.exists(store.dir.resolve("organisations").resolve(o.id + ".organisation")));
        assertTrue(store.itemExists(mira.id), "deleting the group keeps the cards");
    }

    @Test
    void youCanFindWhichGroupsACardBelongsTo(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        Card mira = card(store, "Mira", Card.Kind.NPC);
        Organisation a = store.createOrganisation(store.project.root, named("Guild"));
        Organisation b = store.createOrganisation(store.project.root, named("Family"));
        a.addMember(mira.id, "");
        b.addMember(a.id, "Rival guild");
        assertEquals(List.of(a.id), store.organisationsOf(mira.id).stream().map(x -> x.id).toList());
        assertEquals(List.of(b.id), store.organisationsOf(a.id).stream().map(x -> x.id).toList());
    }

    private static Organisation named(String name) {
        Organisation o = new Organisation();
        o.name = name;
        return o;
    }

    @Test
    void searchFindsOrganisationsByNameTypeAndMemberRole(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        Card mira = card(store, "Mira", Card.Kind.NPC);
        Organisation o = named("The Silver Hand");
        o.type = "Thieves guild";
        store.createOrganisation(store.project.root, o);
        o.addMember(mira.id, "Fence");

        assertTrue(SearchService.search(store, "silver", 20).stream().anyMatch(r -> r.boardId.equals(o.id) && r.snippet.startsWith("Organisation")));
        assertTrue(SearchService.search(store, "thieves", 20).stream().anyMatch(r -> r.boardId.equals(o.id) && r.snippet.contains("type")));
        assertTrue(SearchService.search(store, "fence", 20).stream().anyMatch(r -> r.boardId.equals(o.id) && r.snippet.contains("role")));
        assertTrue(SearchService.search(store, "mira", 20).stream().anyMatch(r -> r.boardId.equals(o.id) && r.snippet.contains("member")));
    }

    @Test
    void markdownListsTheMembersAndTheirRoles(@TempDir Path tmp) throws Exception {
        ProjectStore store = ProjectStore.create(tmp, "Camp");
        Card mira = card(store, "Mira", Card.Kind.NPC);
        Organisation o = named("The Silver Hand");
        o.type = "Thieves guild";
        o.addMember(mira.id, "Leader");
        o.addMember("gone", "Ghost");
        String md = MarkdownExport.organisation(o, store::itemNameOrNull);
        assertTrue(md.startsWith("# The Silver Hand"));
        assertTrue(md.contains("*Thieves guild*"));
        assertTrue(md.contains("- Mira - Leader"));
        assertTrue(md.contains("- (deleted item) - Ghost"));
    }
}
