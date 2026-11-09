package dnd.ui;

public enum MapTool {
    SELECT("Select", "Select, move, rotate and resize pieces and tokens. Double-click a token to rename it"),
    HAND("Hand", "Drag the map around (also: middle mouse drag, or hold Space)"),
    TERRAIN("Terrain", "Paint ground onto the grid (drag to paint). Pick Erase in the terrain list to clear cells"),
    ASSET("Piece", "Place the piece picked in the side panel. Click to stamp it as many times as you like; Esc to stop"),
    TOKEN("Token", "Click a cell to place a labelled token for a player or monster"),
    RULER("Ruler", "Drag to measure distance in cells (5 ft each)");

    public final String label;
    public final String tip;

    MapTool(String label, String tip) {
        this.label = label;
        this.tip = tip;
    }
}
