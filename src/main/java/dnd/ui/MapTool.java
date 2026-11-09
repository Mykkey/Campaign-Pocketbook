package dnd.ui;

public enum MapTool {
    SELECT("Select", "Select, move, rotate and resize pieces and tokens. Double-click a token to rename it"),
    HAND("Hand", "Drag the map around (also: middle mouse drag, or hold Space)"),
    TERRAIN("Terrain", "Paint ground onto the grid (drag to paint). Pick Erase in the terrain list to clear cells"),
    ASSET("Piece", "Place the piece picked in the side panel. Click to stamp it as many times as you like; Esc to stop"),
    TOKEN("Token", "Click a cell to place a labelled token for a player or monster"),
    RULER("Ruler", "Drag to measure distance in cells (5 ft each)"),
    FOG("Fog", "Reveal or hide parts of the map for the players. Pick brush or rectangle and reveal or hide in the Fog menu"),
    TEMPLATE("Area", "Lay a spell or breath area over the map: click where it starts, drag to aim it. Choose the shape and size in the toolbar. Esc clears it"),
    PIN("Pin", "Drop a numbered note for yourself (never shown to players): click the map to add one, click a pin to edit it, drag to move it"),
    LIGHT("Light", "Place a light source (pick the kind in the toolbar). Drag to move, right-click to remove. Turn on Dark map to see its effect");

    public final String label;
    public final String tip;

    MapTool(String label, String tip) {
        this.label = label;
        this.tip = tip;
    }
}
