package dnd.model;

/** The season a world map is shown in: it changes the colours of the land and water. NONE leaves the art style as it is. */
public enum WorldSeason {
    NONE("Any season"), SPRING("Spring"), SUMMER("Summer"), AUTUMN("Autumn"), WINTER("Winter");

    public final String label;

    WorldSeason(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
