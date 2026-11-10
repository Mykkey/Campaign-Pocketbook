package dnd.ui;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.stage.Window;


/**
 * Light and dark mode. The choice is remembered in {@code settings.properties} next to the projects and applied to
 * every window (including dialogs and popups) by giving its scene a theme stylesheet that defines the app's named
 * colours (used by inline styles, {@code -t-text}, {@code -t-surface}...) and, for dark, the base colours of the
 * standard controls.
 */
public final class Theme {
    private Theme() { }

    private static final BooleanProperty DARK = new SimpleBooleanProperty(false);
    private static boolean installed;

    /** Called after the text size or font changed, so the main window can build itself again with the new sizes (set by the app). */
    public static Runnable onLookChanged;

    public static BooleanProperty darkProperty() {
        return DARK;
    }

    public static boolean isDark() {
        return DARK.get();
    }

    /** A button that flips between light and dark mode; its text always names the mode it will switch to. */
    public static Button toggleButton() {
        Button b = new Button();
        b.graphicProperty().bind(Bindings.createObjectBinding(() -> Icons.of(DARK.get() ? "sun" : "moon"), DARK));
        b.getStyleClass().add("icon-button");
        b.tooltipProperty().bind(Bindings.createObjectBinding(
                () -> IconButtons.tip(DARK.get() ? "Switch to light mode (remembered)" : "Switch to dark mode (remembered)"), DARK));
        b.setFocusTraversable(false);
        b.setOnAction(e -> setDark(!isDark()));
        return b;
    }

    /** Reads the saved choice and starts applying the theme to every window that opens. Call once at startup. */
    public static void install() {
        if (installed) return;
        installed = true;
        DARK.set("dark".equals(AppSettings.getString("theme", "light")));
        AppSettings.addListener((k, v) -> {
            if ("theme".equals(k)) Platform.runLater(() -> setDark("dark".equals(v)));
        });
        AppSettings.addListener((k, v) -> {
            if (k.equals("ui.highContrast")) {
                Platform.runLater(() -> {
                    for (Window w : Window.getWindows()) if (w.getScene() != null) apply(w.getScene());
                });
            }
            if (k.equals(UiScale.KEY) || k.equals("ui.font")) {
                Platform.runLater(() -> {
                    for (Window w : Window.getWindows()) if (w.getScene() != null) apply(w.getScene());
                    Runnable r = onLookChanged;
                    if (r != null) r.run();
                });
            }
        });
        for (Window w : Window.getWindows()) hook(w);
        Window.getWindows().addListener((ListChangeListener<Window>) c -> {
            while (c.next()) for (Window w : c.getAddedSubList()) hook(w);
        });
    }

    private static void hook(Window w) {
        w.sceneProperty().addListener((o, was, scene) -> {
            if (scene != null) apply(scene);
        });
        if (w.getScene() != null) apply(w.getScene());
    }

    /** Switches mode, remembers it, and restyles every open window. */
    public static void setDark(boolean dark) {
        if (dark == DARK.get()) return;
        DARK.set(dark);
        AppSettings.setString("theme", dark ? "dark" : "light");
        for (Window w : Window.getWindows()) if (w.getScene() != null) apply(w.getScene());
    }

    /** Gives a scene the stylesheet for the current mode. */
    public static void apply(Scene scene) {
        String url = Theme.class.getResource(isDark() ? "/theme-dark.css" : "/theme-light.css").toExternalForm();
        scene.getStylesheets().removeIf(u -> u.endsWith("/theme-dark.css") || u.endsWith("/theme-light.css")
                || u.endsWith("/theme-contrast-dark.css") || u.endsWith("/theme-contrast-light.css"));
        scene.getStylesheets().add(url);
        if (AppSettings.getBoolean("ui.highContrast", false)) {
            scene.getStylesheets().add(Theme.class.getResource(isDark() ? "/theme-contrast-dark.css" : "/theme-contrast-light.css").toExternalForm());
        }
        UiScale.applyTo(scene);
    }

    /**
     * Whiteboard ink for the current mode. In dark mode, dark colours (the default near-black text and lines) are
     * lightened so they can be read on the dark board; colours that are already light are left alone. The saved
     * colour is never changed.
     */
    public static Color ink(Color c) {
        if (!isDark() || c == null) return c;
        double lum = 0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue();
        if (lum >= 0.45) return c;
        double b = c.getBrightness();
        double nb = c.getSaturation() < 0.35 ? Math.min(0.93, 1.05 - b) : Math.min(1.0, b + 0.45);
        return Color.hsb(c.getHue(), c.getSaturation() < 0.35 ? c.getSaturation() : Math.max(0.0, c.getSaturation() - 0.15), nb, c.getOpacity());
    }
}