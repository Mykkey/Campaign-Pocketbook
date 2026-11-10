package dnd.ui;

/** The tools of the world map editor. */
public enum WorldTool {
    SELECT("Select", "select", "Select, move and resize symbols. Drag on empty map to select several. [ and ] turn them, F flips, Delete removes"),
    HAND("Hand", "hand", "Drag the map around (also: middle mouse drag, or hold Space). The wheel zooms"),
    BASE("Land and sea", "world-base", "Paint ocean, lakes, land, lava or ice. Choose what to paint next to the tool; the eraser takes it away again"),
    BIOME("Biomes", "leaf", "Paint what covers the land: grass, sand, snow, forest floor... (only on land and ice)"),
    STAMP("Stamp", "stamp", "Place the symbol chosen in the side panel (a mountain, a town...). Click as often as you like; Esc to stop"),
    PATH("Lines", "river", "Draw a river, road, trail, sea route, border or railway: drag to draw freehand, or click points and double-click to finish. Choose the kind next to the tool"),
    REGION("Region", "region", "Outline a named area (a kingdom, a forest): click the corners, double-click to close it, then name it"),
    SCATTER("Scatter", "scatter", "Paint a whole forest, hills or a mountain range of the chosen symbol and its relatives; they are spread out naturally"),
    LABEL("Label", "text", "Write a name on the map: a sea, a kingdom, a town. Choose the kind and the curve next to the tool; double-click a label to change its words"),
    RULER("Ruler", "ruler", "Measure a journey: click points along the way, and see the distance and how long it takes on foot, riding or by ship. Esc clears it"),
    FILL("Bucket", "bucket", "Fill a whole area in one click: the sea, a lake, a continent, or all the land of one biome. Choose what to fill with next to the tool");

    public final String label;
    public final String icon;
    public final String tip;

    WorldTool(String label, String icon, String tip) {
        this.label = label;
        this.icon = icon;
        this.tip = tip;
    }
}
