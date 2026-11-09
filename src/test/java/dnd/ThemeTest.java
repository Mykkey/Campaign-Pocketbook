package dnd;

import dnd.ui.Theme;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThemeTest {
    private static double lum(Color c) {
        return 0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue();
    }

    @AfterEach
    void reset() {
        Theme.darkProperty().set(false); // only the in-memory flag: nothing is saved
    }

    @Test
    void lightModeLeavesInkAlone() {
        Theme.darkProperty().set(false);
        Color c = Color.web("#222222");
        assertEquals(c, Theme.ink(c));
    }

    @Test
    void darkModeLightensDarkInkSoItCanBeRead() {
        Theme.darkProperty().set(true);
        assertTrue(lum(Theme.ink(Color.web("#222222"))) > 0.6, "near-black text becomes light");
        assertTrue(lum(Theme.ink(Color.BLACK)) > 0.6);
        Color navy = Color.web("#1e3a8a");
        Color lifted = Theme.ink(navy);
        assertTrue(lum(lifted) > lum(navy) + 0.2, "dark blue is lifted");
        assertTrue(lifted.getSaturation() > 0.2, "but stays blue");
        assertEquals(Math.round(navy.getHue()), Math.round(lifted.getHue()), 1.0);
    }

    @Test
    void darkModeKeepsLightAndBrightColours() {
        Theme.darkProperty().set(true);
        for (String hex : new String[]{"#ffffff", "#fff2a8", "#f59e0b", "#7db1ff"}) {
            Color c = Color.web(hex);
            assertEquals(c, Theme.ink(c), hex);
        }
    }

    @Test
    void nullIsHandled() {
        Theme.darkProperty().set(true);
        assertNull(Theme.ink(null));
    }
}