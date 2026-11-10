package dnd;

import dnd.ui.Icons;
import dnd.ui.MapTool;
import dnd.ui.Tool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IconsTest {
    @Test
    void everyToolHasAnIcon() {
        for (Tool t : Tool.values()) assertTrue(Icons.has(t.name().toLowerCase()), "whiteboard tool " + t);
        for (MapTool t : MapTool.values()) assertTrue(Icons.has(t.name().toLowerCase()), "map tool " + t);
    }

    @Test
    void theIconsUsedByTheToolbarsExist() {
        for (String n : new String[]{"image", "map", "link", "unlink", "note", "no-note", "link-all", "name-hints", "previews",
                "fill-shapes", "undo", "redo", "delete", "home", "back", "forward", "names", "person", "loot", "relations", "quest",
                "calendar", "book", "clock", "sun", "moon", "grid", "snap", "eye", "eye-off", "battle", "popout", "fit", "clear",
                "add", "stop", "sliders", "monitor", "exit", "target"}) {
            assertTrue(Icons.has(n), n);
        }
    }

    @Test
    void theGearIsAToothedWheelWithAHoleNotASun() throws Exception {
        java.lang.reflect.Field f = Icons.class.getDeclaredField("ICONS");
        f.setAccessible(true);
        @SuppressWarnings("unchecked") java.util.Map<String, String[]> all = (java.util.Map<String, String[]>) f.get(null);
        String[] gear = all.get("gear");
        assertEquals(2, gear.length, "the toothed outline and the centre hole");
        assertTrue(gear[0].split(" L").length >= 32, "eight teeth of four corners each");
        assertFalse(gear[0].contains(" V") || gear[0].contains(" H"), "no sun-ray spokes");
        assertNotEquals(String.join("|", all.get("sun")), String.join("|", gear));
        for (String n : new String[]{"dice", "pen", "text", "world-base", "scalebar", "legend", "relief"}) assertTrue(Icons.has(n), n);
    }

    @Test
    void everyIconPartIsWellFormed() throws Exception {
        java.lang.reflect.Field f = Icons.class.getDeclaredField("ICONS");
        f.setAccessible(true);
        @SuppressWarnings("unchecked") java.util.Map<String, String[]> all = (java.util.Map<String, String[]>) f.get(null);
        for (var e : all.entrySet()) {
            assertTrue(e.getValue().length > 0, e.getKey());
            for (String part : e.getValue()) {
                assertTrue(part.matches("[sfd][bgr]?:M[-0-9A-Za-z .,]+"), e.getKey() + ": " + part);
            }
        }
    }
}
