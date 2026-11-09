package dnd.loot;

/** The tables behind the loot generator: gems, art, magic item names by rarity, and little personal curios. */
final class LootData {
    private LootData() { }

    static final String[] GEMS_10 = {"azurite", "banded agate", "blue quartz", "eye agate", "hematite", "lapis lazuli",
            "malachite", "moss agate", "obsidian", "rhodochrosite", "tiger eye", "turquoise"};
    static final String[] GEMS_50 = {"bloodstone", "carnelian", "chalcedony", "chrysoprase", "citrine", "jasper",
            "moonstone", "onyx", "quartz", "sardonyx", "star rose quartz", "zircon"};
    static final String[] GEMS_100 = {"amber", "amethyst", "chrysoberyl", "coral", "garnet", "jade", "jet", "pearl",
            "spinel", "tourmaline"};
    static final String[] GEMS_500 = {"alexandrite", "aquamarine", "black pearl", "blue spinel", "peridot", "topaz"};
    static final String[] GEMS_1000 = {"black opal", "blue sapphire", "emerald", "fire opal", "opal", "star ruby",
            "star sapphire", "yellow sapphire"};
    static final String[] GEMS_5000 = {"black sapphire", "diamond", "jacinth", "ruby"};

    static final String[] ART_25 = {"pewter tankard with a hunting scene", "carved bone comb", "thin silver bangle",
            "embroidered altar cloth", "painted ceramic tile showing a harbour", "copper bowl with a silver rim",
            "set of engraved bone chess pieces", "hand mirror in a lacquered frame", "silk scarf dyed deep red",
            "brass pocket sundial with a velvet case"};
    static final String[] ART_250 = {"silver ring set with a small garnet", "carved ivory fox", "heavy gold armband",
            "silver necklace with a moonstone drop", "gilded bronze circlet", "brocade coat with gold buttons",
            "large wall hanging of a royal hunt", "jade-handled letter opener", "set of six crystal goblets",
            "enamelled snuff box with a gold hinge"};
    static final String[] ART_750 = {"silver goblet studded with pearls", "ceremonial sword with an ornate silver sheath",
            "ebony lute with mother-of-pearl inlay", "small gold idol of a forgotten god", "gold hair comb set with amber",
            "crystal decanter with a gold stopper", "gold-handled dagger with a polished jet pommel",
            "filigree silver brooch", "obsidian bust with gold ear rings", "gold-leaf theatre mask"};
    static final String[] ART_2500 = {"gold chain set with a large fire opal", "landscape by a celebrated painter",
            "velvet cloak clasped with moonstones", "platinum cuff set with a blue sapphire",
            "pair of jewelled dress gloves", "gold anklet set with small rubies", "mechanical gold songbird",
            "gold tiara set with four aquamarines", "monocle with a sapphire lens", "necklace of matched pink pearls"};
    static final String[] ART_7500 = {"gold crown studded with gems", "platinum signet ring set with a diamond",
            "gold statuette of a dragon with ruby eyes", "gold drinking cup set with emeralds",
            "platinum-trimmed jewel casket", "gilded ceremonial child's cradle",
            "jade board game with solid gold pieces", "ivory drinking horn bound in gold and gems"};

    static final String[] MAGIC_COMMON = {"Potion of healing", "Potion of climbing", "Potion of animal friendship",
            "Spell scroll (cantrip)", "Spell scroll (1st level)", "Cloak of billowing", "Driftglobe", "Moon-touched sword"};
    static final String[] MAGIC_UNCOMMON = {"Potion of greater healing", "Potion of water breathing", "Potion of fire breath",
            "Spell scroll (2nd level)", "Spell scroll (3rd level)", "Weapon +1", "Shield +1", "Bag of holding",
            "Boots of elvenkind", "Cloak of elvenkind", "Cloak of protection", "Gauntlets of ogre power",
            "Gloves of swimming and climbing", "Goggles of night", "Hat of disguise", "Headband of intellect",
            "Lantern of revealing", "Rope of climbing", "Wand of magic detection", "Wand of secrets",
            "Decanter of endless water", "Eversmoking bottle", "Immovable rod", "Bracers of archery"};
    static final String[] MAGIC_RARE = {"Potion of superior healing", "Spell scroll (4th level)", "Spell scroll (5th level)",
            "Weapon +2", "Armor +1", "Ring of protection", "Ring of evasion", "Amulet of health", "Belt of hill giant strength",
            "Boots of speed", "Cloak of displacement", "Flame tongue", "Frost brand", "Helm of telepathy", "Wand of fireballs",
            "Wand of lightning bolts", "Necklace of fireballs", "Ring of spell storing", "Staff of healing", "Broom of flying",
            "Ring of free action"};
    static final String[] MAGIC_VERY_RARE = {"Potion of supreme healing", "Spell scroll (6th level)", "Spell scroll (7th level)",
            "Weapon +3", "Armor +2", "Staff of fire", "Staff of frost", "Ring of regeneration", "Robe of stars",
            "Belt of fire giant strength", "Amulet of the planes", "Carpet of flying", "Crystal ball", "Manual of bodily health",
            "Ring of telekinesis", "Rod of absorption", "Sword of sharpness"};
    static final String[] MAGIC_LEGENDARY = {"Spell scroll (8th level)", "Spell scroll (9th level)", "Armor +3", "Holy avenger",
            "Vorpal sword", "Staff of the magi", "Ring of three wishes", "Cloak of invisibility", "Rod of lordly might",
            "Robe of the archmagi", "Luck blade"};

    /** Small personal oddments: what is in a pocket, not what is in a vault. */
    static final String[] CURIOS = {
            "a tarnished locket holding a lock of grey hair", "a deck of cards with the queen of hearts missing",
            "a tooth on a leather cord", "a folded letter, water-stained and unsigned", "a wax-sealed vial of something that smells of lavender",
            "a carved wooden holy symbol, worn smooth", "a small brass key with no obvious lock", "a map scrap showing half a river",
            "a pouch of dried herbs", "a child's wooden toy soldier", "a bent silver spoon engraved with a family crest",
            "a whistle that makes no sound a person can hear", "a coin from a kingdom nobody recognises", "a sprig of dried mistletoe tied with red thread",
            "a lucky rabbit's foot, dyed blue", "a small mirror with a cracked corner", "a battered flask of cheap spirits",
            "a pair of loaded dice", "a love note written in a hasty hand", "an unlit stub of a black candle",
            "a stained bandage roll and a needle", "a ring of keys, none of them matching", "a tin of boot grease",
            "a rolled-up contract with the name scratched out", "a scrap of cloth in the colours of a noble house",
            "a pocket-sized book of bawdy songs", "a bag of marbles", "a fishhook necklace", "a rusted dagger with a jewelled hilt, jewels missing",
            "a smoking pipe carved like a dragon's head", "a handful of nails and a small hammer", "a dried flower pressed in a prayer book",
            "a letter of introduction to someone who has since died", "a tiny bell that rings when something undead is near... or so the owner claims",
            "a bundle of arrows fletched with unusual feathers", "a stub of chalk and a ball of string", "a lock of red hair in a silk ribbon",
            "a clay token stamped with a guild mark", "a half-eaten honey cake wrapped in cloth"};
}
