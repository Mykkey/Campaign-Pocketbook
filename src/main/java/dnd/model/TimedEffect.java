package dnd.model;

/** Something on a creature that lasts a number of its own turns (a spell, a poison, "marked by the ranger"). */
public class TimedEffect {
    public String name = "";
    /** Turns of the creature left; it ends when this reaches 0. */
    public int turns = 1;

    public TimedEffect() { }

    public TimedEffect(String name, int turns) {
        this.name = name;
        this.turns = turns;
    }
}
