package dnd.ui;

import javafx.beans.property.*;
import javafx.scene.paint.Color;

/** Current tool and style settings, shared by every open board. */
public class ToolState {
    public final ObjectProperty<Tool> tool = new SimpleObjectProperty<>(Tool.SELECT);
    /** Text colour / outline colour. */
    public final ObjectProperty<Color> color = new SimpleObjectProperty<>(colourSetting("new.board.color", "#222222"));
    /** Sticky-note colour / shape fill. */
    public final ObjectProperty<Color> fill = new SimpleObjectProperty<>(colourSetting("new.board.fill", "#FFF2A8"));
    public final BooleanProperty useFill = new SimpleBooleanProperty(false);
    public final BooleanProperty bold = new SimpleBooleanProperty(false);
    public final BooleanProperty italic = new SimpleBooleanProperty(false);
    public final IntegerProperty width = new SimpleIntegerProperty(3);
    public final IntegerProperty fontSize = new SimpleIntegerProperty(Math.max(8, Math.min(120, AppSettings.getInt("new.board.fontSize", 22))));
    /** Underline the names of cards and spells in text and offer to link them (remembered between sessions). */
    public final BooleanProperty nameHints = new SimpleBooleanProperty(AppSettings.getBoolean("nameHints", true));

    /** Show a preview of the linked item when hovering a link (remembered between sessions). */
    public final BooleanProperty linkPreviews = new SimpleBooleanProperty(AppSettings.getBoolean("linkPreviews", true));

    private static Color colourSetting(String key, String standard) {
        try {
            return Color.web(AppSettings.getString(key, standard));
        } catch (IllegalArgumentException e) {
            return Color.web(standard);
        }
    }

    public ToolState() {
        nameHints.addListener((o, a, n) -> AppSettings.setBoolean("nameHints", n));
        linkPreviews.addListener((o, a, n) -> AppSettings.setBoolean("linkPreviews", n));
    }
}
