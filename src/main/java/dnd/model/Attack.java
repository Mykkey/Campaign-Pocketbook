package dnd.model;

import dnd.rules.Dice;

/** A weapon or natural attack listed on a card; shown in the battle tracker when it is that creature's turn. */
public class Attack {
    public String name = "Attack";
    public int toHit = 0;
    /** Damage dice, e.g. "1d6+2". */
    public String damage = "1d6";
    public String damageType = "slashing";
    /** "5 ft." for melee or "80/320 ft." for ranged. */
    public String reach = "5 ft.";
    /** Extra effects, e.g. "DC 11 Strength save or be knocked prone". */
    public String text = "";

    public Attack() { }

    public Attack(String name, int toHit, String damage, String damageType, String reach, String text) {
        this.name = name;
        this.toHit = toHit;
        this.damage = damage;
        this.damageType = damageType;
        this.reach = reach;
        this.text = text;
    }

    public Attack copy() {
        return new Attack(name, toHit, damage, damageType, reach, text);
    }

    public boolean ranged() {
        return reach != null && reach.contains("/");
    }

    /** Average damage, or -1 if the dice text isn't valid. */
    public int averageDamage() {
        return Dice.parse(damage).map(Dice::average).orElse(-1);
    }

    /** "+4 to hit, 5 ft., 5 (1d6+2) slashing" */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(toHit >= 0 ? "+" : "").append(toHit).append(" to hit");
        if (reach != null && !reach.isBlank()) sb.append(ranged() ? ", range " : ", reach ").append(reach);
        int avg = averageDamage();
        sb.append(", ");
        if (avg >= 0) sb.append(avg).append(" (").append(damage.trim()).append(")");
        else sb.append(damage == null ? "" : damage.trim());
        if (damageType != null && !damageType.isBlank()) sb.append(" ").append(damageType.trim());
        if (text != null && !text.isBlank()) sb.append(". ").append(text.trim());
        return sb.toString();
    }
}
