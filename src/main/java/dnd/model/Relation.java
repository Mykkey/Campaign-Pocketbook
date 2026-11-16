package dnd.model;

/** One line in a card's Relationships list: this card {@link #type} the card {@link #targetCardId}. */
public class Relation {
    public String targetCardId;
    public RelationType type = RelationType.OTHER;
    /** For a custom link (type CUSTOM): the id of its {@link LinkType} in the project. */
    public String linkTypeId;
    /** Optional detail, e.g. "since the war" or "owes him money". */
    public String note = "";

    public Relation() { }

    public Relation(String targetCardId, RelationType type, String note) {
        this.targetCardId = targetCardId;
        this.type = type;
        this.note = note == null ? "" : note;
    }

    public Relation copy() {
        Relation r = new Relation(targetCardId, type, note);
        r.linkTypeId = linkTypeId;
        return r;
    }
}