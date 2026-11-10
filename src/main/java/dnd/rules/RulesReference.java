package dnd.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A short, plain-words summary of the rules a DM looks up most at the table (fifth edition style). It is written as a memory
 * aid, not a copy of any rulebook: when in doubt, the book wins and so does the DM.
 */
public final class RulesReference {
    private RulesReference() { }

    public record Entry(String category, String title, String body) {
        boolean matches(String q) {
            return title.toLowerCase(Locale.ROOT).contains(q) || body.toLowerCase(Locale.ROOT).contains(q) || category.toLowerCase(Locale.ROOT).contains(q);
        }
    }

    public static final String CONDITIONS = "Conditions";
    public static final String COMBAT = "In combat";
    public static final String COVER = "Cover, light and vision";
    public static final String TRAVEL = "Exploring and travel";
    public static final String REST = "Rest and recovery";
    public static final String HAZARDS = "Hazards";

    private static final List<Entry> ALL = new ArrayList<>();

    private static void add(String category, String title, String body) {
        ALL.add(new Entry(category, title, body));
    }

    static {
        add(CONDITIONS, "Blinded", "Cannot see, so it automatically fails any check that needs sight.\nAttack rolls against it have advantage; its own attack rolls have disadvantage.");
        add(CONDITIONS, "Charmed", "Cannot attack the charmer or target it with harmful abilities or magic.\nThe charmer has advantage on social checks against it.");
        add(CONDITIONS, "Concentrating", "Not an official condition: a marker for a spell that needs concentration.\nCasting another concentration spell, being incapacitated or killed ends it. Whenever it takes damage it must make a Constitution save: DC 10 or half the damage taken, whichever is higher (one save per source of damage). A failed save ends the spell.");
        add(CONDITIONS, "Deafened", "Cannot hear, so it automatically fails any check that needs hearing.");
        add(CONDITIONS, "Exhausted", "Exhaustion comes in levels (the exact effects depend on the edition you use).\nIn the 2014 rules: 1 disadvantage on ability checks, 2 speed halved, 3 disadvantage on attacks and saves, 4 hit point maximum halved, 5 speed 0, 6 death. A long rest with food and drink removes one level.");
        add(CONDITIONS, "Frightened", "Disadvantage on ability checks and attack rolls while the source of its fear is in line of sight.\nIt cannot willingly move closer to the source.");
        add(CONDITIONS, "Grappled", "Its speed becomes 0 and it cannot benefit from any bonus to speed.\nEnds if the grappler is incapacitated, or if an effect moves it out of the grappler's reach.");
        add(CONDITIONS, "Incapacitated", "Cannot take actions or reactions.");
        add(CONDITIONS, "Invisible", "Cannot be seen without magic or special senses; it counts as heavily obscured for hiding. Its location can still be given away by noise or tracks.\nAttack rolls against it have disadvantage; its own attack rolls have advantage.");
        add(CONDITIONS, "Paralyzed", "Incapacitated, and cannot move or speak. Automatically fails Strength and Dexterity saves.\nAttack rolls against it have advantage, and any hit from within 5 feet is a critical hit.");
        add(CONDITIONS, "Petrified", "Turned to stone: incapacitated, cannot move or speak, unaware of its surroundings. Weight is multiplied by ten and it stops aging.\nAttacks against it have advantage; it fails Strength and Dexterity saves; it has resistance to all damage and is immune to poison and disease.");
        add(CONDITIONS, "Poisoned", "Disadvantage on attack rolls and ability checks.");
        add(CONDITIONS, "Prone", "Can only crawl, unless it stands up (that costs half its movement).\nDisadvantage on its own attack rolls. Attacks against it have advantage from within 5 feet and disadvantage from farther away.");
        add(CONDITIONS, "Restrained", "Its speed becomes 0. Attack rolls against it have advantage, and its own attack rolls have disadvantage.\nDisadvantage on Dexterity saves.");
        add(CONDITIONS, "Stunned", "Incapacitated, cannot move, and can speak only falteringly. Automatically fails Strength and Dexterity saves.\nAttack rolls against it have advantage.");
        add(CONDITIONS, "Unconscious", "Incapacitated, cannot move or speak, unaware of its surroundings. Drops what it holds and falls prone.\nAutomatically fails Strength and Dexterity saves. Attacks against it have advantage, and any hit from within 5 feet is a critical hit.");

        add(COMBAT, "Order of a turn", "On its turn a creature can move up to its speed and take one action. It may also take one bonus action if it has something that grants one, and interact with one object for free.\nA reaction can be used once per round, between its turn and its next one.");
        add(COMBAT, "Actions in combat", "Attack, Cast a Spell, Dash (double the movement), Disengage (no opportunity attacks this turn), Dodge (attacks against you have disadvantage, and you have advantage on Dexterity saves), Help (give an ally advantage on their next check or attack), Hide, Ready (prepare an action and a trigger), Search, Use an Object.");
        add(COMBAT, "Opportunity attacks", "When a hostile creature you can see moves out of your reach, you can use your reaction to make one melee attack against it. Moving by teleport, being forced to move, or taking the Disengage action does not provoke one.");
        add(COMBAT, "Advantage and disadvantage", "Roll two d20s and take the higher (advantage) or the lower (disadvantage). They cancel each other out: any amount of both means a single roll. Advantage and disadvantage do not stack.");
        add(COMBAT, "Critical hits", "A natural 20 on an attack roll always hits and is a critical hit: roll all the attack's damage dice twice, then add the modifiers once. A natural 1 always misses.");
        add(COMBAT, "Surprise", "A creature that does not notice a threat is surprised: it cannot move or take an action on its first turn of the fight, and cannot take reactions until that turn ends. Compare Stealth with passive Perception to see who is surprised.");
        add(COMBAT, "Grappling and shoving", "A grapple or a shove replaces one attack. The attacker makes a Strength (Athletics) check against the target's Strength (Athletics) or Dexterity (Acrobatics), the target's choice. The target can be at most one size larger.\nGrapple: the target is Grappled. Shove: knock it prone, or push it 5 feet away.");
        add(COMBAT, "Two-weapon fighting", "If you attack with a light melee weapon in one hand, you can use your bonus action to attack with a different light weapon in the other. You do not add your ability modifier to that extra attack's damage unless it is negative.");
        add(COMBAT, "Ranged attacks in melee", "A ranged attack against a target within 5 feet of a hostile creature that can see you is made with disadvantage. Attacking beyond a weapon's normal range, up to its long range, also has disadvantage; beyond long range you cannot attack.");
        add(COMBAT, "Mounted combat", "A willing creature at least one size larger can be a mount. A controlled mount acts on your initiative and can only Dash, Disengage or Dodge. An independent mount acts on its own turn.");
        add(COMBAT, "Death saves and dying", "At 0 hit points a player character falls unconscious and starts making death saving throws at the start of each of its turns: a d20, 10 or higher is a success. Three successes make it stable; three failures kill it.\nA natural 20 brings it back with 1 hit point; a natural 1 counts as two failures. Damage at 0 hit points is one failure (two if it was a critical hit). Healing at any amount ends the dying state.\nIf the damage taken at 0 hit points is at least its hit point maximum, it dies at once. Many DMs let monsters simply die at 0.");
        add(COMBAT, "Instant death", "If damage reduces you to 0 hit points and the damage left over equals or exceeds your hit point maximum, you die instantly.");
        add(COMBAT, "Stabilizing and knocking out", "A creature at 0 hit points can be stabilized with a DC 10 Wisdom (Medicine) check. When you reduce a creature to 0 hit points with a melee attack, you can choose to knock it out instead of killing it.");
        add(COMBAT, "Damage resistance and vulnerability", "Resistance halves the damage of a type (rounded down); vulnerability doubles it. Apply other changes first, then resistance, then vulnerability. Temporary hit points absorb damage first and are not added to healing.");
        add(COMBAT, "Concentration", "Only one concentration spell at a time. Concentration is broken by casting another, by being incapacitated or killed, and by failing a Constitution save when damaged (DC 10 or half the damage, whichever is higher).");

        add(COVER, "Half, three-quarters and total cover", "Half cover (a low wall, a creature in the way): +2 to AC and Dexterity saves.\nThree-quarters cover (a portcullis, a narrow window): +5.\nTotal cover: cannot be targeted directly.");
        add(COVER, "Lightly and heavily obscured", "Lightly obscured (dim light, patchy fog, moderate foliage): disadvantage on Perception checks that rely on sight.\nHeavily obscured (darkness, dense fog, thick foliage): a creature that relies on sight is effectively blinded.");
        add(COVER, "Bright light, dim light and darkness", "Bright light lets most creatures see normally. Dim light is lightly obscured. Darkness is heavily obscured.\nDarkvision treats dim light as bright and darkness as dim, but only in shades of grey and only within its range.");
        add(COVER, "Torches and lamps", "A torch gives bright light in a 20-foot radius and dim light for another 20 feet. A lantern: bright 30 feet, dim another 30 (hooded: bright 5 feet, dim 5 feet). A candle: bright 5 feet, dim another 5.");
        add(COVER, "Hiding", "You can try to hide when you are heavily obscured or behind cover. Make a Dexterity (Stealth) check; creatures compare it with their passive Wisdom (Perception). You are revealed when you attack, or when they find you.");
        add(COVER, "Passive checks", "A passive check is 10 plus all the usual modifiers (add 5 for advantage, subtract 5 for disadvantage). Passive Perception is the most common.");

        add(TRAVEL, "Travel pace", "Fast: 4 miles an hour, 30 miles a day, -5 penalty to passive Perception, no stealth.\nNormal: 3 miles an hour, 24 miles a day.\nSlow: 2 miles an hour, 18 miles a day, can travel stealthily.\nBy default a day of travel is 8 hours. Difficult terrain halves the distance.");
        add(TRAVEL, "Forced march", "After 8 hours of travel each extra hour needs a Constitution save for each traveller, DC 10 plus 1 for every hour past 8. A failure is a level of exhaustion.");
        add(TRAVEL, "Mounts and vehicles", "A mount travelling at a normal pace covers about 48 miles in a day (24 more than a walker); a sailing ship about 48, a longship about 72 and a rowboat about 15. These match the defaults of the world map ruler.");
        add(TRAVEL, "Food and water", "A creature needs a gallon of water and a pound of food a day (double water in hot weather). With half the water it risks exhaustion; with less than half it gains a level of exhaustion each day. Going without food: it can last 3 plus its Constitution modifier days (minimum 1) before it gains exhaustion each day.");
        add(TRAVEL, "Foraging", "While travelling at a normal or slow pace, a party can forage: a DC 10 Wisdom (Survival) check finds 1d6 + Wisdom modifier pounds of food and the same number of gallons of water. A harsher environment raises the DC.");
        add(TRAVEL, "Getting lost and navigating", "The navigator makes a Wisdom (Survival) check against a DC set by terrain: easy for roads and clear landmarks, hard for featureless wilderness. On a failure the party goes astray for hours.");
        add(TRAVEL, "Overland difficult terrain", "Difficult terrain (deep snow, heavy undergrowth, swamp) halves the party's travel speed.");

        add(REST, "Short rest", "At least 1 hour of light activity. A creature can spend Hit Dice to heal: roll the die and add its Constitution modifier for each one spent. Some features recharge.");
        add(REST, "Long rest", "At least 8 hours (up to 6 asleep and 2 of light activity); a creature can benefit from only one in 24 hours and needs at least 1 hit point. It regains all hit points and up to half its maximum Hit Dice, and loses one level of exhaustion.\nA rest is interrupted by an hour of strenuous activity (fighting, casting spells, walking).");
        add(REST, "Healing", "Hit points cannot exceed the maximum. A healing spell or potion does nothing for a creature that has died, unless it says otherwise. Temporary hit points do not stack: take the larger and discard the other.");
        add(REST, "Resurrection and returning from death", "Revivify (within 1 minute, 300 gp diamond), Raise Dead (within 10 days, -4 penalty that fades), Reincarnate, Resurrection and True Resurrection are the usual tools. Each has its own components and limits.");

        add(HAZARDS, "Falling", "A creature takes 1d6 bludgeoning damage for every 10 feet it falls, to a maximum of 20d6, and lands prone unless it avoids the damage.");
        add(HAZARDS, "Suffocating and drowning", "A creature can hold its breath for 1 + its Constitution modifier minutes (minimum 30 seconds). When it runs out of breath or is choking it can survive for a number of rounds equal to its Constitution modifier (minimum 1); then it drops to 0 hit points and is dying.");
        add(HAZARDS, "Fire, acid and poison", "Burning creatures take fire damage each turn until the flames are put out (an action). Acid and many poisons call for a saving throw; the DC is usually part of the hazard or the monster's description.");
        add(HAZARDS, "Disease, extreme heat and cold", "Extreme heat: a Constitution save each hour (DC 5, plus 1 for every hour) or gain a level of exhaustion. Extreme cold: a DC 10 Constitution save each hour, otherwise exhaustion. Resistance to the damage type, or suitable gear, avoids it.");
        add(HAZARDS, "Traps", "A trap has a detection DC (Investigation or Perception), a way to disarm it (usually Thieves' Tools or a clever solution), and an effect (a save or an attack against the creature that triggers it). A common guideline is damage of 1d10 for a minor setback and 10d10 for a deadly one.");
    }

    public static List<Entry> all() {
        return List.copyOf(ALL);
    }

    public static List<String> categories() {
        List<String> out = new ArrayList<>();
        for (Entry e : ALL) if (!out.contains(e.category())) out.add(e.category());
        return out;
    }

    /** The entries that mention the text (in the title, the body or the category), in the usual order; all of them for an empty search. */
    public static List<Entry> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry e : ALL) if (q.isEmpty() || e.matches(q)) out.add(e);
        return out;
    }

    /** The rule for a condition (case-insensitive), or null for a custom condition. */
    public static String forCondition(String name) {
        if (name == null) return null;
        for (Entry e : ALL) if (e.category().equals(CONDITIONS) && e.title().equalsIgnoreCase(name.trim())) return e.body();
        return null;
    }
}
