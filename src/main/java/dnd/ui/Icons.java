package dnd.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.shape.SVGPath;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Small drawn icons for the toolbars, on a 16 x 16 grid. They are plain shapes (no fonts or emoji), so they look the
 * same on every machine, and they take the theme's text colour, so they work in light and dark mode. Each icon is a
 * list of parts written as {@code "style:path"}: {@code s} = outline, {@code f} = filled, {@code d} = dashed outline,
 * followed by an optional colour letter ({@code b} blue, {@code g} green, {@code r} red), e.g. {@code "sb:M3 3 H13"}
 * is a blue outline.
 */
public final class Icons {
    private Icons() { }

    private static final Map<String, String[]> ICONS = new LinkedHashMap<>();

    private static void def(String name, String... parts) {
        ICONS.put(name, parts);
    }

    private static final String CHAIN = "M6.8 9.2 L9.2 6.8 M7.2 4.6 L8.3 3.5 A2.6 2.6 0 0 1 12 7.2 L10.9 8.3 M8.8 11.4 L7.7 12.5 A2.6 2.6 0 0 1 4 8.8 L5.1 7.7";
    private static final String EYE = "M1.5 8 C3.5 4.5 6 3.5 8 3.5 C10 3.5 12.5 4.5 14.5 8 C12.5 11.5 10 12.5 8 12.5 C6 12.5 3.5 11.5 1.5 8 Z M8 5.9 A2.1 2.1 0 1 1 7.99 5.9";
    private static final String NOTE = "M2.5 3 H13.5 V10.5 H8 L5 13.5 V10.5 H2.5 Z";
    private static final String SUN = "M8 5.3 A2.7 2.7 0 1 1 7.99 5.3 M8 1.5 V3 M8 13 V14.5 M1.5 8 H3 M13 8 H14.5 M3.4 3.4 L4.4 4.4 M11.6 11.6 L12.6 12.6 M3.4 12.6 L4.4 11.6 M11.6 4.4 L12.6 3.4";
    private static final String MOON = "M12.8 9.6 A5.2 5.2 0 1 1 6.4 3.2 A4.1 4.1 0 0 0 12.8 9.6 Z";

