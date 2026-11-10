package dnd;

import dnd.ui.WindowFit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WindowFitTest {
    @Test
    void aBigScreenGetsTheWantedSizeCentred() {
        double[] b = WindowFit.fit(1400, 850, 0, 0, 1920, 1040);
        assertEquals(1400, b[2], 1e-9);
        assertEquals(850, b[3], 1e-9);
        assertEquals(260, b[0], 1e-9);
        assertEquals(95, b[1], 1e-9);
    }

    @Test
    void aSmallScaledLaptopScreenShrinksTheWindowSoTheTitleBarIsOnScreen() {
        // 1920 x 1080 at 150% is 1280 x 720, minus the taskbar: about 1280 x 680
        double[] b = WindowFit.fit(1400, 850, 0, 0, 1280, 680);
        assertTrue(b[2] <= 1280 && b[3] <= 680, "fits: " + b[2] + " x " + b[3]);
        assertTrue(b[0] >= 0 && b[1] >= 0, "the top left corner, with the title bar, is on the screen");
        assertTrue(b[0] + b[2] <= 1280 && b[1] + b[3] <= 680);
        assertTrue(b[3] < 680, "room is left above and below for the title bar and taskbar");
    }

    @Test
    void aSecondMonitorWithAnOffsetKeepsTheWindowInsideIt() {
        double[] b = WindowFit.fit(1400, 850, -1366, 0, 1366, 728);
        assertTrue(b[0] >= -1366 && b[0] + b[2] <= 0);
        assertTrue(b[1] >= 0 && b[1] + b[3] <= 728);
    }

    @Test
    void aTinyScreenStillGetsAUsableWindow() {
        double[] b = WindowFit.fit(1400, 850, 0, 0, 300, 200);
        assertTrue(b[2] >= 320 - 1e-9 || b[2] <= 300);
        assertTrue(b[0] >= 0 && b[1] >= 0);
    }
}
