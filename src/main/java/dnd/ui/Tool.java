package dnd.ui;

public enum Tool {
    SELECT("Select", "Select / move. Double-click empty space to add text; double-click a linked object to follow its link"),
    HAND("Hand", "Drag the board around (also: middle mouse drag, or hold Space)"),
    TEXT("Text", "Click to place text"),
    STICKY("Sticky", "Click to place a sticky note"),
    PEN("Pen", "Freehand drawing"),
    ERASER("Eraser", "Drag over pen drawings to erase them (text, images etc. are not affected)"),
    RECT("Rect", "Draw a rectangle"),
    ELLIPSE("Ellipse", "Draw an ellipse"),
    LINE("Line", "Draw a line"),
    ARROW("Arrow", "Draw an arrow");

    public final String label;
    public final String tip;

    Tool(String label, String tip) {
        this.label = label;
        this.tip = tip;
    }
}
