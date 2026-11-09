package dnd.model;

import java.util.ArrayList;
import java.util.List;

/** A spell from an imported SRD file (see SrdLibrary). */
public class Spell {
    public String id = "";
    public String name = "";
    /** 0 = cantrip. */
    public int level;
    public String school = "";
    public String castingTime = "";
    public String range = "";
    public String components = "";
    public String material = "";
    public String duration = "";
    public boolean concentration;
    public boolean ritual;
    public String description = "";
    public String higherLevel = "";
    public List<String> classes = new ArrayList<>();
    /** The DM's own notes (not part of the printed spell). */
    public String notes = "";
    /** Id of the SRD library spell this card was made from, or null for a homebrew spell. */
    public String libraryId;

    public Spell() { }

    /** A deep copy with the same id (callers give it a new one if it is a new item). */
    public Spell copy() {
        Spell s = new Spell();
        s.id = id; s.name = name; s.level = level; s.school = school; s.castingTime = castingTime; s.range = range;
        s.components = components; s.material = material; s.duration = duration; s.concentration = concentration;
        s.ritual = ritual; s.description = description; s.higherLevel = higherLevel;
        s.classes = new ArrayList<>(classes); s.notes = notes; s.libraryId = libraryId;
        return s;
    }

    /** "Cantrip", "1st-level", "2nd-level"... */
    public String levelLabel() {
        if (level <= 0) return "Cantrip";
        String suffix = level == 1 ? "st" : level == 2 ? "nd" : level == 3 ? "rd" : "th";
        return level + suffix + "-level";
    }

    /** The spell as readable text, for the preview, the clipboard and a whiteboard note. */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('\n');
        String lvl = level <= 0 ? school + " cantrip" : levelLabel() + " " + school.toLowerCase();
        if (ritual) lvl += " (ritual)";
        sb.append(lvl.trim());
        if (!classes.isEmpty()) sb.append("  -  ").append(String.join(", ", classes));
        sb.append('\n');
        sb.append("Casting time: ").append(castingTime).append('\n');
        sb.append("Range: ").append(range).append('\n');
        sb.append("Components: ").append(components);
        if (material != null && !material.isBlank()) sb.append(" (").append(material.trim().replaceAll("\\.$", "")).append(')');
        sb.append('\n');
        sb.append("Duration: ").append(concentration && !duration.toLowerCase().contains("concentration") ? "Concentration, " : "")
                .append(duration).append('\n');
        if (description != null && !description.isBlank()) sb.append('\n').append(description.trim()).append('\n');
        if (higherLevel != null && !higherLevel.isBlank()) sb.append("\nAt higher levels. ").append(higherLevel.trim()).append('\n');
        return sb.toString().trim();
    }
}
