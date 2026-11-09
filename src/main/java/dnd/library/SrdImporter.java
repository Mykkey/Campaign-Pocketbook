package dnd.library;

import com.fasterxml.jackson.databind.JsonNode;
import dnd.model.Attack;
import dnd.model.Card;
import dnd.model.Feature;
import dnd.model.Spell;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the SRD JSON files published by the open 5e-database project (5e-SRD-Monsters.json and
 * 5e-SRD-Spells.json) into cards and spells. Nothing is bundled with the app: the DM downloads the files once and
 * imports them.
 */
public final class SrdImporter {
    private SrdImporter() { }

    public enum Kind { MONSTERS, SPELLS, UNKNOWN }

    /** Which kind of SRD file this is, from its first entry. */
    public static Kind detect(JsonNode root) {
        if (root == null || !root.isArray() || root.isEmpty()) return Kind.UNKNOWN;
        JsonNode first = root.get(0);
        if (first.has("hit_points") && first.has("challenge_rating")) return Kind.MONSTERS;
        if (first.has("school") && first.has("level") && first.has("casting_time")) return Kind.SPELLS;
        return Kind.UNKNOWN;
    }

    // ================================================================ monsters

    public static List<Card> monsters(JsonNode array) {
        List<Card> out = new ArrayList<>();
        for (JsonNode m : array) {
            if (!m.hasNonNull("name")) continue;
            out.add(monster(m));
        }
        return out;
    }

    static Card monster(JsonNode m) {
        Card c = new Card();
        String index = text(m, "index");
        c.id = "srd-" + (index.isBlank() ? text(m, "name").toLowerCase().replaceAll("[^a-z0-9]+", "-") : index);
        c.name = text(m, "name");
        c.kind = Card.Kind.MONSTER;
        c.size = text(m, "size");
        String type = text(m, "type");
        String sub = text(m, "subtype");
        c.creatureType = sub.isBlank() ? type : type + " (" + sub + ")";
        c.alignment = text(m, "alignment");

        JsonNode ac = m.get("armor_class");
        if (ac != null && ac.isArray() && !ac.isEmpty()) {
            c.ac = ac.get(0).path("value").asInt(10);
            String t = ac.get(0).path("type").asText("");
            c.acNote = t.equals("natural") ? "natural armor" : t.equals("armor") ? "armor" : t.equals("dex") ? "" : t;
        } else if (ac != null && ac.isNumber()) {
            c.ac = ac.asInt(10);
        }
        c.hpMax = Math.max(1, m.path("hit_points").asInt(1));
        c.hitDice = text(m, "hit_dice").isBlank() ? text(m, "hit_points_roll") : text(m, "hit_dice");
        c.speed = speed(m.get("speed"));

        c.str = m.path("strength").asInt(10);
        c.dex = m.path("dexterity").asInt(10);
        c.con = m.path("constitution").asInt(10);
        c.intel = m.path("intelligence").asInt(10);
        c.wis = m.path("wisdom").asInt(10);
        c.cha = m.path("charisma").asInt(10);

        List<String> saves = new ArrayList<>(), skills = new ArrayList<>();
        for (JsonNode p : m.path("proficiencies")) {
            String n = p.path("proficiency").path("name").asText("");
            String bonus = signed(p.path("value").asInt(0));
            if (n.startsWith("Saving Throw: ")) saves.add(n.substring("Saving Throw: ".length()) + " " + bonus);
            else if (n.startsWith("Skill: ")) skills.add(n.substring("Skill: ".length()) + " " + bonus);
        }
        c.saves = String.join(", ", saves);
        c.skills = String.join(", ", skills);
        c.vulnerabilities = joinStrings(m.get("damage_vulnerabilities"));
        c.resistances = joinStrings(m.get("damage_resistances"));
        c.immunities = joinStrings(m.get("damage_immunities"));
        List<String> conds = new ArrayList<>();
        for (JsonNode ci : m.path("condition_immunities")) conds.add(ci.isTextual() ? ci.asText() : ci.path("name").asText(""));
        c.conditionImmunities = String.join(", ", conds);
        c.senses = senses(m.get("senses"));
        c.languages = text(m, "languages");
        c.challenge = challenge(m.path("challenge_rating").asDouble(0));
        c.xp = m.path("xp").asInt(0);
        c.proficiency = m.path("proficiency_bonus").asInt(2);

        for (JsonNode a : m.path("special_abilities")) c.traits.add(feature(a));
        for (JsonNode a : m.path("actions")) {
            Attack atk = attack(a);
            if (atk != null) c.attacks.add(atk);
            else c.actions.add(feature(a));
        }
        for (JsonNode a : m.path("reactions")) c.reactions.add(feature(a));
        for (JsonNode a : m.path("legendary_actions")) c.legendary.add(feature(a));
        return c;
    }

    private static final Pattern REACH = Pattern.compile("reach (\\d+ ft\\.?)");
    private static final Pattern RANGE = Pattern.compile("range (\\d+/\\d+ ft\\.?)");

