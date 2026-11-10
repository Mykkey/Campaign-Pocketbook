package dnd.model;

/** The kind of grid laid over a world map for measuring, or none. */
public enum WorldGridKind {
    NONE("No grid"),
    SQUARE("Square grid"),
    HEX("Hex grid");

    public final String label;

    WorldGridKind(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
