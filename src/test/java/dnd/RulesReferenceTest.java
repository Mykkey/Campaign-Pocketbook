package dnd;

import dnd.battle.ConditionStyle;
import dnd.rules.RulesReference;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RulesReferenceTest {
    @Test
    void everyStandardConditionHasARule() {
        for (String c : ConditionStyle.names()) {
            assertNotNull(RulesReference.forCondition(c), "no rule text for " + c);
        }
        assertNull(RulesReference.forCondition("Inspired"), "custom conditions have no rule");
        assertNotNull(RulesReference.forCondition("  prone "), "lookup ignores case and spaces");
    }

    @Test
    void entriesAreCompleteAndNotRepeated() {
        Set<String> seen = new HashSet<>();
        for (RulesReference.Entry e : RulesReference.all()) {
            assertFalse(e.title().isBlank());
            assertFalse(e.body().isBlank(), e.title());
            assertTrue(RulesReference.categories().contains(e.category()));
            assertTrue(seen.add(e.category() + "/" + e.title().toLowerCase()), "duplicate " + e.title());
        }
        assertTrue(RulesReference.categories().size() >= 5);
    }

    @Test
    void searchFindsByTitleBodyAndCategoryAndAnEmptySearchShowsEverything() {
        assertEquals(RulesReference.all().size(), RulesReference.search("").size());
        assertEquals(RulesReference.all().size(), RulesReference.search(null).size());
        assertTrue(RulesReference.search("grapple").stream().anyMatch(e -> e.title().equals("Grappled")));
        assertTrue(RulesReference.search("FALLING").stream().anyMatch(e -> e.title().equals("Falling")));
        assertTrue(RulesReference.search("hazards").size() >= 3);
        assertTrue(RulesReference.search("zzzz-no-such-rule").isEmpty());
    }
}
