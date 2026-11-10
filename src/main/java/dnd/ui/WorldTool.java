package dnd.ui;

/** The tools of the world map editor. */
public enum WorldTool {
    HAND("Hand", "hand", "Drag the map around (also: middle mouse drag, or hold Space). The wheel zooms"),
    BASE("Land and sea", "world-base", "Paint ocean, lakes, land, lava or ice. Choose what to paint next to the tool; the eraser takes it away again"),
    BIOME("Biomes", "leaf", "Paint what covers the land: grass, sand, snow, forest floor... (only on land and ice)");

    public final String label;
    public final String icon;
    public final String tip;

    WorldTool(String label, String icon, String tip) {
        this.label = label;
        this.icon = icon;
        this.tip = tip;
    }
}
