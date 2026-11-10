package dnd.ui;

import javafx.geometry.Rectangle2D;

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

    public static double[] fit(double wantedWidth, double wantedHeight, double ux, double uy, double uw, double uh) {
        double w = Math.min(wantedWidth, Math.max(320, uw * 0.96));
        double h = Math.min(wantedHeight, Math.max(240, uh * 0.94)); // leave room for the title bar
        double x = ux + (uw - w) / 2;
        double y = uy + (uh - h) / 2;
        return new double[]{Math.max(ux, x), Math.max(uy, y), w, h};
    }
}
