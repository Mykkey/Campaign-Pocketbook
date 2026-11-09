package dnd.npc;

import dnd.names.NameData.Race;
import dnd.names.NameGenerator.Gender;
import dnd.npc.NpcData.AgeGroup;
import dnd.npc.NpcData.Attitude;
import dnd.npc.NpcData.Profession;

import java.util.ArrayList;
import java.util.List;

/** One rolled person. The fields are plain so a single detail can be re-rolled without touching the rest. */
public final class Npc {
    public String name = "";
    public Race race = Race.HUMAN;
    /** MASCULINE or FEMININE; ANY for races whose names do not depend on gender. */
    public Gender gender = Gender.ANY;
    public AgeGroup ageGroup = AgeGroup.ADULT;
    public int age = 30;
    public Profession profession;
    public Attitude attitude = Attitude.NEUTRAL;
    /** How they react when the party approaches, and how much they will share. */
    public String opening = "";
    public List<String> appearance = new ArrayList<>();
    public List<String> personality = new ArrayList<>();
    public String mannerism = "";
    public String voice = "";
    public String wants = "";
    public String secret = "";
    /** Things this person might know or offer. */
    public List<String> knows = new ArrayList<>();

    /** "Female dwarf innkeeper, middle-aged (140)". */
    public String summary() {
        String sex = gender == Gender.FEMININE ? "Female " : gender == Gender.MASCULINE ? "Male " : "";
        String who = sex + race.display.toLowerCase() + " " + (profession == null ? "" : profession.name().toLowerCase());
        who = Character.toUpperCase(who.charAt(0)) + who.substring(1);
        return who.trim() + ", " + ageGroup.label.toLowerCase() + " (" + age + ")";
    }

    /** Plain text for the clipboard, a whiteboard note or a card's notes. */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('\n');
        sb.append(summary()).append('\n');
        sb.append("Attitude: ").append(attitude.label).append(". ").append(opening).append('\n');
        sb.append("Appearance: ").append(String.join("; ", appearance)).append('\n');
        sb.append("Personality: ").append(String.join("; ", personality)).append('\n');
        sb.append("Mannerism: ").append(mannerism).append('\n');
        sb.append("Voice: ").append(voice).append('\n');
        sb.append("Wants: ").append(wants).append('\n');
        sb.append("Secret: ").append(secret).append('\n');
        sb.append("Might know: ").append(String.join("; ", knows));
        return sb.toString();
    }

    public Npc copy() {
        Npc n = new Npc();
        n.name = name; n.race = race; n.gender = gender; n.ageGroup = ageGroup; n.age = age;
        n.profession = profession; n.attitude = attitude; n.opening = opening;
        n.appearance = new ArrayList<>(appearance);
        n.personality = new ArrayList<>(personality);
        n.mannerism = mannerism; n.voice = voice; n.wants = wants; n.secret = secret;
        n.knows = new ArrayList<>(knows);
        return n;
    }
}