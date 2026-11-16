package dnd;

import dnd.export.MarkdownExport;
import dnd.model.Card;
import dnd.model.TreeNode;
import dnd.rules.Pantheon;
import dnd.search.SearchService;
import dnd.storage.ProjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PantheonTest {
    private static Card god(String name, String alignment, String domains, String rank) {
        Card c = new Card();
        c.id = name;
        c.name = name;
        c.kind = Card.Kind.DEITY;
        c.alignment = alignment;
        c.domains = domains;
        c.divineRank = rank;
        return c;
    }

    @Test
    void alignmentsWrittenInManyWaysLandOnTheRightSquare() {
        assertArrayEquals(new int[]{0, 0}, Pantheon.alignmentCell("lawful good"));
        assertArrayEquals(new int[]{2, 2}, Pantheon.alignmentCell("Chaotic Evil"));
        assertArrayEquals(new int[]{1, 1}, Pantheon.alignmentCell("true neutral"));
        assertArrayEquals(new int[]{1, 1}, Pantheon.alignmentCell("Neutral"));
        assertArrayEquals(new int[]{1, 0}, Pantheon.alignmentCell("neutral good"));
        assertArrayEquals(new int[]{0, 1}, Pantheon.alignmentCell("lawful neutral"));
        assertArrayEquals(new int[]{2, 1}, Pantheon.alignmentCell("chaotic neutral"));
        assertArrayEquals(new int[]{1, 2}, Pantheon.alignmentCell("neutral evil"));
        assertArrayEquals(new int[]{0, 2}, Pantheon.alignmentCell("LE"));
        assertArrayEquals(new int[]{2, 0}, Pantheon.alignmentCell("cg"));
        assertArrayEquals(new int[]{1, 1}, Pantheon.alignmentCell("N"));
        assertArrayEquals(new int[]{0, 0}, Pantheon.alignmentCell("lawful-good"));
        assertNull(Pantheon.alignmentCell("unaligned"));
        assertNull(Pantheon.alignmentCell(""));
        assertNull(Pantheon.alignmentCell(null));
        assertNull(Pantheon.alignmentCell("any alignment"));
    }

    @Test
    void theGridPutsEachGodInItsSquareAndKeepsTheRestApart() {
        List<Card> gods = List.of(god("Sol", "lawful good", "", ""), god("Nyx", "chaotic neutral", "", ""),
                god("Vel", "lawful good", "", ""), god("Ash", "unaligned", "", ""));
        Pantheon.AlignmentGrid g = Pantheon.alignmentGrid(gods);
        assertEquals(List.of("Sol", "Vel"), g.cells().get(0).get(0).stream().map(c -> c.name).toList(), "sorted by name");
        assertEquals(List.of("Nyx"), g.cells().get(2).get(1).stream().map(c -> c.name).toList());
        assertEquals(List.of("Ash"), g.elsewhere().stream().map(c -> c.name).toList());
        assertTrue(g.cells().get(1).get(1).isEmpty());
    }

    @Test
    void domainsAreReadFromTextAndTheUnclaimedOnesAreFound() {
        Card a = god("A", "", "life, Light and  war", "");
        assertEquals(List.of("Life", "Light", "War"), Pantheon.domainsOf(a));
        assertEquals(List.of("Sea", "Storms"), Pantheon.domainsOf(god("B", "", "sea; Storms; sea", "")), "no repeats, any separator");
        assertTrue(Pantheon.domainsOf(god("C", "", "  ", "")).isEmpty());

        List<Card> gods = List.of(a, god("B", "", "Tempest, Sea", ""));
        Map<String, List<Card>> d = Pantheon.byDomain(gods);
        assertEquals(List.of("A"), d.get("Life").stream().map(c -> c.name).toList());
        assertEquals(List.of("B"), d.get("Tempest").stream().map(c -> c.name).toList());
        assertEquals(List.of("B"), d.get("Sea").stream().map(c -> c.name).toList(), "a domain of your own is listed too");
        assertTrue(d.get("Death").isEmpty(), "standard domains with no god are still listed");
        assertEquals("Arcana", d.keySet().iterator().next(), "the standard ones come first, in order");
        assertEquals("Sea", List.copyOf(d.keySet()).get(Pantheon.STANDARD_DOMAINS.size()), "then the extra ones");
        List<String> unclaimed = Pantheon.unclaimed(gods);
        assertTrue(unclaimed.contains("Death") && unclaimed.contains("Forge"));
        assertFalse(unclaimed.contains("Life") || unclaimed.contains("War") || unclaimed.contains("Tempest"));
        assertFalse(unclaimed.contains("Sea"), "only standard domains count as unclaimed");
    }

    @Test
    void ranksAreOrderedFromTheHighestAndGroupsComeFromTheirOrganisations() {
        List<Card> gods = List.of(god("D", "", "", "Demigod"), god("L", "", "", "lesser god"), god("G", "", "", "Greater god"),
                god("N", "", "", ""), god("I", "", "", "Intermediate god"));
        assertEquals(List.of("Greater god", "Intermediate god", "lesser god", "Demigod", "No rank"), List.copyOf(Pantheon.byRank(gods).keySet()));

        Map<String, List<Card>> groups = Pantheon.byGroup(gods, c -> c.name.equals("G") ? List.of("High Court", "Sea Court")
                : c.name.equals("I") ? List.of("High Court") : List.of());
        assertEquals(List.of("High Court", "Sea Court", "Not in an organisation"), List.copyOf(groups.keySet()));
        assertEquals(List.of("G", "I"), groups.get("High Court").stream().map(c -> c.name).toList());
        assertEquals(3, groups.get("Not in an organisation").size());
    }

    @Test
    void onlyDeitiesAreTreatedAsGods() {
        Card monster = new Card();
        monster.name = "Goblin";
        assertEquals(List.of("Sol"), Pantheon.deitiesOf(List.of(monster, god("Sol", "", "", ""))).stream().map(c -> c.name).toList());
    }

    @Test
    void aDeityCardKeepsItsFieldsHasADivineTreeIconAndIsFoundBySearchAndExport(@TempDir Path tmp) throws Exception {
        ProjectStore s = ProjectStore.create(tmp, "Camp");
        Card g = new Card();
        g.name = "Maris";
        g.kind = Card.Kind.DEITY;
        g.portfolio = "the sea and sailors";
        g.domains = "Tempest, Nature";
        g.divineRank = "Greater god";
        g.alignment = "chaotic neutral";
        g.symbol = "a silver trident";
        g.favouredWeapon = "trident";
        g.holyDays = "Midsummer tide";
        g.tenets = "Never turn away the shipwrecked.";
        g.rites = "Pour out the first cup of wine.";
        s.createCard(s.project.root, g);
        s.flush();
        TreeNode node = s.project.root.find(g.id);
        assertTrue(node.isCard());
        assertTrue(node.divine, "a deity gets the deity icon");

        Card back = ProjectStore.open(tmp, "Camp").card(g.id);
        assertEquals(Card.Kind.DEITY, back.kind);
        assertEquals("the sea and sailors", back.portfolio);
        assertEquals("Tempest, Nature", back.domains);
        assertEquals("Never turn away the shipwrecked.", back.tenets);
        assertTrue(ProjectStore.open(tmp, "Camp").project.root.find(g.id).divine, "the flag is saved with the project");

        Card copy = back.copy();
        assertEquals("a silver trident", copy.symbol, "a copy keeps the divinity fields");

        assertTrue(SearchService.search(s, "shipwrecked", 10).stream().anyMatch(r -> r.boardId.equals(g.id)), "tenets are searchable");
        assertTrue(SearchService.search(s, "trident", 10).stream().anyMatch(r -> r.boardId.equals(g.id)));
        String md = MarkdownExport.card(g);
        assertTrue(md.startsWith("# Maris"));
        assertTrue(md.contains("*Greater god, chaotic neutral*"));
        assertTrue(md.contains("Portfolio") && md.contains("the sea and sailors"));
        assertTrue(md.contains("## Tenets") && md.contains("## Rites and taboos"));
        assertFalse(md.contains("Armor Class"), "a god is not printed as a stat block");

        g.kind = Card.Kind.NPC;
        s.syncDivine(g);
        assertFalse(s.project.root.find(g.id).divine, "changing the kind back removes the icon");
    }
}
