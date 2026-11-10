package dnd.ui;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

/** Works out a window size and position that fits on the screen, so the title bar and its close button are always reachable. */
public final class WindowFit {
    private WindowFit() { }

    /**
     * @param wantedWidth  the width the window would like (in the screen's own, scaled, pixels)
     * @param wantedHeight the height it would like
     * @param usable       the part of the screen not covered by the taskbar
     * @return {x, y, width, height}: as close to the wanted size as fits, centred in the usable area
     */
    public static double[] fit(double wantedWidth, double wantedHeight, Rectangle2D usable) {
        return fit(wantedWidth, wantedHeight, usable.getMinX(), usable.getMinY(), usable.getWidth(), usable.getHeight());
    }

    /**
     * Pulls a window that is too big or partly off the screen back inside the usable area: it is made no larger than the area
     * and moved so its top (the title bar with the close button) and its other edges are inside it.
     *
     * @return {x, y, width, height}
     */
    public static double[] clamp(double x, double y, double w, double h, double ux, double uy, double uw, double uh) {
        double nw = Math.min(w, uw), nh = Math.min(h, uh);
        double nx = Math.max(ux, Math.min(x, ux + uw - nw));
        double ny = Math.max(uy, Math.min(y, uy + uh - nh));
        return new double[]{nx, ny, nw, nh};
    }

    private static boolean installed;

    /**
     * Makes every window the app opens (dialogs, the SRD library, the player window...) stay inside the screen it appears on,
     * shrinking it if it is bigger than the screen. Call once at startup; it also covers windows opened later.
     */
    public static void install() {
        if (installed) return;
        installed = true;
        for (Window w : Window.getWindows()) hook(w);
        Window.getWindows().addListener((ListChangeListener<Window>) c -> {
            while (c.next()) for (Window w : c.getAddedSubList()) hook(w);
        });
    }

    private static void hook(Window w) {
        if (!(w instanceof Stage stage)) return;
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, e -> {
            keepOnScreen(stage);
            Platform.runLater(() -> keepOnScreen(stage)); // dialogs finish sizing to their content just after they appear
        });
    }

    /** Moves and shrinks a shown window so that it fits the screen it is on. */
    public static void keepOnScreen(Stage stage) {
        if (!stage.isShowing() || stage.isFullScreen() || stage.isMaximized() || stage.isIconified()) return;
        double x = stage.getX(), y = stage.getY(), w = stage.getWidth(), h = stage.getHeight();
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(w) || Double.isNaN(h) || w <= 0 || h <= 0) return;
        var screens = Screen.getScreensForRectangle(x, y, Math.max(1, w), Math.max(1, h));
        Rectangle2D area = (screens.isEmpty() ? Screen.getPrimary() : screens.get(0)).getVisualBounds();
        double[] b = clamp(x, y, w, h, area.getMinX(), area.getMinY(), area.getWidth(), area.getHeight());
        if (Math.abs(b[2] - w) > 0.5) stage.setWidth(b[2]);
        if (Math.abs(b[3] - h) > 0.5) stage.setHeight(b[3]);
        if (Math.abs(b[0] - x) > 0.5) stage.setX(b[0]);
        if (Math.abs(b[1] - y) > 0.5) stage.setY(b[1]);
    }

    public static double[] fit(double wantedWidth, double wantedHeight, double ux, double uy, double uw, double uh) {
        double w = Math.min(wantedWidth, Math.max(320, uw * 0.96));
        double h = Math.min(wantedHeight, Math.max(240, uh * 0.94)); // leave room for the title bar
        double x = ux + (uw - w) / 2;
        double y = uy + (uh - h) / 2;
        return new double[]{Math.max(ux, x), Math.max(uy, y), w, h};
    }
}
