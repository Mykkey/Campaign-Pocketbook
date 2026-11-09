package dnd.model;

/** Something saved as its own file in a project and listed in the project tree (magic items, quests, sessions). */
public abstract class ProjectEntry {
    /** The same id as its project tree node. */
    public String id = "";
    public String name = "";
}
