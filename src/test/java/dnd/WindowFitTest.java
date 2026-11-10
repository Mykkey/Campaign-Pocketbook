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

    @Test
    void aWindowThatIsTooBigOrPartlyOffScreenIsPulledBackInside() {
        // the SRD library: 1150 x 720 on a 1280 x 680 usable area, opened with its top 60 pixels above the screen
        double[] b = WindowFit.clamp(60, -60, 1150, 720, 0, 0, 1280, 680);
        assertEquals(680, b[3], 1e-9, "no taller than the screen");
        assertEquals(0, b[1], 1e-9, "the title bar is on the screen");
        assertEquals(1150, b[2], 1e-9);
        // one that is already fine is left alone
        double[] ok = WindowFit.clamp(100, 50, 800, 500, 0, 0, 1280, 680);
        assertArrayEquals(new double[]{100, 50, 800, 500}, ok, 1e-9);
        // pushed off the right and bottom edges: moved back in
        double[] moved = WindowFit.clamp(1000, 600, 600, 300, 0, 0, 1280, 680);
        assertEquals(680, moved[0], 1e-9);
        assertEquals(380, moved[1], 1e-9);
        // a second monitor to the left
        double[] left = WindowFit.clamp(-1500, 10, 700, 500, -1366, 0, 1366, 728);
        assertEquals(-1366, left[0], 1e-9);
    }
}
