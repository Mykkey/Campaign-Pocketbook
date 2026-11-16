package dnd.model;

/**
 * How one card relates to another, read as "this card <label> the other card". A relationship only has to be
 * entered on one of the two cards; the other side is worked out (Parent of <-> Child of, and so on).
 *
 * <p>Types that read one way have a partner for the other side. Of each pair, the "forward" one (the one that points from the
 * senior, the actor or the giver) is what the graph keeps; the "reverse" one is turned round when it is read. {@link #CUSTOM}
 * stands for a link the person made up themselves (see {@link LinkType}).
 */
public enum RelationType {
    // ---- family
    PARENT("Parent of", "parent of", true, false, "#8a94a3"), CHILD("Child of", "child of", true, true, "#8a94a3"),
    SPOUSE("Spouse or partner of", "partner", true, false, "#d6577f"), SIBLING("Sibling of", "sibling", true, false, "#8a94a3"),
    // ---- teaching and work
    MENTOR("Mentor of", "mentor", false, false, "#8b5cf6"), STUDENT("Student of", "student", false, true, "#8b5cf6"),
    EMPLOYER("Employer of", "employer", false, false, "#1aa3a3"), EMPLOYEE("Works for", "employee", false, true, "#1aa3a3"),
    // ---- rule, service and creation
    RULER("Rules over", "rules", false, false, "#b45309"), SUBJECT("Is ruled by", "ruled by", false, true, "#b45309"),
    CREATOR("Created", "created", false, false, "#0e7490"), CREATION("Was created by", "created by", false, true, "#0e7490"),
    PATRON("Is the patron of", "patron of", false, false, "#a16207"), PROTEGE("Is the protege of", "protege of", false, true, "#a16207"),
    DEITY("Is worshipped by", "worshipped by", false, false, "#d4a017"), WORSHIPPER("Worships", "worships", false, true, "#d4a017"),
    // ---- feelings and deeds
    ALLY("Ally of", "ally", false, false, "#2f9e5f"), FRIEND("Friend of", "friend", false, false, "#3fae9a"),
    LOVER("Romantic interest of", "romance", false, false, "#e0567f"),
    RIVAL("Rival or enemy of", "rival", false, false, "#d64545"),
    PROTECTOR("Protects", "protects", false, false, "#2563eb"), PROTECTED("Is protected by", "protected by", false, true, "#2563eb"),
    HUNTER("Hunts", "hunts", false, false, "#9f1239"), HUNTED("Is hunted by", "hunted by", false, true, "#9f1239"),
    KILLER("Killed", "killed", false, false, "#7f1d1d"), VICTIM("Was killed by", "killed by", false, true, "#7f1d1d"),
    BETRAYER("Betrayed", "betrayed", false, false, "#be185d"), BETRAYED("Was betrayed by", "betrayed by", false, true, "#be185d"),
    CREDITOR("Is owed a debt by", "is owed by", false, false, "#ca8a04"), DEBTOR("Owes a debt to", "owes", false, true, "#ca8a04"),
    FEARED("Is feared by", "feared by", false, false, "#6d28d9"), FEARS("Fears", "fears", false, true, "#6d28d9"),
    // ---- anything else
    OTHER("Connected to", "connected", false, false, "#8a94a3"),
    /** A link the person made up (its name and colour come from the project's {@link LinkType}). */
    CUSTOM("Custom link", "custom", false, false, "#8a94a3");

    public final String label;
    /** A short word for it on a diagram ("rival", "worshipped by"). */
    public final String shortLabel;
    /** Family relations make up the family tree; the rest make up the web. */
    public final boolean family;
    /** True for the "other side" of a pair: it is turned round to its partner when the relationships are put together. */
    public final boolean reverse;
    /** The colour of its line on a diagram ("#RRGGBB"). */
    public final String colour;

    RelationType(String label, String shortLabel, boolean family, boolean reverse, String colour) {
        this.label = label;
        this.shortLabel = shortLabel;
        this.family = family;
        this.reverse = reverse;
        this.colour = colour;
    }

    /** What the other card is to this one: Parent of <-> Child of, Mentor of <-> Student of, Rules over <-> Is ruled by, and so on. */
    public RelationType inverse() {
        return switch (this) {
            case PARENT -> CHILD;
            case CHILD -> PARENT;
            case MENTOR -> STUDENT;
            case STUDENT -> MENTOR;
            case EMPLOYER -> EMPLOYEE;
            case EMPLOYEE -> EMPLOYER;
            case RULER -> SUBJECT;
            case SUBJECT -> RULER;
            case CREATOR -> CREATION;
            case CREATION -> CREATOR;
            case PATRON -> PROTEGE;
            case PROTEGE -> PATRON;
            case DEITY -> WORSHIPPER;
            case WORSHIPPER -> DEITY;
            case PROTECTOR -> PROTECTED;
            case PROTECTED -> PROTECTOR;
            case HUNTER -> HUNTED;
            case HUNTED -> HUNTER;
            case KILLER -> VICTIM;
            case VICTIM -> KILLER;
            case BETRAYER -> BETRAYED;
            case BETRAYED -> BETRAYER;
            case CREDITOR -> DEBTOR;
            case DEBTOR -> CREDITOR;
            case FEARED -> FEARS;
            case FEARS -> FEARED;
            default -> this;
        };
    }

    /** True when both sides read the same (spouse, sibling, ally, rival, friend, other). */
    public boolean symmetric() {
        return inverse() == this;
    }

    @Override
    public String toString() {
        return label;
    }
}
