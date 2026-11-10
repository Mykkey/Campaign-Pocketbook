package dnd.ui;

/** The tools of the world map editor. */
public enum WorldTool {
    SELECT("Select", "select", "Select, move and resize symbols. Drag on empty map to select several. [ and ] turn them, F flips, Delete removes"),
    HAND("Hand", "hand", "Drag the map around (also: middle mouse drag, or hold Space). The wheel zooms"),
    BASE("Land and sea", "world-base", "Paint ocean, lakes, land, lava or ice. Choose what to paint next to the tool; the eraser takes it away again"),
    BIOME("Biomes", "leaf", "Paint what covers the land: grass, sand, snow, forest floor... (only on land and ice)"),
    STAMP("Stamp", "stamp", "Place the symbol chosen in the side panel (a mountain, a town...). Click as often as you like; Esc to stop"),
    SCATTER("Scatter", "scatter", "Paint a whole forest, hills or a mountain range of the chosen symbol and its relatives; they are spread out naturally");

    public final String label;
    public final String icon;
    public final String tip;

    WorldTool(String label, String icon, String tip) {
        this.label = label;
        this.icon = icon;
        this.tip = tip;
    }
}