    static {
        // ---- whiteboard tools
        def("select", "f:M3.5 2 L3.5 12.8 L6.3 10.2 L8.4 14.4 L10.4 13.5 L8.3 9.4 L12 9.2 Z");
        def("hand", "s:M5.5 14 C4 12.6 3 11 2.5 9.6 L3.3 8.9 L5.5 10.6 V4.2 A1 1 0 0 1 7.5 4.2 V8 M7.5 8 V2.9 A1 1 0 0 1 9.5 2.9 V8 M9.5 8 V3.7 A1 1 0 0 1 11.5 3.7 V9 M11.5 9 V5.7 A1 1 0 0 1 13.5 5.7 V10 C13.5 12.5 12 14 9.5 14 Z");
        def("text", "s:M3 3.5 H13 M8 3.5 V13 M6 13 H10");
        def("sticky", "s:M2.5 2.5 H13.5 V10 L10 13.5 H2.5 Z M13.5 10 H10 V13.5");
        def("pen", "s:M3 13 L3.8 10 L10.6 3.2 A1.3 1.3 0 0 1 12.8 5.4 L6 12.2 Z M9.4 4.4 L11.6 6.6");
        def("eraser", "s:M2.8 9.7 L8.7 3.8 A1 1 0 0 1 10.1 3.8 L12.2 5.9 A1 1 0 0 1 12.2 7.3 L7.5 12 H4.5 Z M5.8 7 L9 10.2 M7.5 13.2 H13.5");
        def("rect", "s:M2.5 4 H13.5 V12 H2.5 Z");
        def("ellipse", "s:M8 3.5 A5.5 4.5 0 1 1 7.99 3.5 Z");
        def("line", "s:M3 13 L13 3");
        def("arrow", "s:M3 13 L13 3 M7.5 3 H13 V8.5");
        def("image", "s:M2.5 3 H13.5 V13 H2.5 Z M2.5 10.5 L6 7 L9 10 L11 8 L13.5 10.5", "f:M10.9 5.8 A1.1 1.1 0 1 1 10.89 5.8 Z");
        def("map", "s:M1.5 3.5 L5.5 2 L10.5 3.5 L14.5 2 V12.5 L10.5 14 L5.5 12.5 L1.5 14 Z M5.5 2.5 V12.5 M10.5 3.5 V13.5");
        // ---- links and notes
        def("link", "sb:" + CHAIN);
        def("unlink", "sb:" + CHAIN, "sr:M3 3 L13 13");
        def("note", "sg:" + NOTE, "sg:M5 6 H11 M5 8 H9");
        def("no-note", "sg:" + NOTE, "sr:M3 3 L13 13");
        def("link-all", "sb:M5.5 8.5 L7.8 6.2 M6 4.3 L7 3.3 A2.3 2.3 0 0 1 10.3 6.6 L9.3 7.6 M7.5 10.3 L6.5 11.3 A2.3 2.3 0 0 1 3.2 8 L4.2 7", "sb:M11.5 9.5 V14 M9.25 11.75 H13.75");
        def("name-hints", "s:M4 10 L8 3 L12 10 M5.6 7.3 H10.4", "d:M2.5 13.2 H13.5");
        def("previews", "s:" + EYE);
        def("fill-shapes", "f:M3 3 H13 V13 H3 Z");
        def("undo", "s:M5.5 3.5 L2.5 6.5 L5.5 9.5 M2.5 6.5 H9.5 A3.5 3.5 0 0 1 9.5 13.5 H6");
        def("redo", "s:M10.5 3.5 L13.5 6.5 L10.5 9.5 M13.5 6.5 H6.5 A3.5 3.5 0 0 0 6.5 13.5 H10");
        def("help", "s:M8 1.5 A6.5 6.5 0 1 1 7.99 1.5 Z", "s:M6 6.3 C6 4.2 10 4.2 10 6.4 C10 8 8 8 8 9.5", "f:M8 11.3 A0.9 0.9 0 1 1 7.99 11.3 Z");
        def("delete", "sr:M3 4.5 H13 M6 4.5 V3 H10 V4.5 M4.5 4.5 L5 13.5 H11 L11.5 4.5 M7 7 V11 M9 7 V11");
        // ---- top bar
        def("home", "s:M2 8 L8 2.5 L14 8 M3.5 7 V13.5 H12.5 V7 M6.5 13.5 V9.5 H9.5 V13.5");
        def("back", "s:M10 3 L5 8 L10 13");
        def("forward", "s:M6 3 L11 8 L6 13");
        def("names", "s:M2.5 4 H13.5 V12 H2.5 Z M5 7.3 H11 M5 9.7 H9");
        def("person", "s:M8 3 A2.7 2.7 0 1 1 7.99 3 Z M3 14 C3 11.2 5.2 9.5 8 9.5 C10.8 9.5 13 11.2 13 14");
        def("loot", "s:M2.5 7 H13.5 V13.5 H2.5 Z M2.5 7 C2.5 4.2 4.5 2.5 8 2.5 C11.5 2.5 13.5 4.2 13.5 7 M7 9.5 H9 V11.3 H7 Z");
        def("relations", "s:M8 4.5 L4 11 M8 4.5 L12 11 M4 11 H12", "f:M6.3 4.5 A1.7 1.7 0 1 0 9.7 4.5 A1.7 1.7 0 1 0 6.3 4.5 Z M2.3 11 A1.7 1.7 0 1 0 5.7 11 A1.7 1.7 0 1 0 2.3 11 Z M10.3 11 A1.7 1.7 0 1 0 13.7 11 A1.7 1.7 0 1 0 10.3 11 Z");
        def("quest", "s:M4 14.5 V2", "f:M4.5 2.5 H13 L11 5.2 L13 7.9 H4.5 Z");
        def("calendar", "s:M2.5 4 H13.5 V13.5 H2.5 Z M2.5 7 H13.5 M5.5 2.5 V5 M10.5 2.5 V5");
        def("gear", "s:M6.94 3.42 L7.08 1.46 L8.92 1.46 L9.06 3.42 L10.49 4.01 L11.97 2.73 L13.27 4.03 L11.99 5.51 L12.58 6.94 L14.54 7.08 L14.54 8.92 L12.58 9.06 L11.99 10.49 L13.27 11.97 L11.97 13.27 L10.49 11.99 L9.06 12.58 L8.92 14.54 L7.08 14.54 L6.94 12.58 L5.51 11.99 L4.03 13.27 L2.73 11.97 L4.01 10.49 L3.42 9.06 L1.46 8.92 L1.46 7.08 L3.42 6.94 L4.01 5.51 L2.73 4.03 L4.03 2.73 L5.51 4.01 Z", "s:M8 6 A2 2 0 1 1 7.99 6 Z");
        def("rules", "s:M3.5 2.5 H12.5 V13.5 H3.5 Z M6 5.5 H10 M6 8 H10 M6 10.5 H8.5");
        def("book", "s:M2.5 3 H7 A1 1 0 0 1 8 4 V13.5 A1 1 0 0 0 7 12.5 H2.5 Z M13.5 3 H9 A1 1 0 0 0 8 4 V13.5 A1 1 0 0 1 9 12.5 H13.5 Z");
        def("clock", "s:M8 2.5 A5.5 5.5 0 1 1 7.99 2.5 Z M8 5 V8 L10.2 9.6");
        def("sun", "s:" + SUN);
        def("moon", "s:" + MOON);
        // ---- battlemap tools
        def("terrain", "s:M1.5 13 L6 5 L8.5 9 L10.5 6.5 L14.5 13 Z");
        def("asset", "s:M8 2 L13.5 5 V11 L8 14 L2.5 11 V5 Z M2.5 5 L8 8 L13.5 5 M8 8 V14");
        def("token", "s:M8 2.5 A5.5 5.5 0 1 1 7.99 2.5 Z", "f:M8 6 A2 2 0 1 1 7.99 6 Z");
        def("ruler", "s:M2 11 L11 2 L14 5 L5 14 Z M5 8 L6.5 9.5 M7.5 6 L9 7.5 M9.5 4 L10.5 5");
        def("resize", "s:M2.5 2.5 H9 V9 H2.5 Z M9 6 H13.5 V13.5 H6 V9 M13.5 2.5 L9.5 6.5 M11 2.5 H13.5 V5");
        def("relief", "s:M1.5 13.5 L5.5 5 L8.5 10 L10.5 7 L14.5 13.5 Z M5.5 5 L5.5 13.5 M10.5 7 L10.5 13.5");
        def("fog", "s:M4.5 12.5 A3 3 0 0 1 4.8 6.6 A4 4 0 0 1 12.2 7 A2.8 2.8 0 0 1 11.5 12.5 Z");
        def("template", "s:M2.5 8 L13.5 3 A8 8 0 0 1 13.5 13 Z");
        def("pin", "s:M8 14 C8 14 3.5 9.5 3.5 6.5 A4.5 4.5 0 0 1 12.5 6.5 C12.5 9.5 8 14 8 14 Z", "f:M8 5.2 A1.5 1.5 0 1 1 7.99 5.2 Z");
        def("light", "s:M8 2 A4 4 0 0 1 10.5 9.3 V11 H5.5 V9.3 A4 4 0 0 1 8 2 Z M6 13 H10 M6.8 14.8 H9.2");
        def("grid", "s:M2.5 2.5 H13.5 V13.5 H2.5 Z M2.5 6.2 H13.5 M2.5 9.8 H13.5 M6.2 2.5 V13.5 M9.8 2.5 V13.5");
        def("snap", "s:M3 3 V8 A5 5 0 0 0 13 8 V3 H10 V8 A2 2 0 0 1 6 8 V3 Z");
        def("eye", "s:" + EYE);
        def("eye-off", "s:" + EYE, "sr:M3 13 L13 3");
        def("battle", "s:M2.5 2.5 L12.5 12.5 M13.5 2.5 L3.5 12.5 M10.5 13.5 L13.5 10.5 M5.5 13.5 L2.5 10.5");
        def("popout", "s:M9 2.5 H13.5 V7 M13.5 2.5 L7.5 8.5 M11 9.5 V13.5 H2.5 V5 H6.5");
        def("fit", "s:M2.5 6 V2.5 H6 M10 2.5 H13.5 V6 M13.5 10 V13.5 H10 M6 13.5 H2.5 V10");
        def("clear", "s:M3.5 3.5 L12.5 12.5 M12.5 3.5 L3.5 12.5");
        // ---- world map
        def("dice", "s:M3 3 H13 V13 H3 Z", "f:M5.8 5.8 A0.8 0.8 0 1 1 5.79 5.8 Z M10.2 10.2 A0.8 0.8 0 1 1 10.19 10.2 Z M8 8 A0.8 0.8 0 1 1 7.99 8 Z M10.2 5.8 A0.8 0.8 0 1 1 10.19 5.8 Z M5.8 10.2 A0.8 0.8 0 1 1 5.79 10.2 Z");
        def("globe", "s:M8 1.8 A6.2 6.2 0 1 1 7.99 1.8 Z M1.8 8 H14.2 M8 1.8 C5.2 4.6 5.2 11.4 8 14.2 M8 1.8 C10.8 4.6 10.8 11.4 8 14.2");
        def("world-base", "s:M2 10.5 C3 9 4.5 9.5 5.5 8.5 C6.5 7.5 6 5 8 4.5 C10 4 10.5 6 12 6.5 C13.5 7 14 9 13 11 C12 13 9 13 7 12.5 C5 12 3 12.5 2 10.5 Z");
        def("stamp", "s:M6.5 2.5 H9.5 V7 L12 9.5 V11 H4 V9.5 L6.5 7 Z M3 13.5 H13");
        def("scatter", "s:M4.5 4.5 A1.4 1.4 0 1 1 4.51 4.5 Z M10.5 3.5 A1.4 1.4 0 1 1 10.51 3.5 Z M7.5 8 A1.4 1.4 0 1 1 7.51 8 Z M12.5 9.5 A1.4 1.4 0 1 1 12.51 9.5 Z M3.5 11.5 A1.4 1.4 0 1 1 3.51 11.5 Z M9 13 A1.4 1.4 0 1 1 9.01 13 Z");
        def("river", "s:M3 2.5 C6 4 4 6 7 7.5 C10 9 8 11 11 12.5 L13 13.5");
        def("road", "s:M5 14 L6.5 2 M11 14 L9.5 2", "d:M8 14 L8 2");
        def("trail", "d:M3 13 C6 12 7 8 10 7 C12 6.5 12.5 4 13.5 3");
        def("route", "d:M2.5 11 C5 7 8 12 10.5 8 C12 5.8 13 5 14 4.5", "s:M12.5 3 L14.5 4.5 L12.5 6");
        def("border", "d:M2.5 13 L5 6 L9 9 L12 2.5");
        def("railway", "s:M4 14 L7 2 M12 14 L9 2", "s:M4.7 11 H11.4 M5.4 8 H10.7 M6.2 5 H10");
        def("region", "d:M3 4 L10 2.5 L13.5 7 L11 13 L4 12 Z");
        def("waves", "s:M2 6 C3.5 4.5 5 7.5 6.5 6 C8 4.5 9.5 7.5 11 6 C12 5 13 5.5 14 6 M2 10.5 C3.5 9 5 12 6.5 10.5 C8 9 9.5 12 11 10.5 C12 9.5 13 10 14 10.5");
        def("drop", "s:M8 2 C8 2 3.5 7 3.5 10 A4.5 4.5 0 0 0 12.5 10 C12.5 7 8 2 8 2 Z");
        def("flame", "s:M8 14 C4.5 14 3.5 11 5 8.5 C5.5 9.5 6.2 9.8 6.6 9.6 C6 7 7 4.5 9 2 C9.5 4.5 12 6.5 12 10 C12 12.5 10.5 14 8 14 Z");
        def("snowflake", "s:M8 1.5 V14.5 M2.4 4.7 L13.6 11.3 M2.4 11.3 L13.6 4.7 M6.5 2.7 L8 4 L9.5 2.7 M6.5 13.3 L8 12 L9.5 13.3");
        def("leaf", "s:M3 13 C3 6.5 7 3 13.5 2.5 C13.5 9 10 13 3 13 Z M3 13 L9 7");
        def("scalebar", "s:M2 10 H14 M2 7.5 V12.5 M8 8.8 V11.2 M14 7.5 V12.5");
        def("legend", "s:M3 2.5 H13 V13.5 H3 Z M5 5.5 H6.5 M8.5 5.5 H11 M5 8 H6.5 M8.5 8 H11 M5 10.5 H6.5 M8.5 10.5 H11");
        def("bucket", "s:M3.5 8.5 L8 4 L12.5 8.5 L8.5 12.5 Q7.5 13.5 6.5 12.5 Z M8 4 L7 3", "d:M13.5 10.5 C13.5 10.5 12.2 12 12.2 12.8 A1.3 1.3 0 0 0 14.8 12.8 C14.8 12 13.5 10.5 13.5 10.5 Z");
        def("brush", "s:M3 13.5 C3 11.5 4.5 10.5 6 10.5 C6.5 10.5 7 11 7 11.5 C7 13 5.5 13.5 3 13.5 Z M6.8 10.2 L13 3.2 L13.8 3.9 L8.2 11.2");
        // ---- battle screen
        def("add", "s:M8 3 V13 M3 8 H13");
        def("stop", "fr:M3.5 3.5 H12.5 V12.5 H3.5 Z");
        def("sliders", "s:M2.5 5 H13.5 M2.5 11 H13.5 M6 3 V7 M10 9 V13");
        def("fog", "s:M4.5 12.5 A3 3 0 0 1 4.2 6.6 A4 4 0 0 1 11.6 5.6 A3.4 3.4 0 0 1 11.8 12.5 Z M5.5 14.5 H10.5");
        def("monitor", "s:M2.5 3 H13.5 V10.5 H2.5 Z M6 13.5 H10 M8 10.5 V13.5");
        def("exit", "s:M6.5 2.5 H3 V13.5 H6.5 M7 8 H13.5 M11 5.5 L13.5 8 L11 10.5");
        def("target", "s:M8 2.5 A5.5 5.5 0 1 1 7.99 2.5 Z M8 5.5 A2.5 2.5 0 1 1 7.99 5.5 Z");
    }

