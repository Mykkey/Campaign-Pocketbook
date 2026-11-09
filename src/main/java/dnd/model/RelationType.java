package dnd.model;

/**
 * How one card relates to another, read as "this card <label> the other card". A relationship only has to be
 * entered on one of the two cards; the other side is worked out (Parent of <-> Child of, and so on).
 */
public enum RelationType {
    PARENT("Parent of", true), CHILD("Child of", true), SPOUSE("Spouse or partner of", true), SIBLING("Sibling of", true),
    MENTOR("Mentor of", false), STUDENT("Student of", false), EMPLOYER("Employer of", false), EMPLOYEE("Works for", false),
    ALLY("Ally of", false), RIVAL("Rival or enemy of", false), OTHER("Connected to", false);

    public final String label;
    /** Family relations make up the family tree; the rest make up the web. */
    public final boolean family;

    RelationType(String label, boolean family) {
        this.label = label;
        this.family = family;
    }

    /** What the other card is to this one: Parent of <-> Child of, Mentor of <-> Student of, Employer of <-> Works for. */
    public RelationType inverse() {
        return switch (this) {
            case PARENT -> CHILD;
            case CHILD -> PARENT;
            case MENTOR -> STUDENT;
            case STUDENT -> MENTOR;
            case EMPLOYER -> EMPLOYEE;
            case EMPLOYEE -> EMPLOYER;
            default -> this;
        };
    }

    /** True when both sides read the same (spouse, sibling, ally, rival, other). */
    public boolean symmetric() {
        return inverse() == this;
    }

    @Override
    public String toString() {
        return label;
    }
}