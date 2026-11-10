package dnd.model;

/** The art style of a world map: how the land and sea are coloured and how its symbols are drawn. */
public enum WorldStyle {
    CLASSIC_INK("Classic ink", "Ink and sepia on old parchment, with water lines along the coasts"),
    WATERCOLOUR("Watercolour", "Soft, light washes of colour that bleed at the edges"),
    FANTASY_COLOUR("Fantasy colour", "Bold, saturated colours with shaded shallows and foamy shores"),
    RETRO_PIXEL("Retro pixel", "Chunky pixels and a limited palette, like an old role-playing game"),
    DARK_FANTASY("Dark fantasy", "Dim, desaturated land under black seas, with glowing lava");

    public final String label;
    public final String description;

    WorldStyle(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /** Short lower-case id used in file names ("classic", "watercolour", "fantasy", "pixel", "dark"). */
    public String folder() {
        return switch (this) {
            case CLASSIC_INK -> "classic";
            case WATERCOLOUR -> "watercolour";
            case FANTASY_COLOUR -> "fantasy";
            case RETRO_PIXEL -> "pixel";
            case DARK_FANTASY -> "dark";
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
