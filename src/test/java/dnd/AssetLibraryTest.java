package dnd;

import dnd.map.AssetLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AssetLibraryTest {

    private static boolean hasVisiblePixels(BufferedImage img) {
        int count = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (((img.getRGB(x, y) >>> 24) & 0xff) > 32) count++;
            }
        }
        return count > img.getWidth() * img.getHeight() / 50; // at least ~2% covered
    }

    @Test
    void everyBuiltInAssetRendersSomething() {
        AssetLibrary lib = new AssetLibrary(null);
        assertTrue(lib.assets().size() >= 150, "expected at least 150 built-in assets, got " + lib.assets().size());
        Set<String> ids = new HashSet<>();
        for (AssetLibrary.Asset a : lib.assets()) {
            assertTrue(ids.add(a.id), "duplicate id " + a.id);
            assertTrue(a.cellsW > 0 && a.cellsH > 0, a.id);
            BufferedImage img = lib.render(a.id, 128);
            assertNotNull(img, a.id + " failed to load");
            assertTrue(hasVisiblePixels(img), a.id + " rendered blank");
            // aspect ratio follows the artwork
            assertEquals(a.cellsW / a.cellsH, (double) img.getWidth() / img.getHeight(), 0.05, a.id);
        }
    }

    /** Maps saved with earlier versions reference these ids and sizes; they must keep working. */
    @Test
    void originalPiecesAreStillAvailableAtTheirOriginalSizes() {
        Object[][] original = {
                {"buildings/cottage", 2, 2}, {"buildings/house", 3, 2}, {"buildings/manor", 4, 3}, {"buildings/tavern", 3, 3},
                {"buildings/shop", 2, 2}, {"buildings/barn", 3, 2}, {"buildings/tower", 2, 2}, {"buildings/keep", 4, 4},
                {"walls/straight", 2, 1}, {"walls/corner", 1, 1}, {"walls/gate", 3, 1}, {"walls/tower", 1, 1},
                {"walls/battlement", 2, 1}, {"walls/fence", 2, 1}, {"walls/door", 1, 1}, {"walls/window", 1, 1},
                {"paths/dirt-straight", 1, 1}, {"paths/dirt-corner", 1, 1}, {"paths/dirt-cross", 1, 1},
                {"paths/stone-straight", 1, 1}, {"paths/stone-corner", 1, 1}, {"paths/bridge", 2, 1},
                {"paths/stairs", 1, 1}, {"paths/stepping-stones", 1, 1},
                {"camp/tent", 2, 2}, {"camp/tent-round", 2, 2}, {"camp/campfire", 1, 1}, {"camp/bedroll", 1, 2},
                {"camp/barrel", 1, 1}, {"camp/crate", 1, 1}, {"camp/cart", 2, 1}, {"camp/table", 2, 1},
                {"camp/chair", 1, 1}, {"camp/chest", 1, 1}, {"camp/logs", 1, 1},
                {"nature/tree-oak", 2, 2}, {"nature/tree-pine", 2, 2}, {"nature/bush", 1, 1}, {"nature/rock", 1, 1},
                {"nature/boulders", 2, 2}, {"nature/pond", 3, 2}, {"nature/flowers", 1, 1}, {"nature/stump", 1, 1}};
        AssetLibrary lib = new AssetLibrary(null);
        for (Object[] o : original) {
            AssetLibrary.Asset a = lib.get((String) o[0]);
            assertNotNull(a, o[0] + " is missing");
            assertEquals((int) (Integer) o[1], a.cellsW, 0.001, o[0] + " width");
            assertEquals((int) (Integer) o[2], a.cellsH, 0.001, o[0] + " height");
        }
    }

    @Test
    void hasTheExpectedCategories() {
        AssetLibrary lib = new AssetLibrary(null);
        for (String c : new String[]{"Buildings", "Walls", "Paths", "Camp", "Nature", "Dungeon", "Furniture"}) {
            assertTrue(lib.categories().contains(c), c);
        }
        assertTrue(lib.categories().contains(AssetLibrary.IMPORTED));
    }

    @Test
    void importedSvgAndPngAppearInTheLibrary(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("blob.svg"),
                "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 200 100'><rect width='200' height='100' fill='red'/></svg>");
        BufferedImage png = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 40; x++) for (int y = 0; y < 40; y++) png.setRGB(x, y, 0xFF00FF00);
        javax.imageio.ImageIO.write(png, "png", tmp.resolve("square.png").toFile());
        Files.writeString(tmp.resolve("notes.txt"), "ignored");

        AssetLibrary lib = new AssetLibrary(tmp);
        AssetLibrary.Asset svg = lib.get("imported/blob.svg");
        AssetLibrary.Asset pic = lib.get("imported/square.png");
        assertNotNull(svg);
        assertNotNull(pic);
        assertNull(lib.get("imported/notes.txt"));
        assertTrue(svg.imported);
        assertEquals(2.0, svg.cellsW, 0.01);   // longest side defaults to 2 cells
        assertEquals(1.0, svg.cellsH, 0.01);
        assertTrue(hasVisiblePixels(lib.render(svg.id, 100)));
        assertTrue(hasVisiblePixels(lib.render(pic.id, 100)));
    }
}