    /** Every icon name (for tests). */
    public static Set<String> names() {
        return ICONS.keySet();
    }

    public static boolean has(String name) {
        return ICONS.containsKey(name);
    }

    private static String colour(char c) {
        return switch (c) {
            case 'b' -> "#2f6fe0";
            case 'g' -> "#1f9d4d";
            case 'r' -> "#d93a3a";
            default -> "-t-text";
        };
    }

    /** The icon as a node about 18 px square. An unknown name gives an empty square rather than an error. */
    public static Node of(String name) {
        Group g = new Group();
        String[] parts = ICONS.get(name);
        if (parts != null) {
            for (String part : parts) {
                int colon = part.indexOf(':');
                String style = part.substring(0, colon);
                char kind = style.charAt(0);
                String col = colour(style.length() > 1 ? style.charAt(1) : ' ');
                SVGPath p = new SVGPath();
                p.setContent(part.substring(colon + 1));
                if (kind == 'f') {
                    p.setStyle("-fx-fill: " + col + "; -fx-stroke: transparent;");
                } else {
                    p.setStyle("-fx-fill: transparent; -fx-stroke: " + col + "; -fx-stroke-width: 1.4; -fx-stroke-line-cap: round; "
                            + "-fx-stroke-line-join: round;" + (kind == 'd' ? " -fx-stroke-dash-array: 1.2 2.6;" : ""));
                }
                g.getChildren().add(p);
            }
        }
        g.setScaleX(1.125);
        g.setScaleY(1.125);
        Pane box = new Pane(g);
        box.setMinSize(18, 18);
        box.setPrefSize(18, 18);
        box.setMaxSize(18, 18);
        g.setLayoutX(1.0);
        g.setLayoutY(1.0);
        box.setMouseTransparent(true);
        return box;
    }
}
