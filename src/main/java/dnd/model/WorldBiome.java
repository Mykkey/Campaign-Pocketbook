package dnd.model;

/** What covers the land of a world map: painted over land (or ice) on the biome layer. 0 means plain, unpainted land. */
public enum WorldBiome {
    GRASS(1, "Grassland"),
    PLAINS(2, "Plains"),
    FARMLAND(3, "Farmland"),
    FOREST(4, "Forest floor"),
    JUNGLE(5, "Jungle"),
    SAVANNA(6, "Savanna"),
    SAND(7, "Sand"),
    DUNES(8, "Desert dunes"),
    BADLANDS(9, "Badlands"),
    ROCK(10, "Rock"),
    SNOW(11, "Snow"),
    TUNDRA(12, "Tundra"),
    SWAMP(13, "Swamp"),
    MOOR(14, "Moorland"),
    ASH(15, "Volcanic ash");

    public final byte code;
    public final String label;

    WorldBiome(int code, String label) {
        this.code = (byte) code;
        this.label = label;
    }

    public static WorldBiome of(byte code) {
        for (WorldBiome b : values()) if (b.code == code) return b;
        return null;
    }

    @Override
    public String toString() {
        return label;
    }
}
