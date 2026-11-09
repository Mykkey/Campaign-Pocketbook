package dnd.npc;

import dnd.names.NameData.Race;
import dnd.names.NameGenerator;
import dnd.names.NameGenerator.Gender;
import dnd.npc.NpcData.AgeGroup;
import dnd.npc.NpcData.Attitude;
import dnd.npc.NpcData.Profession;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Rolls a random person for a DM who needs one right now. Anything pinned in the {@link Spec} is used as given and
 * everything else is rolled, and any single detail can be re-rolled later. No UI code; the {@link Random} is
 * injectable so it can be tested.
 */
public final class NpcGenerator {
    /** What the DM has pinned down. Null (or ANY for gender) means "roll it". */
    public record Spec(Race race, Gender gender, String profession, String category, AgeGroup age, Attitude attitude) {
        public static Spec any() {
            return new Spec(null, null, null, null, null, null);
        }
    }

    /** The parts of an NPC that can be re-rolled on their own. */
    public enum Detail { NAME, AGE, PROFESSION, ATTITUDE, APPEARANCE, PERSONALITY, MANNERISM, VOICE, WANTS, SECRET, KNOWS }

    private final Random random;
    private final List<Profession> professions;
    private final NameGenerator names;

    public NpcGenerator(Random random, List<Profession> professions) {
        this.random = random;
        this.professions = professions.isEmpty() ? NpcData.PROFESSIONS : professions;
        this.names = new NameGenerator(random);
    }

    /** Built-in professions plus the DM's own, with a fresh random source. */
    public NpcGenerator() {
        this(new Random(), CustomProfessions.shared().all());
    }

    public List<Profession> professions() {
        return professions;
    }

    // ================================================================ generating

    public Npc generate(Spec spec) {
        Npc n = new Npc();
        n.race = spec.race() != null ? spec.race() : randomRace();
        n.gender = n.race.unisex() ? Gender.ANY
                : spec.gender() == Gender.MASCULINE || spec.gender() == Gender.FEMININE ? spec.gender()
                : random.nextBoolean() ? Gender.MASCULINE : Gender.FEMININE;
        n.name = names.generate(n.race, n.gender);
        n.ageGroup = spec.age() != null ? spec.age() : randomAgeGroup();
        n.age = randomAge(n.race, n.ageGroup);
        n.profession = pickProfession(spec);
        n.attitude = spec.attitude() != null ? spec.attitude() : randomAttitude();
        n.opening = pick(NpcData.OPENINGS[n.attitude.ordinal()]);
        n.appearance = pickDistinct(NpcData.APPEARANCE, 2);
        n.personality = personality(n.attitude);
        n.mannerism = pick(NpcData.MANNERISMS);
        n.voice = pick(NpcData.VOICES);
        n.wants = pick(NpcData.WANTS);
        n.secret = pick(NpcData.SECRETS);
        n.knows = pickDistinct(n.profession.knows().toArray(new String[0]), 2);
        return n;
    }

    /** Re-rolls one detail, leaving everything else as it is. Pinned choices in {@code spec} are respected. */
    public void reroll(Npc n, Detail detail, Spec spec) {
        switch (detail) {
            case NAME -> n.name = names.generate(n.race, n.gender);
            case AGE -> {
                if (spec.age() == null) n.ageGroup = randomAgeGroup();
                n.age = randomAge(n.race, n.ageGroup);
            }
            case PROFESSION -> {
                n.profession = pickProfession(spec);
                n.knows = pickDistinct(n.profession.knows().toArray(new String[0]), 2);
            }
            case ATTITUDE -> {
                if (spec.attitude() == null) n.attitude = randomAttitude();
                n.opening = pick(NpcData.OPENINGS[n.attitude.ordinal()]);
                n.personality = personality(n.attitude);
            }
            case APPEARANCE -> n.appearance = pickDistinct(NpcData.APPEARANCE, 2);
            case PERSONALITY -> n.personality = personality(n.attitude);
            case MANNERISM -> n.mannerism = pick(NpcData.MANNERISMS);
            case VOICE -> n.voice = pick(NpcData.VOICES);
            case WANTS -> n.wants = pick(NpcData.WANTS);
            case SECRET -> n.secret = pick(NpcData.SECRETS);
            case KNOWS -> n.knows = pickDistinct(n.profession.knows().toArray(new String[0]), 2);
        }
    }

