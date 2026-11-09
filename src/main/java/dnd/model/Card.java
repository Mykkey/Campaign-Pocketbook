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
    /** Set on a project card that was made from a "common enemy" in the shared library (see CommonEnemies). */
    public String libraryId;

    public Card copy() {
        Card c = new Card();
        c.copyFrom(this);
        return c;
    }

    /** Overwrites every stat and description with {@code o}'s, keeping this card's id and library link. */
    public void copyFrom(Card o) {
        name = o.name; kind = o.kind; size = o.size; creatureType = o.creatureType; alignment = o.alignment;
        ac = o.ac; acNote = o.acNote; hpMax = o.hpMax; hitDice = o.hitDice; speed = o.speed;
        str = o.str; dex = o.dex; con = o.con; intel = o.intel; wis = o.wis; cha = o.cha;
        proficiency = o.proficiency; initiativeBonus = o.initiativeBonus;
        saves = o.saves; skills = o.skills; vulnerabilities = o.vulnerabilities; resistances = o.resistances;
        immunities = o.immunities; conditionImmunities = o.conditionImmunities;
        senses = o.senses; languages = o.languages; challenge = o.challenge; xp = o.xp;
        portrait = o.portrait; notes = o.notes;
        traits.clear(); attacks.clear(); actions.clear(); reactions.clear(); legendary.clear();
        for (Feature f : o.traits) traits.add(f.copy());
        for (Attack a : o.attacks) attacks.add(a.copy());
        for (Feature f : o.actions) actions.add(f.copy());
        for (Feature f : o.reactions) reactions.add(f.copy());
        for (Feature f : o.legendary) legendary.add(f.copy());
    }
}
