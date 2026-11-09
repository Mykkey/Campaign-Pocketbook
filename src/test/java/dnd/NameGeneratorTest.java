package dnd;

import dnd.names.NameData;
import dnd.names.NameData.Race;
import dnd.names.NameGenerator;
import dnd.names.NameGenerator.Gender;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NameGeneratorTest {

    @Test
    void everyRaceAndGenderGivesFirstAndLastName() {
        NameGenerator g = new NameGenerator(new Random(42));
        for (Race r : NameData.races()) {
            for (Gender gender : Gender.values()) {
                for (int i = 0; i < 50; i++) {
                    String n = g.generate(r, gender);
                    int space = n.indexOf(' ');
                    assertTrue(space > 0 && space < n.length() - 1, r + ": '" + n + "' needs two parts");
                    assertEquals(n.trim(), n);
                }
            }
        }
    }

    @Test
    void poolsAreHealthy() {
        for (Race r : NameData.races()) {
            for (List<String> pool : List.of(r.masculine, r.feminine, r.last)) {
                assertTrue(pool.size() >= 10, r + " pool too small");
                Set<String> seen = new HashSet<>();
                for (String s : pool) {
                    assertFalse(s.isBlank(), r + " has a blank entry");
                    assertTrue(seen.add(s.toLowerCase()), r + " duplicate: " + s);
                }
            }
        }
    }

    @Test
    void sameSeedSameNames() {
        NameGenerator a = new NameGenerator(new Random(7));
        NameGenerator b = new NameGenerator(new Random(7));
        assertEquals(a.generate(Race.DWARF, Gender.ANY, 5), b.generate(Race.DWARF, Gender.ANY, 5));
    }

    @Test
    void genderFilterUsesTheRightPool() {
        NameGenerator g = new NameGenerator(new Random(1));
        for (int i = 0; i < 100; i++) {
            String first = g.generate(Race.ELF, Gender.FEMININE).split(" ")[0];
            assertTrue(Race.ELF.feminine.contains(first));
            String mfirst = g.generate(Race.ELF, Gender.MASCULINE).split(" ")[0];
            assertTrue(Race.ELF.masculine.contains(mfirst));
        }
        boolean sawM = false, sawF = false;
        for (int i = 0; i < 200; i++) {
            String first = g.generate(Race.HUMAN, Gender.ANY).split(" ")[0];
            sawM |= Race.HUMAN.masculine.contains(first);
            sawF |= Race.HUMAN.feminine.contains(first);
        }
        assertTrue(sawM && sawF);
    }

    @Test
    void batchHasNoDuplicates() {
        List<String> names = new NameGenerator(new Random(3)).generate(Race.HUMAN, Gender.ANY, 10);
        assertEquals(10, new HashSet<>(names).size());
    }
}