    // ================================================================ choices

    private Profession pickProfession(Spec spec) {
        if (spec.profession() != null) {
            for (Profession p : professions) if (p.name().equalsIgnoreCase(spec.profession())) return p;
        }
        List<Profession> pool = professions;
        if (spec.category() != null) {
            List<Profession> inCategory = new ArrayList<>();
            for (Profession p : professions) if (p.category().equalsIgnoreCase(spec.category())) inCategory.add(p);
            if (!inCategory.isEmpty()) pool = inCategory;
        }
        return pool.get(random.nextInt(pool.size()));
    }

    /** Mostly humans, as in a typical town, with the other races turning up now and then. */
    private Race randomRace() {
        Race[] all = Race.values();
        int[] weight = new int[all.length];
        int total = 0;
        for (int i = 0; i < all.length; i++) {
            weight[i] = switch (all[i]) {
                case HUMAN -> 40;
                case ELF, DWARF, HALFLING -> 8;
                case HALF_ELF -> 6;
                case GNOME, HALF_ORC -> 5;
                case TIEFLING, DRAGONBORN -> 4;
                default -> 1;
            };
            total += weight[i];
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < all.length; i++) {
            roll -= weight[i];
            if (roll < 0) return all[i];
        }
        return Race.HUMAN;
    }

    private AgeGroup randomAgeGroup() {
        int r = random.nextInt(100);
        return r < 15 ? AgeGroup.YOUNG : r < 60 ? AgeGroup.ADULT : r < 85 ? AgeGroup.MIDDLE_AGED : AgeGroup.ELDERLY;
    }

    private int randomAge(Race race, AgeGroup group) {
        int[] range = NpcData.ageRange(race, group);
        return range[0] + random.nextInt(range[1] - range[0] + 1);
    }

    private Attitude randomAttitude() {
        int r = random.nextInt(100);
        return r < 25 ? Attitude.FRIENDLY : r < 65 ? Attitude.NEUTRAL : r < 85 ? Attitude.WARY : Attitude.HOSTILE;
    }

    /** Two traits, leaning warm or cold with the attitude. */
    private List<String> personality(Attitude attitude) {
        List<String> out = new ArrayList<>();
        String[] first, second;
        switch (attitude) {
            case FRIENDLY -> {
                first = NpcData.PERSONALITY_WARM;
                second = NpcData.PERSONALITY_WARM;
            }
            case WARY -> {
                first = NpcData.PERSONALITY_NEUTRAL;
                second = NpcData.PERSONALITY_COLD;
            }
            case HOSTILE -> {
                first = NpcData.PERSONALITY_COLD;
                second = NpcData.PERSONALITY_COLD;
            }
            default -> {
                first = NpcData.PERSONALITY_NEUTRAL;
                String[][] any = {NpcData.PERSONALITY_WARM, NpcData.PERSONALITY_NEUTRAL, NpcData.PERSONALITY_COLD};
                second = any[random.nextInt(3)];
            }
        }
        out.add(pick(first));
        for (int i = 0; i < 20 && out.size() < 2; i++) {
            String s = pick(second);
            if (!out.contains(s)) out.add(s);
        }
        return out;
    }

    private String pick(String[] table) {
        return table[random.nextInt(table.length)];
    }

    private List<String> pickDistinct(String[] table, int count) {
        List<String> out = new ArrayList<>();
        int attempts = 0;
        while (out.size() < Math.min(count, table.length) && attempts++ < 50) {
            String s = table[random.nextInt(table.length)];
            if (!out.contains(s)) out.add(s);
        }
        return out;
    }

    /** Case-insensitive lookup, for the UI. */
    public Profession findProfession(String name) {
        if (name == null) return null;
        for (Profession p : professions) if (p.name().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) return p;
        return null;
    }
}