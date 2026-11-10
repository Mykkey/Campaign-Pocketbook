package dnd.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Icon-only dropdown buttons for the toolbars: a button showing one icon, whose menu lists related things, each with its
 * own icon and a label. Toggles show a tick; number and choice boxes sit in a row of their own and keep the menu open.
 */
final class IconMenu {
    private IconMenu() { }

    /** The dropdown button: an icon with a small arrow, a tooltip, and the flat toolbar look. */
    static MenuButton create(String icon, String tooltip) {
        MenuButton b = new MenuButton();
        IconButtons.decorate(b, icon, tooltip);
        b.getStyleClass().add("icon-menu");
        b.setPopupSide(javafx.geometry.Side.BOTTOM);
        return b;
    }

    /** A row with an icon and a label; the key of {@code actionId} (if any) is added to the label. */
    static MenuItem action(String icon, String label, String actionId, Runnable run) {
        MenuItem m = new MenuItem(IconMenuModel.rowTextFor(label, actionId));
        if (icon != null) m.setGraphic(Icons.of(icon));
        m.setOnAction(e -> run.run());
        return m;
    }

    /** A row with an icon, a label and a tick that follows the property both ways. */
    static CheckMenuItem toggle(String icon, String label, String actionId, BooleanProperty property) {
        CheckMenuItem m = new CheckMenuItem(IconMenuModel.rowTextFor(label, actionId));
        if (icon != null) m.setGraphic(Icons.of(icon));
        m.selectedProperty().bindBidirectional(property);
        return m;
    }

    /** A row with an icon, a label and a tick that is not tied to a property (the caller reads and sets it). */
    static CheckMenuItem toggle(String icon, String label, boolean on, java.util.function.Consumer<Boolean> changed) {
        CheckMenuItem m = new CheckMenuItem(label);
        if (icon != null) m.setGraphic(Icons.of(icon));
        m.setSelected(on);
        m.selectedProperty().addListener((o, a, b) -> changed.accept(b));
        return m;
    }

    /** A row for a number or choice box: icon, label, then the control; clicking it does not close the menu. */
    static CustomMenuItem control(String icon, String label, Node control) {
        Label text = new Label(label);
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        if (icon != null) row.getChildren().add(Icons.of(icon));
        row.getChildren().addAll(text, gap, control);
        CustomMenuItem m = new CustomMenuItem(row, false);
        return m;
    }

    /** Greys the row out while {@code disabled} is true. */
    static <T extends MenuItem> T disableWhen(T item, ObservableValue<Boolean> disabled) {
        item.disableProperty().bind(disabled);
        return item;
    }

    /** Shows the row only while {@code shown} is true. */
    static <T extends MenuItem> T showWhen(T item, ObservableValue<Boolean> shown) {
        item.visibleProperty().bind(shown);
        return item;
    }

    static Menu submenu(String icon, String label, MenuItem... items) {
        Menu m = new Menu(label);
        if (icon != null) m.setGraphic(Icons.of(icon));
        m.getItems().addAll(items);
        return m;
    }

    static SeparatorMenuItem separator() {
        return new SeparatorMenuItem();
    }
}
