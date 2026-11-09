package dnd.ui;

import dnd.storage.ProjectStore;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.stage.Window;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

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

    public static BooleanProperty darkProperty() {
        return DARK;
    }

    public static boolean isDark() {
        return DARK.get();
    }

    private static Path settingsFile() {
        return ProjectStore.baseDir().resolve("settings.properties");
    }

    /** A button that flips between light and dark mode; its text always names the mode it will switch to. */
    public static Button toggleButton() {
        Button b = new Button();
        b.graphicProperty().bind(Bindings.createObjectBinding(() -> Icons.of(DARK.get() ? "sun" : "moon"), DARK));
        b.getStyleClass().add("icon-button");
        b.tooltipProperty().bind(Bindings.createObjectBinding(
                () -> new Tooltip(DARK.get() ? "Switch to light mode (remembered)" : "Switch to dark mode (remembered)"), DARK));
        b.setFocusTraversable(false);
        b.setOnAction(e -> setDark(!isDark()));
        return b;
    }

    /** Reads the saved choice and starts applying the theme to every window that opens. Call once at startup. */
    public static void install() {
        if (installed) return;
        installed = true;
        DARK.set(loadDark());
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
        saveDark(dark);
        for (Window w : Window.getWindows()) if (w.getScene() != null) apply(w.getScene());
    }

    /** Gives a scene the stylesheet for the current mode. */
    public static void apply(Scene scene) {
        String url = Theme.class.getResource(isDark() ? "/theme-dark.css" : "/theme-light.css").toExternalForm();
        scene.getStylesheets().removeIf(u -> u.endsWith("/theme-dark.css") || u.endsWith("/theme-light.css"));
        scene.getStylesheets().add(url);
    }

    private static boolean loadDark() {
        Path f = settingsFile();
        if (!Files.isRegularFile(f)) return false;
        try (InputStream in = Files.newInputStream(f)) {
            Properties p = new Properties();
            p.load(in);
            return "dark".equals(p.getProperty("theme"));
        } catch (IOException e) {
            return false;
        }
    }

    private static void saveDark(boolean dark) {
        Path f = settingsFile();
        Properties p = new Properties();
        if (Files.isRegularFile(f)) {
            try (InputStream in = Files.newInputStream(f)) {
                p.load(in);
            } catch (IOException ignored) {
                // start fresh
            }
        }
        p.setProperty("theme", dark ? "dark" : "light");
        try {
            Files.createDirectories(f.getParent());
            try (OutputStream out = Files.newOutputStream(f)) {
                p.store(out, "DnD Campaign Notes settings");
            }
        } catch (IOException e) {
            e.printStackTrace(); // the choice just won't be remembered
        }
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