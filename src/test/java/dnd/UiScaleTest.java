package dnd;

import dnd.ui.AppSettings;
import dnd.ui.UiScale;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UiScaleTest {
    @BeforeEach
    void freshSettings(@TempDir Path tmp) {
        AppSettings.useFile(tmp.resolve("settings.properties"));
    }

    @AfterEach
    void back() {
        AppSettings.useFile(null);
    }

    @Test
    void theScaleIsAFactorKeptInsideItsLimits() {
        assertEquals(1.0, UiScale.factor(), 1e-9);
        AppSettings.setInt(UiScale.KEY, 150);
        assertEquals(1.5, UiScale.factor(), 1e-9);
        assertEquals(30, UiScale.px(20), 1e-9);
        AppSettings.setInt(UiScale.KEY, 5000);
        assertEquals(2.0, UiScale.factor(), 1e-9, "never bigger than 200%");
        AppSettings.setInt(UiScale.KEY, 10);
        assertEquals(0.8, UiScale.factor(), 1e-9, "never smaller than 80%");
        AppSettings.setString(UiScale.KEY, "lots");
        assertEquals(1.0, UiScale.factor(), 1e-9, "a damaged value means 100%");
        assertEquals(80, UiScale.clampPercent(-3));
        assertEquals(200, UiScale.clampPercent(999));
    }

    @Test
    void pixelSizesBecomeRelativeSizes() {
        assertEquals("1.000em", UiScale.em(12));
        assertEquals("0.917em", UiScale.em(11));
        assertEquals("2.333em", UiScale.em(28));
    }

    @Test
    void printingCanForceTheNormalSizeWhateverTheScreenIsSetTo() {
        AppSettings.setInt(UiScale.KEY, 180);
        assertEquals(36, UiScale.px(20), 1e-9);
        double printed = UiScale.withFactor(1.0, () -> UiScale.px(20));
        assertEquals(20, printed, 1e-9);
        assertEquals(36, UiScale.px(20), 1e-9, "back to the setting afterwards");
        assertEquals(40, UiScale.withFactor(2.0, () -> UiScale.withFactor(1.0, () -> 0) + UiScale.px(20)), 1e-9, "nested overrides restore the outer one");
    }

    @Test
    void theBaseSizeIsAddedToAWindowsStyleOnceAndReplacedNotRepeated() {
        Pane root = new Pane();
        root.setStyle("-fx-background-color: #123456;");
        UiScale.applyToRoot(root);
        String first = root.getStyle();
        assertTrue(first.startsWith("-fx-background-color: #123456;"), "its own style is kept: " + first);
        assertTrue(first.contains("-fx-font-size:"), first);
        UiScale.applyToRoot(root);
        assertEquals(first, root.getStyle(), "applying it again changes nothing");
        AppSettings.setInt(UiScale.KEY, 200);
        UiScale.applyToRoot(root);
        assertNotEquals(first, root.getStyle());
        assertEquals(1, root.getStyle().split("-fx-font-size:", -1).length - 1, "only one font size in the style: " + root.getStyle());
        assertTrue(root.getStyle().startsWith("-fx-background-color: #123456;"));
        AppSettings.setString("ui.font", "Verdana");
        UiScale.applyToRoot(root);
        assertTrue(root.getStyle().contains("-fx-font-family: \"Verdana\""), root.getStyle());
        AppSettings.remove("ui.font");
        UiScale.applyToRoot(root);
        assertFalse(root.getStyle().contains("font-family"));
    }
}