    /** An action with an attack bonus and damage dice becomes an {@link Attack}; anything else is null. */
    static Attack attack(JsonNode a) {
        if (!a.has("attack_bonus") || !a.path("damage").isArray()) return null;
        List<String> dice = new ArrayList<>(), types = new ArrayList<>();
        for (JsonNode d : a.get("damage")) {
            if (!d.hasNonNull("damage_dice")) continue; // alternatives ("choose one") are skipped
            dice.add(d.get("damage_dice").asText().replaceAll("\\s+", ""));
            String t = d.path("damage_type").path("name").asText("");
            if (!t.isBlank()) types.add(t.toLowerCase());
        }
        if (dice.isEmpty()) return null;
        String desc = a.path("desc").asText("");
        String reach = "5 ft.";
        Matcher r = REACH.matcher(desc);
        Matcher g = RANGE.matcher(desc);
        if (g.find()) reach = g.group(1).endsWith(".") ? g.group(1) : g.group(1) + ".";
        else if (r.find()) reach = r.group(1).endsWith(".") ? r.group(1) : r.group(1) + ".";
        int end = desc.lastIndexOf(" damage.");
        String extra = end >= 0 ? desc.substring(end + " damage.".length()).trim() : "";
        return new Attack(a.path("name").asText("Attack"), a.path("attack_bonus").asInt(0), String.join("+", dice),
                String.join(" plus ", types), reach, extra);
    }

    private static Feature feature(JsonNode a) {
        String name = a.path("name").asText("");
        JsonNode u = a.get("usage");
        if (u != null && u.isObject()) {
            String type = u.path("type").asText("");
            if (type.equals("per day")) name += " (" + u.path("times").asInt() + "/Day)";
            else if (type.equals("recharge on roll")) {
                int min = u.path("min_value").asInt(6);
                name += min >= 6 ? " (Recharge 6)" : " (Recharge " + min + "-6)";
            } else if (type.equals("recharge after rest")) name += " (Recharges after a Short or Long Rest)";
        }
        return new Feature(name, a.path("desc").asText(""));
    }

    private static String speed(JsonNode s) {
        if (s == null || !s.isObject()) return s != null && s.isTextual() ? s.asText() : "30 ft.";
        List<String> parts = new ArrayList<>();
        if (s.hasNonNull("walk")) parts.add(s.get("walk").asText());
        Iterator<Map.Entry<String, JsonNode>> it = s.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (e.getKey().equals("walk") || !e.getValue().isTextual()) continue;
            parts.add(e.getKey() + " " + e.getValue().asText());
        }
        if (s.path("hover").asBoolean(false)) {
            for (int i = 0; i < parts.size(); i++) if (parts.get(i).startsWith("fly ")) parts.set(i, parts.get(i) + " (hover)");
        }
        return String.join(", ", parts);
    }

    private static String senses(JsonNode s) {
        if (s == null || !s.isObject()) return s != null && s.isTextual() ? s.asText() : "";
        List<String> parts = new ArrayList<>();
        String passive = null;
        Iterator<Map.Entry<String, JsonNode>> it = s.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            if (e.getKey().equals("passive_perception")) passive = "passive Perception " + e.getValue().asText();
            else parts.add(e.getKey().replace('_', ' ') + " " + e.getValue().asText());
        }
        if (passive != null) parts.add(passive);
        return String.join(", ", parts);
    }

    /** 0.125 -> "1/8", 0.25 -> "1/4", 0.5 -> "1/2", 5.0 -> "5". */
    public static String challenge(double cr) {
        if (Math.abs(cr - 0.125) < 1e-6) return "1/8";
        if (Math.abs(cr - 0.25) < 1e-6) return "1/4";
        if (Math.abs(cr - 0.5) < 1e-6) return "1/2";
        return String.valueOf((long) cr);
    }

    // ================================================================ spells

    public static List<Spell> spells(JsonNode array) {
        List<Spell> out = new ArrayList<>();
        for (JsonNode s : array) {
            if (!s.hasNonNull("name")) continue;
            Spell sp = new Spell();
            sp.id = "srd-" + text(s, "index");
            sp.name = text(s, "name");
            sp.level = s.path("level").asInt(0);
            sp.school = s.path("school").isObject() ? s.path("school").path("name").asText("") : text(s, "school");
            sp.castingTime = text(s, "casting_time");
            sp.range = text(s, "range");
            List<String> comps = new ArrayList<>();
            for (JsonNode c : s.path("components")) comps.add(c.asText());
            sp.components = String.join(", ", comps);
            sp.material = text(s, "material");
            sp.duration = text(s, "duration");
            sp.concentration = s.path("concentration").asBoolean(false);
            sp.ritual = s.path("ritual").asBoolean(false);
            sp.description = paragraphs(s.get("desc"));
            sp.higherLevel = paragraphs(s.get("higher_level"));
            for (JsonNode c : s.path("classes")) sp.classes.add(c.isTextual() ? c.asText() : c.path("name").asText(""));
            out.add(sp);
        }
        return out;
    }

    // ================================================================ helpers

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? "" : v.asText("");
    }

    private static String paragraphs(JsonNode n) {
        if (n == null || n.isNull()) return "";
        if (n.isTextual()) return n.asText();
        List<String> parts = new ArrayList<>();
        for (JsonNode p : n) parts.add(p.asText());
        return String.join("\n\n", parts);
    }

    private static String joinStrings(JsonNode n) {
        if (n == null || !n.isArray()) return "";
        List<String> parts = new ArrayList<>();
        for (JsonNode p : n) if (!p.asText().isBlank()) parts.add(p.asText());
        return String.join("; ", parts);
    }

    private static String signed(int v) {
        return v >= 0 ? "+" + v : String.valueOf(v);
    }
}
