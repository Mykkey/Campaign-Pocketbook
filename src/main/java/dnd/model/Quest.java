package dnd.model;

import java.util.ArrayList;
import java.util.List;

/** A quest or plot thread: who gave it, what it pays, the steps and clues, and the items it touches. */
public class Quest extends ProjectEntry {
    public enum Status {
        OPEN("Open"), ACTIVE("Active"), ON_HOLD("On hold"), DONE("Done"), FAILED("Failed");

        public final String label;

        Status(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** One step or clue; ticked off when the party has done it or found it. */
    public static class Step {
        public String text = "";
        public boolean done;

        public Step() { }

        public Step(String text, boolean done) {
            this.text = text;
            this.done = done;
        }
    }

    public Status status = Status.OPEN;
    /** Who gave the quest, as typed (e.g. "Mira the innkeeper"); also the last known name of the chosen character. */
    public String giver = "";
    /** Id of the character card that gave the quest, or null when the giver is just a typed name. */
    public String giverId;
    public String reward = "";
    public String summary = "";
    public List<Step> steps = new ArrayList<>();
    /** Ids of the cards, maps, whiteboards and items this quest involves. */
    public List<String> related = new ArrayList<>();
    /** The DM's own notes. */
    public String notes = "";

    public Quest() { }

    /**
     * The giver to show: the chosen character's current name (so a renamed card updates here), falling back to the
     * typed name if the card was deleted or none was chosen.
     *
     * @param nameOf the name of the item with an id, or null if there is no such item
     */
    public String giverName(java.util.function.Function<String, String> nameOf) {
        if (giverId != null) {
            String n = nameOf.apply(giverId);
            if (n != null && !n.isBlank()) return n;
        }
        return giver == null ? "" : giver;
    }

    public int stepsDone() {
        int n = 0;
        for (Step s : steps) if (s.done) n++;
        return n;
    }

    /** "2 of 5 steps", or "" when there are none. */
    public String progressText() {
        return steps.isEmpty() ? "" : stepsDone() + " of " + steps.size() + (steps.size() == 1 ? " step" : " steps");
    }

    public boolean isFinished() {
        return status == Status.DONE || status == Status.FAILED;
    }

    public Quest copy() {
        Quest q = new Quest();
        q.id = id; q.name = name; q.status = status; q.giver = giver; q.giverId = giverId; q.reward = reward; q.summary = summary;
        for (Step s : steps) q.steps.add(new Step(s.text, s.done));
        q.related = new ArrayList<>(related);
        q.notes = notes;
        return q;
    }
}
