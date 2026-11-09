package dnd.ui;

import javafx.beans.property.*;
import javafx.scene.paint.Color;

/** Current tool and style settings, shared by every open board. */
public class ToolState {
    public final ObjectProperty<Tool> tool = new SimpleObjectProperty<>(Tool.SELECT);
    /** Text colour / outline colour. */
    public final ObjectProperty<Color> color = new SimpleObjectProperty<>(Color.web("#222222"));
    /** Sticky-note colour / shape fill. */
    public final ObjectProperty<Color> fill = new SimpleObjectProperty<>(Color.web("#FFF2A8"));
    public final BooleanProperty useFill = new SimpleBooleanProperty(false);
    public final BooleanProperty bold = new SimpleBooleanProperty(false);
    public final BooleanProperty italic = new SimpleBooleanProperty(false);
    public final IntegerProperty width = new SimpleIntegerProperty(3);
    public final IntegerProperty fontSize = new SimpleIntegerProperty(22);
}
