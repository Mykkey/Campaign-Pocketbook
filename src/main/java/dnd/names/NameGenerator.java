package dnd.names;

import dnd.names.NameData.Race;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Picks random "First Last" names for a race. No UI code, so it is easy to test. */
public class NameGenerator {
    public enum Gender {
        ANY("Any"), MASCULINE("Masculine"), FEMININE("Feminine");

        public final String display;

        Gender(String display) { this.display = display; }

        @Override
        public String toString() { return display; }
    }

    private final Random random;

    public NameGenerator() { this(new Random()); }

    public NameGenerator(Random random) { this.random = random; }

    public String generate(Race race, Gender gender) {
        return pick(firstPool(race, gender)) + " " + pick(race.last);
    }

    /** Generates {@code count} names, avoiding duplicates within the batch where the pools allow it. */
    public List<String> generate(Race race, Gender gender, int count) {
        Set<String> out = new LinkedHashSet<>();
        int attempts = 0;
        while (out.size() < count && attempts++ < count * 20) {
            out.add(generate(race, gender));
        }
        return new ArrayList<>(out);
    }

    private List<String> firstPool(Race race, Gender gender) {
        switch (gender) {
            case MASCULINE:
                return race.masculine;
            case FEMININE:
                return race.feminine;
            default:
                return random.nextBoolean() ? race.masculine : race.feminine;
        }
    }

    private String pick(List<String> list) {
        return list.get(random.nextInt(list.size()));
    }
}
