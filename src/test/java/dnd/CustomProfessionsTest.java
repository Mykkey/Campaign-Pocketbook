package dnd;

import dnd.npc.CustomProfessions;
import dnd.npc.NpcData;
import dnd.npc.NpcData.Profession;
import dnd.npc.NpcGenerator;
import dnd.npc.NpcGenerator.Spec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CustomProfessionsTest {
    private static Profession prof(String name) {
        return new Profession(name, "Homebrew", "commoner", List.of("one", "two", "three"));
    }

    @Test
    void startsEmptyAndAllIsJustTheBuiltIns(@TempDir Path tmp) {
        CustomProfessions c = new CustomProfessions(tmp.resolve("p.json"));
        assertTrue(c.custom().isEmpty());
        assertEquals(NpcData.PROFESSIONS.size(), c.all().size());
    }

    @Test
    void addedProfessionsPersistAndAppearAfterTheBuiltIns(@TempDir Path tmp) {
        Path f = tmp.resolve("sub").resolve("p.json");
        CustomProfessions c = new CustomProfessions(f);
        c.put(null, new Profession("Cheesemonger", "Merchants and crafters", "commoner", List.of("rinds", "rumours", "rats")));

        CustomProfessions again = new CustomProfessions(f);
        assertEquals(1, again.custom().size());
        Profession p = again.custom().get(0);
        assertEquals("Cheesemonger", p.name());
        assertEquals("Merchants and crafters", p.category());
        assertEquals(List.of("rinds", "rumours", "rats"), p.knows());
        assertEquals(NpcData.PROFESSIONS.size() + 1, again.all().size());
        assertEquals("Cheesemonger", again.all().get(again.all().size() - 1).name());
    }

    @Test
    void builtInNamesAndDuplicatesAreRejected(@TempDir Path tmp) {
        CustomProfessions c = new CustomProfessions(tmp.resolve("p.json"));
        assertThrows(IllegalArgumentException.class, () -> c.put(null, prof("innkeeper")), "built-in, any case");
        assertThrows(IllegalArgumentException.class, () -> c.put(null, prof("  ")));
        c.put(null, prof("Rat catcher"));
        assertThrows(IllegalArgumentException.class, () -> c.put(null, prof("RAT CATCHER")));
        assertEquals(1, c.custom().size());
        assertTrue(CustomProfessions.isBuiltIn("Blacksmith"));
        assertFalse(CustomProfessions.isBuiltIn("Rat catcher"));
    }

    @Test
    void editingRenamesAndRemovingDeletes(@TempDir Path tmp) {
        CustomProfessions c = new CustomProfessions(tmp.resolve("p.json"));
        c.put(null, prof("Rat catcher"));
        c.put("Rat catcher", new Profession("Vermin hunter", "Wilderness and the road", "bandit", List.of("a", "b", "c")));
        assertEquals(1, c.custom().size());
        assertEquals("Vermin hunter", c.custom().get(0).name());
        assertEquals("bandit", c.custom().get(0).template());
        c.put("Vermin hunter", c.custom().get(0)); // saving with the same name is fine

        c.remove("vermin hunter");
        assertTrue(c.custom().isEmpty());
        assertTrue(new CustomProfessions(tmp.resolve("p.json")).custom().isEmpty());
    }

    @Test
    void blankCategoryAndTemplateGetDefaults(@TempDir Path tmp) {
        CustomProfessions c = new CustomProfessions(tmp.resolve("p.json"));
        c.put(null, new Profession("Wanderer", " ", "", List.of("x", "y", "z")));
        Profession p = c.custom().get(0);
        assertEquals("Custom", p.category());
        assertEquals("commoner", p.template());
    }

    @Test
    void theGeneratorCanRollACustomProfession(@TempDir Path tmp) {
        CustomProfessions c = new CustomProfessions(tmp.resolve("p.json"));
        c.put(null, prof("Dragon groomer"));
        NpcGenerator g = new NpcGenerator(new Random(1), c.all());
        assertEquals("Dragon groomer", g.generate(new Spec(null, null, "Dragon groomer", null, null, null)).profession.name());
        boolean seen = false;
        for (int i = 0; i < 2000 && !seen; i++) seen = "Homebrew".equals(g.generate(new Spec(null, null, null, "Homebrew", null, null)).profession.category());
        assertTrue(seen, "a custom category works as a filter");
    }
}