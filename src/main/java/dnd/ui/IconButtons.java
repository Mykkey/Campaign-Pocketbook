package dnd.ui;

import javafx.beans.property.BooleanProperty;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Labeled;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;

/** Makes toolbar buttons icon-only: a drawn icon, a tooltip, and the compact flat look from the theme. */
final class IconButtons {
    private IconButtons() { }

    /** Turns an existing button into an icon button (its text goes; keep the wording in the tooltip). */
    static <T extends ButtonBase> T decorate(T b, String icon) {
        b.setText(null);
        b.setGraphic(Icons.of(icon));
        if (!b.getStyleClass().contains("icon-button")) b.getStyleClass().add("icon-button");
        b.setStyle(""); // drop the old bold / size styling meant for text buttons
        b.setFocusTraversable(false);
        return b;
    }

    static <T extends ButtonBase> T decorate(T b, String icon, String tooltip) {
        decorate(b, icon);
        b.setTooltip(new Tooltip(tooltip));
        return b;
    }

    /** An icon toggle that mirrors a true/false setting (it replaces a check box). */
    static ToggleButton switchToggle(String icon, String tooltip, BooleanProperty property) {
        ToggleButton t = decorate(new ToggleButton(), icon, tooltip);
        t.selectedProperty().bindBidirectional(property);
        return t;
    }

    /** An icon toggle with no property behind it yet. */
    static ToggleButton toggle(String icon, String tooltip) {
        return decorate(new ToggleButton(), icon, tooltip);
    }

    /** For labels that sit beside a control: they carry a tooltip but no text. */
    static void quiet(Labeled l) {
        l.setVisible(false);
        l.setManaged(false);
    }
}
