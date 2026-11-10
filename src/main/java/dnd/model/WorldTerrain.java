package dnd.model;

/** What a cell of a world map's base layer is: the sea, a lake, land, lava or ice. 0 means nothing painted (bare parchment). */
public enum WorldTerrain {
    OCEAN(1, "Ocean", "Salt water: seas and oceans"),
    LAKE(2, "Lake", "Fresh water: lakes and large rivers"),
    LAND(3, "Land", "Dry land. Paint a biome on it for grass, sand, snow..."),
    LAVA(4, "Lava", "Molten rock: lava fields and lava seas"),
    ICE(5, "Ice", "Ice sheets and frozen seas");

    public final byte code;
    public final String label;
    public final String tip;

    WorldTerrain(int code, String label, String tip) {
        this.code = (byte) code;
        this.label = label;
        this.tip = tip;
    }

    public static WorldTerrain of(byte code) {
        for (WorldTerrain t : values()) if (t.code == code) return t;
        return null;
    }

    /** Land, ice and lava are solid ground; the sea and lakes are not. */
    public boolean solid() {
        return this == LAND || this == ICE || this == LAVA;
    }

    @Override
    public String toString() {
        return label;
    }
}
