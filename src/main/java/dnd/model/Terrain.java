package dnd.model;

/** Ground types that can be painted onto battlemap cells. */
public enum Terrain {
    GRASS("grass", "Grass", "#7fb36b"),
    DIRT("dirt", "Dirt", "#a67c52"),
    SAND("sand", "Sand", "#e3d29a"),
    STONE("stone", "Stone", "#a3a3a3"),
    WOOD("wood", "Wood floor", "#b88a52"),
    WATER("water", "Water", "#5b9bd5"),
    SNOW("snow", "Snow", "#eef3f7"),
    LAVA("lava", "Lava", "#e8602c");

    public final String id;
    public final String label;
    public final String color;

    Terrain(String id, String label, String color) {
        this.id = id;
        this.label = label;
        this.color = color;
    }

    public static Terrain byId(String id) {
        for (Terrain t : values()) if (t.id.equals(id)) return t;
        return null;
    }
}
