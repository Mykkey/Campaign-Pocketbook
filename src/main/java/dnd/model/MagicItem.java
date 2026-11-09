package dnd.model;

import java.util.List;

/** A magic item card: a homebrew or loot item with its rarity, attunement and what it does. */
public class MagicItem extends ProjectEntry {
    public static final List<String> TYPES = List.of("Wondrous item", "Weapon", "Armor", "Shield", "Potion", "Scroll",
            "Ring", "Rod", "Staff", "Wand", "Ammunition", "Other");
    public static final List<String> RARITIES = List.of("Common", "Uncommon", "Rare", "Very rare", "Legendary", "Artifact");
    public static final List<String> RECHARGES = List.of("", "Dawn", "Dusk", "Daily", "Long rest", "Short rest", "Weekly", "Never");

    public String type = "Wondrous item";
    public String rarity = "Uncommon";
    public boolean attunement;
    /** What attunement needs ("by a spellcaster", "by a cleric or paladin"), or blank. */
    public String attunementNote = "";
    public String description = "";
    /** 0 = the item has no charges. */
    public int charges;
    public String recharge = "";
    /** A curse or drawback, or blank. */
    public String curse = "";
    /** Worth in gold pieces, as text ("500", "2,500-5,000"), or blank. */
    public String value = "";
    /** The DM's own notes (not part of the printed card). */
    public String notes = "";

    public MagicItem() { }

    /** A deep copy with the same id (callers give it a new one if it is a new item). */
    public MagicItem copy() {
        MagicItem m = new MagicItem();
        m.id = id; m.name = name; m.type = type; m.rarity = rarity; m.attunement = attunement;
        m.attunementNote = attunementNote; m.description = description; m.charges = charges; m.recharge = recharge;
        m.curse = curse; m.value = value; m.notes = notes;
        return m;
    }

    /** "Wondrous item, rare (requires attunement by a wizard)". */
    public String subtitle() {
        StringBuilder sb = new StringBuilder(type == null || type.isBlank() ? "Item" : type);
        if (rarity != null && !rarity.isBlank()) sb.append(", ").append(rarity.toLowerCase(java.util.Locale.ROOT));
        if (attunement) {
            sb.append(" (requires attunement");
            if (attunementNote != null && !attunementNote.isBlank()) sb.append(' ').append(attunementNote.trim());
            sb.append(')');
        }
        return sb.toString();
    }

    /** The item as readable text, for the preview and the clipboard. */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('\n').append(subtitle()).append('\n');
        if (description != null && !description.isBlank()) sb.append('\n').append(description.trim()).append('\n');
        if (charges > 0) {
            sb.append("\nCharges: ").append(charges);
            if (recharge != null && !recharge.isBlank()) sb.append(" (recharges ").append(recharge.toLowerCase(java.util.Locale.ROOT)).append(')');
            sb.append('\n');
        }
        if (curse != null && !curse.isBlank()) sb.append("\nCurse: ").append(curse.trim()).append('\n');
        if (value != null && !value.isBlank()) sb.append("\nValue: ").append(value.trim()).append(" gp\n");
        return sb.toString().trim();
    }
}
