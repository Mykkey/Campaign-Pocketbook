package dnd;

import dnd.map.world.WorldSnap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldSnapTest {
    @Test
    void theMiddleSnapsToTheMapMiddle() {
        // a 40-wide label whose middle is 3 cells left of the middle of a 1000-wide map
        WorldSnap.Result r = WorldSnap.snap(477, 517, 100, 120, 1000, 800, 6);
        assertEquals(3, r.dx(), 1e-9);
        assertEquals(500, r.guideX(), 1e-9);
        assertEquals(0, r.dy(), 1e-9);
        assertTrue(Double.isNaN(r.guideY()));
    }

    @Test
    void edgesSnapToEdgesAndTheClosestWins() {
        WorldSnap.Result left = WorldSnap.snap(4, 44, 300, 320, 1000, 800, 6);
        assertEquals(-4, left.dx(), 1e-9);
        assertEquals(0, left.guideX(), 1e-9);
        WorldSnap.Result bottomRight = WorldSnap.snap(950, 997, 770, 802, 1000, 800, 6);
        assertEquals(3, bottomRight.dx(), 1e-9);
        assertEquals(1000, bottomRight.guideX(), 1e-9);
        assertEquals(-2, bottomRight.dy(), 1e-9);
        assertEquals(800, bottomRight.guideY(), 1e-9);
        // left edge 2 from the left, middle 1 from the centre: the closer (centre) wins
        WorldSnap.Result both = WorldSnap.snap(2, 1000, 100, 200, 1000, 800, 6);
        assertEquals(0, both.dx(), 1e-9, "its middle is already on the map middle");
    }

    @Test
    void nothingSnapsWhenFarAway() {
        WorldSnap.Result r = WorldSnap.snap(100, 140, 100, 120, 1000, 800, 6);
        assertEquals(0, r.dx());
        assertEquals(0, r.dy());
        assertFalse(r.any());
    }
}
