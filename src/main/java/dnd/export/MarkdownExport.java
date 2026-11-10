package dnd.export;

import dnd.model.Attack;
import dnd.model.Card;
import dnd.model.Encounter;
import dnd.model.Feature;
import dnd.model.MagicItem;
import dnd.model.Quest;
import dnd.model.Session;
import dnd.model.Spell;

import java.util.List;
import java.util.function.Function;

/**
 * Writes the text items of a campaign as Markdown: stat blocks, spells, magic items, quests, session logs and encounters.
 * Pure text, so it is unit tested; the result can be pasted into a wiki, a notes app or a document.
 */
public final class MarkdownExport {
    private MarkdownExport() { }

    private static void line(StringBuilder sb, String s) {
        sb.append(s).append('\n');
    }

    private static boolean has(String s) {
        return s != null && !s.isBlank();
    }

    private static void field(StringBuilder sb, String label, String value) {
        if (has(value)) line(sb, "**" + label + "** " + value.trim() + "  ");
    }

    private static String signed(int n) {
        return (n >= 0 ? "+" : "") + n;
    }

    private static void features(StringBuilder sb, String heading, List<Feature> list) {
        if (list.isEmpty()) return;
        line(sb, "");
        line(sb, "## " + heading);
        line(sb, "");
        for (Feature f : list) {
            if (!has(f.name) && !has(f.text)) continue;
            line(sb, (has(f.name) ? "***" + f.name.trim().replaceAll("\\.$", "") + ".*** " : "") + (f.text == null ? "" : f.text.trim()));
            line(sb, "");
        }
    }

    /** A creature or character as a stat block. */
    public static String card(Card c) {
        StringBuilder sb = new StringBuilder();
        line(sb, "# " + c.name);
        line(sb, "*" + c.size + " " + c.creatureType + ", " + c.alignment + "*");
        line(sb, "");
        field(sb, "Armor Class", c.ac + (has(c.acNote) ? " (" + c.acNote.trim() + ")" : ""));
        field(sb, "Hit Points", c.hpMax + (has(c.hitDice) ? " (" + c.hitDice.trim() + ")" : ""));
        field(sb, "Speed", c.speed);
        line(sb, "");
        line(sb, "| " + String.join(" | ", Card.ABILITIES) + " |");
        line(sb, "|" + "---|".repeat(6));
        StringBuilder row = new StringBuilder("|");
        for (int i = 0; i < 6; i++) row.append(' ').append(c.score(i)).append(" (").append(signed(Card.mod(c.score(i)))).append(") |");
        line(sb, row.toString());
        line(sb, "");
        field(sb, "Saving Throws", c.saves);
        field(sb, "Skills", c.skills);
        field(sb, "Damage Vulnerabilities", c.vulnerabilities);
        field(sb, "Damage Resistances", c.resistances);
        field(sb, "Damage Immunities", c.immunities);
        field(sb, "Condition Immunities", c.conditionImmunities);
        field(sb, "Senses", c.senses);
        field(sb, "Languages", c.languages);
        field(sb, "Challenge", (has(c.challenge) ? c.challenge.trim() : "0") + " (" + c.xp + " XP)");
        field(sb, "Proficiency Bonus", signed(c.proficiency));
        features(sb, "Traits", c.traits);
        if (!c.attacks.isEmpty() || !c.actions.isEmpty()) {
            line(sb, "");
            line(sb, "## Actions");
            line(sb, "");
            for (Attack a : c.attacks) {
                line(sb, "***" + a.name + ".*** " + a.summary() + (has(a.text) ? " " + a.text.trim() : ""));
                line(sb, "");
            }
            for (Feature f : c.actions) {
                if (!has(f.name) && !has(f.text)) continue;
                line(sb, (has(f.name) ? "***" + f.name.trim().replaceAll("\\.$", "") + ".*** " : "") + (f.text == null ? "" : f.text.trim()));
                line(sb, "");
            }
        }
        features(sb, "Reactions", c.reactions);
        features(sb, "Legendary Actions", c.legendary);
        if (has(c.notes)) {
            line(sb, "");
            line(sb, "## Notes");
            line(sb, "");
            line(sb, c.notes.trim());
        }
        return sb.toString().trim() + "\n";
    }

    public static String spell(Spell s) {
        StringBuilder sb = new StringBuilder();
        line(sb, "# " + s.name);
        String lvl = s.level <= 0 ? s.school + " cantrip" : s.levelLabel() + " " + s.school.toLowerCase();
        if (s.ritual) lvl += " (ritual)";
        line(sb, "*" + lvl.trim() + (s.classes.isEmpty() ? "" : " (" + String.join(", ", s.classes) + ")") + "*");
        line(sb, "");
        field(sb, "Casting Time", s.castingTime);
        field(sb, "Range", s.range);
        field(sb, "Components", has(s.components) ? s.components.trim() + (has(s.material) ? " (" + s.material.trim().replaceAll("\\.$", "") + ")" : "") : "");
        field(sb, "Duration", (s.concentration && has(s.duration) && !s.duration.toLowerCase().contains("concentration") ? "Concentration, " : "") + (s.duration == null ? "" : s.duration));
        if (has(s.description)) {
            line(sb, "");
            line(sb, s.description.trim());
        }
        if (has(s.higherLevel)) {
            line(sb, "");
            line(sb, "***At higher levels.*** " + s.higherLevel.trim());
        }
        if (has(s.notes)) {
            line(sb, "");
            line(sb, "## Notes");
            line(sb, "");
            line(sb, s.notes.trim());
        }
        return sb.toString().trim() + "\n";
    }

