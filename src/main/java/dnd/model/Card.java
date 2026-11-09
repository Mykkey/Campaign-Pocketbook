package dnd.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A character or creature card: a 5e-style stat block. Stored as cards/&lt;id&gt;.card (JSON).
 * Combatants in a battle copy their HP/AC from a card and look up its attacks.
 */
public class Card {
    public enum Kind { PLAYER, NPC, MONSTER }

    public String id = UUID.randomUUID().toString();
    public String name = "New Card";
    public Kind kind = Kind.MONSTER;

    public String size = "Medium";
    public String creatureType = "humanoid";
    public String alignment = "unaligned";

    public int ac = 10;
    public String acNote = "";
    public int hpMax = 10;
    public String hitDice = "";
    public String speed = "30 ft.";

    public int str = 10, dex = 10, con = 10, intel = 10, wis = 10, cha = 10;
    public int proficiency = 2;
    /** Null means "use the DEX modifier". */
    public Integer initiativeBonus;

    public String saves = "";
    public String skills = "";
    public String vulnerabilities = "";
    public String resistances = "";
    public String immunities = "";
    public String conditionImmunities = "";
    public String senses = "";
    public String languages = "";
    public String challenge = "0";
    public int xp = 0;

    /** File name inside the project's images folder, or null. */
    public String portrait;

    public List<Feature> traits = new ArrayList<>();
    public List<Attack> attacks = new ArrayList<>();
    public List<Feature> actions = new ArrayList<>();
    public List<Feature> reactions = new ArrayList<>();
    public List<Feature> legendary = new ArrayList<>();
    public String notes = "";

    public Card() { }

    /** Ability modifier: floor((score - 10) / 2). */
    public static int mod(int score) {
        return Math.floorDiv(score - 10, 2);
    }

    public int score(int index) {
        switch (index) {
            case 0: return str;
            case 1: return dex;
            case 2: return con;
            case 3: return intel;
            case 4: return wis;
            default: return cha;
        }
    }

    public static final String[] ABILITIES = {"STR", "DEX", "CON", "INT", "WIS", "CHA"};

    /** What this creature adds to its initiative roll. */
    public int initiative() {
        return initiativeBonus != null ? initiativeBonus : mod(dex);
    }

    public static String signed(int v) {
        return (v >= 0 ? "+" : "") + v;
    }

    /** A deep copy with a new id. */
    public Card copy() {
        Card c = new Card();
        c.name = name; c.kind = kind; c.size = size; c.creatureType = creatureType; c.alignment = alignment;
        c.ac = ac; c.acNote = acNote; c.hpMax = hpMax; c.hitDice = hitDice; c.speed = speed;
        c.str = str; c.dex = dex; c.con = con; c.intel = intel; c.wis = wis; c.cha = cha;
        c.proficiency = proficiency; c.initiativeBonus = initiativeBonus;
        c.saves = saves; c.skills = skills; c.vulnerabilities = vulnerabilities; c.resistances = resistances;
        c.immunities = immunities; c.conditionImmunities = conditionImmunities;
        c.senses = senses; c.languages = languages; c.challenge = challenge; c.xp = xp;
        c.portrait = portrait; c.notes = notes;
        for (Feature f : traits) c.traits.add(f.copy());
        for (Attack a : attacks) c.attacks.add(a.copy());
        for (Feature f : actions) c.actions.add(f.copy());
        for (Feature f : reactions) c.reactions.add(f.copy());
        for (Feature f : legendary) c.legendary.add(f.copy());
        return c;
    }
}
