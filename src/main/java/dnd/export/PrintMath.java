package dnd.export;

/** The arithmetic of putting a tall piece of content onto pages: how much to scale it and how many pages it needs. */
public final class PrintMath {
    private PrintMath() { }

    /**
     * How much to enlarge or shrink content {@code contentWidth} wide so it fills the printable width, but never more than
     * {@code maxScale} (a small card should not be blown up to a whole page).
     */
    public static double scaleFor(double contentWidth, double printableWidth, double maxScale) {
        if (contentWidth <= 0 || printableWidth <= 0) return 1;
        return Math.min(maxScale, printableWidth / contentWidth);
    }

    /** How many pages content {@code contentHeight} tall needs when each page holds {@code pageHeight} of it (at least one). */
    public static int pageCount(double contentHeight, double pageHeight) {
        if (pageHeight <= 0) return 1;
        return Math.max(1, (int) Math.ceil(contentHeight / pageHeight - 1e-9));
    }

    /** How much of the content (before scaling) fits on one page of the given printable height. */
    public static double pageSpan(double printableHeight, double scale) {
        return scale <= 0 ? printableHeight : printableHeight / scale;
    }
}
