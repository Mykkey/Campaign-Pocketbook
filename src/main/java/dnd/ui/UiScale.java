package dnd.ui;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;

import java.util.function.Supplier;

/**
 * How big the app's text and controls are, set in the Settings (80% to 200%). The base text size of every window is set from
 * it; sizes written as {@code em} in the styles follow, and sizes that are fonts made in code use {@link #px}. Pictures such as
 * maps and whiteboards keep their own zoom.
 */
public final class UiScale {
    private UiScale() { }

    public static final String KEY = "ui.scale";
    public static final int MIN = 80, MAX = 200, DEFAULT = 100;

    /** The size the styles were designed at: {@code 11px} was written as {@code 11 / 12} em. */
    public static final double DESIGN_PX = 12;

    private static final ThreadLocal<Double> OVERRIDE = new ThreadLocal<>();

    /** The chosen scale as a factor (1.0 = 100%). */
    public static double factor() {
        Double o = OVERRIDE.get();
        if (o != null) return o;
        return clampPercent(AppSettings.getInt(KEY, DEFAULT)) / 100.0;
    }

    public static int clampPercent(int percent) {
        return Math.max(MIN, Math.min(MAX, percent));
    }

    /** A size in pixels made bigger or smaller by the chosen scale. */
    public static double px(double size) {
        return size * factor();
    }

    /** Runs something with a fixed scale (1.0 for printing, which must never follow the screen's text size). */
    public static <T> T withFactor(double factor, Supplier<T> work) {
        Double before = OVERRIDE.get();
        OVERRIDE.set(factor);
        try {
            return work.get();
        } finally {
            if (before == null) OVERRIDE.remove();
            else OVERRIDE.set(before);
        }
    }

    // ---------------------------------------------------------------- applying it to a window

    private static final String FRAGMENT = "uiStyleFragment";

    /** The base font size the windows get at the chosen scale, in pixels. */
    public static double baseFontPx() {
        return Math.round(Font.getDefault().getSize() * factor() * 10) / 10.0;
    }

    /** What the app's settings add to the style of a window's root: the base font size (and the chosen font family). */
    static String rootStyle() {
        StringBuilder sb = new StringBuilder("-fx-font-size: ").append(baseFontPx()).append("px;");
        String family = AppSettings.getString("ui.font", "");
        if (!family.isBlank()) sb.append(" -fx-font-family: \"").append(family.replace("\"", "")).append("\";");
        return sb.toString();
    }

    /** Gives the scene's root the current base font size, replacing what an earlier call added and leaving the rest of its style alone. */
    static void applyTo(Scene scene) {
        applyToRoot(scene.getRoot());
    }

    public static void applyToRoot(Parent root) {
        if (root == null) return;
        String old = (String) root.getProperties().get(FRAGMENT);
        String style = root.getStyle() == null ? "" : root.getStyle();
        if (old != null) style = style.replace(old, "").trim();
        String add = rootStyle();
        root.getProperties().put(FRAGMENT, add);
        root.setStyle(style.isEmpty() ? add : style + " " + add);
    }

    /** {@code N / 12} as a CSS size, for sizes that were written in pixels: {@code em(11)} gives {@code 0.917em}. */
    public static String em(double designPx) {
        return String.format(java.util.Locale.ROOT, "%.3fem", designPx / DESIGN_PX);
    }
}
