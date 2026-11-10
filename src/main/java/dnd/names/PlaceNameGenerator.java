package dnd.names;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Invents names for places on a fantasy map: towns, kingdoms and regions, seas, rivers and mountain ranges. Seeded, so the
 * same seed always gives the same names; a generator never gives the same name twice.
 */
public final class PlaceNameGenerator {
    private final Random random;
    private final Set<String> used = new HashSet<>();

    public PlaceNameGenerator(long seed) {
        this.random = new Random(seed);
    }

    private static final List<String> TOWN_START = List.of("Ash", "Oak", "Raven", "Stone", "Silver", "Gold", "Frost", "Mill", "Bright", "Thorn",
            "Elder", "High", "Low", "North", "West", "East", "South", "Black", "White", "Red", "Green", "Storm", "Wolf", "Bear", "Hawk", "Iron",
            "Copper", "Willow", "Fair", "Crow", "Amber", "Birch", "Cold", "Dun", "Elm", "Fox", "Grey", "Hart", "Ivy", "King", "Lark", "Moss",
            "Pine", "Rook", "Sun", "Swan", "Wick", "Win");
    private static final List<String> TOWN_END = List.of("ford", "ton", "wick", "haven", "mere", "bury", "dale", "field", "wood", "port", "brook",
            "gate", "hold", "stead", "minster", "fall", "cross", "bridge", "vale", "wall", "ham", "by", "thorpe", "marsh", "cliff", "watch");

    private static final List<String> SYLLABLE_START = List.of("Al", "Bra", "Cal", "Dor", "El", "Fen", "Gal", "Hal", "Ir", "Jor", "Kar", "Lor",
            "Mar", "Nor", "Or", "Pel", "Quin", "Ral", "Sar", "Tor", "Ul", "Val", "Wen", "Yar", "Zen", "Ast", "Bel", "Cor", "Dra", "Eth");
    private static final List<String> SYLLABLE_MIDDLE = List.of("a", "e", "i", "o", "an", "en", "ar", "or", "el", "ia", "ae", "un", "id");
    private static final List<String> SYLLABLE_END = List.of("dor", "mar", "ria", "nia", "land", "heim", "gard", "thor", "vik", "mere", "wyn",
            "tar", "dun", "ven", "sia", "oth", "ard", "ell", "ica", "moor");

    private static final List<String> REGION_PATTERNS = List.of("Kingdom of %s", "%s", "The %s Marches", "Duchy of %s", "%s Highlands",
            "The %s Reach", "Empire of %s", "%s Free Lands", "Principality of %s", "The %s Dominion");
    private static final List<String> SEA_PATTERNS = List.of("Sea of %s", "%s Sea", "The %s Gulf", "%s Ocean", "The %s Deep", "Bay of %s");
    private static final List<String> MOUNTAIN_PATTERNS = List.of("The %s Mountains", "%s Peaks", "The %s Range", "Mount %s", "The %s Spine");
    private static final List<String> RIVER_ADJECTIVE = List.of("Silver", "Swift", "Winding", "Grey", "Amber", "Cold", "Long", "Black", "Clear", "Green");
    private static final List<String> RIVER_NOUN = List.of("flow", "run", "water", "brook", "stream", "wash", "rill", "ford");

    private String pick(List<String> list) {
        return list.get(random.nextInt(list.size()));
    }

    /** Tries a few times to get a name nobody has had yet; after that it numbers the name. */
    private String unique(java.util.function.Supplier<String> make) {
        for (int i = 0; i < 30; i++) {
            String s = make.get();
            if (used.add(s)) return s;
        }
        String base = make.get();
        int n = 2;
        while (!used.add(base + " " + n)) n++;
        return base + " " + n;
    }

    private String invented() {
        StringBuilder sb = new StringBuilder(pick(SYLLABLE_START));
        if (random.nextInt(3) == 0) sb.append(pick(SYLLABLE_MIDDLE));
        sb.append(pick(SYLLABLE_END));
        return sb.toString();
    }

    /** A town, village or city: Ashford, Ravenhold. */
    public String town() {
        return unique(() -> pick(TOWN_START) + pick(TOWN_END));
    }

    /** A kingdom or region: Kingdom of Aldmar, The Thorvik Marches. */
    public String region() {
        return unique(() -> String.format(pick(REGION_PATTERNS), invented()));
    }

    /** A sea, gulf or bay: Sea of Calmar. */
    public String sea() {
        return unique(() -> String.format(pick(SEA_PATTERNS), invented()));
    }

    /** A mountain range: The Borwyn Mountains. */
    public String mountains() {
        return unique(() -> String.format(pick(MOUNTAIN_PATTERNS), invented()));
    }

    /** A river: Silverflow, or River Ulmar. */
    public String river() {
        return unique(() -> random.nextBoolean() ? pick(RIVER_ADJECTIVE) + pick(RIVER_NOUN) : "River " + invented());
    }
}
