package dnd.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.util.Duration;

/** Draggable panel edges whose positions are remembered between runs (as fractions, so text size and window size do not matter). */
public final class Panels {
    private Panels() { }

    static final String PREFIX = "layout.";

    /** A saved divider position forced into a usable range; {@code standard} if nothing sensible was saved. */
    public static double clampFraction(double v, double standard) {
        if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0) return standard;
        return Math.max(0.05, Math.min(0.95, v));
    }

    public static double saved(String key, double standard) {
        return clampFraction(AppSettings.getDouble(PREFIX + key, standard), standard);
    }

    /** Forgets every remembered panel size. */
    public static void resetAll() {
        AppSettings.resetPrefix(PREFIX);
    }

    /** Makes the first divider of the split start where it was left, and remember where it is moved to. */
    static SplitPane remember(SplitPane split, String key, double standard) {
        double start = saved(key, standard);
        split.setDividerPositions(start);
        PauseTransition later = new PauseTransition(Duration.millis(400));
        boolean[] ready = {false};
        later.setOnFinished(e -> {
            if (!split.getDividers().isEmpty()) AppSettings.setDouble(PREFIX + key, split.getDividerPositions()[0]);
        });
        split.getDividers().get(0).positionProperty().addListener((o, a, b) -> {
            if (ready[0]) later.playFromStart();
        });
        // positions are re-applied when the pane first gets a width (a split pane may move them while laying out)
        Runnable apply = () -> {
            split.setDividerPositions(start);
            ready[0] = true;
        };
        if (split.getWidth() > 0 || split.getHeight() > 0) Platform.runLater(apply);
        else {
            javafx.beans.value.ChangeListener<Number> once = new javafx.beans.value.ChangeListener<>() {
                @Override
                public void changed(javafx.beans.value.ObservableValue<? extends Number> o, Number a, Number b) {
                    if (b.doubleValue() > 0) {
                        o.removeListener(this);
                        Platform.runLater(apply);
                    }
                }
            };
            if (split.getOrientation() == javafx.geometry.Orientation.HORIZONTAL) split.widthProperty().addListener(once);
            else split.heightProperty().addListener(once);
        }
        return split;
    }

    /**
     * A side panel and a main area in a split pane whose divider can be dragged. The side keeps its width when the window
     * is resized. {@code sideFraction} is the share of the width the side starts with.
     */
    static SplitPane sideSplit(Node side, Node main, boolean sideFirst, String key, double sideFraction) {
        SplitPane split = sideFirst ? new SplitPane(side, main) : new SplitPane(main, side);
        SplitPane.setResizableWithParent(side, false);
        double firstShare = sideFirst ? sideFraction : 1 - sideFraction;
        remember(split, key, firstShare);
        return split;
    }

    /** Same, with the side panel below the main area (for the initiative strip). */
    static SplitPane bottomSplit(Node main, Node bottom, String key, double mainFraction) {
        SplitPane split = new SplitPane(main, bottom);
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        SplitPane.setResizableWithParent(bottom, false);
        remember(split, key, mainFraction);
        return split;
    }
}