    public static String magicItem(MagicItem m) {
        StringBuilder sb = new StringBuilder();
        line(sb, "# " + m.name);
        line(sb, "*" + m.subtitle() + "*");
        if (has(m.description)) {
            line(sb, "");
            line(sb, m.description.trim());
        }
        line(sb, "");
        if (m.charges > 0) field(sb, "Charges", m.charges + (has(m.recharge) ? " (recharges " + m.recharge.toLowerCase(java.util.Locale.ROOT) + ")" : ""));
        field(sb, "Curse", m.curse);
        field(sb, "Value", has(m.value) ? m.value.trim() + " gp" : "");
        if (has(m.notes)) {
            line(sb, "");
            line(sb, "## Notes");
            line(sb, "");
            line(sb, m.notes.trim());
        }
        return sb.toString().trim() + "\n";
    }

    /** @param nameOf the name of the item with an id (for the giver and the related items), or null if there is no such item */
    public static String quest(Quest q, Function<String, String> nameOf) {
        StringBuilder sb = new StringBuilder();
        line(sb, "# " + q.name);
        line(sb, "*" + q.status.label + "*");
        line(sb, "");
        field(sb, "Given by", q.giverName(nameOf));
        field(sb, "Reward", q.reward);
        if (has(q.summary)) {
            line(sb, "");
            line(sb, q.summary.trim());
        }
        if (!q.steps.isEmpty()) {
            line(sb, "");
            line(sb, "## Steps");
            line(sb, "");
            for (Quest.Step s : q.steps) line(sb, "- [" + (s.done ? "x" : " ") + "] " + s.text.trim());
        }
        related(sb, q.related, nameOf);
        if (has(q.notes)) {
            line(sb, "");
            line(sb, "## Notes");
            line(sb, "");
            line(sb, q.notes.trim());
        }
        return sb.toString().trim() + "\n";
    }

    public static String session(Session s, Function<String, String> nameOf) {
        StringBuilder sb = new StringBuilder();
        line(sb, "# " + s.label());
        StringBuilder when = new StringBuilder();
        if (has(s.realDate)) when.append("Played ").append(s.realDate.trim());
        if (has(s.inWorldDate)) when.append(when.length() > 0 ? "; in the world: " : "In the world: ").append(s.inWorldDate.trim());
        if (s.campaignDay > 0) when.append(when.length() > 0 ? "; " : "").append("campaign day ").append(s.campaignDay);
        if (when.length() > 0) line(sb, "*" + when + "*");
        if (has(s.recap)) {
            line(sb, "");
            line(sb, "## Last time on...");
            line(sb, "");
            line(sb, s.recap.trim());
        }
        if (has(s.summary)) {
            line(sb, "");
            line(sb, "## What happened");
            line(sb, "");
            line(sb, s.summary.trim());
        }
        if (has(s.nextSteps)) {
            line(sb, "");
            line(sb, "## Next steps");
            line(sb, "");
            line(sb, s.nextSteps.trim());
        }
        related(sb, s.related, nameOf);
        return sb.toString().trim() + "\n";
    }

    /** @param creatureOf the name of the creature of an entry (a card or a common enemy), or null if it is missing */
    public static String encounter(Encounter e, Function<Encounter.Entry, String> creatureOf) {
        StringBuilder sb = new StringBuilder();
        line(sb, "# " + e.name);
        line(sb, "");
        line(sb, "## Creatures");
        line(sb, "");
        if (e.entries.isEmpty()) line(sb, "No creatures yet.");
        for (Encounter.Entry en : e.entries) {
            String n = creatureOf.apply(en);
            line(sb, "- " + en.count + " x " + (n == null ? "(missing creature)" : n) + " (" + en.side.name().toLowerCase(java.util.Locale.ROOT) + (en.group ? ", acting as a group" : "") + ")");
        }
        if (has(e.notes)) {
            line(sb, "");
            line(sb, "## Notes");
            line(sb, "");
            line(sb, e.notes.trim());
        }
        if (has(e.loot)) {
            line(sb, "");
            line(sb, "## Treasure");
            line(sb, "");
            line(sb, e.loot.trim().replace("\n", "  \n"));
        }
        return sb.toString().trim() + "\n";
    }

    private static void related(StringBuilder sb, List<String> ids, Function<String, String> nameOf) {
        StringBuilder names = new StringBuilder();
        for (String id : ids) {
            String n = nameOf.apply(id);
            if (n != null && !n.isBlank()) names.append("- ").append(n.trim()).append('\n');
        }
        if (names.length() == 0) return;
        line(sb, "");
        line(sb, "## Related");
        line(sb, "");
        sb.append(names);
    }

    /** A file name for an exported item: the name with anything a file system dislikes turned into a dash. */
    public static String fileName(String itemName, String extension) {
        String n = itemName == null || itemName.isBlank() ? "Untitled" : itemName.trim();
        n = n.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "-").replaceAll("\\s+", " ");
        if (n.length() > 80) n = n.substring(0, 80).trim();
        return n + extension;
    }
}
