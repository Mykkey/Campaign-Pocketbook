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
        assertTrue(lib.assets().size() >= 40, "expected at least 40 built-in assets");
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

    @Test
    void hasTheExpectedCategories() {
        AssetLibrary lib = new AssetLibrary(null);
        for (String c : new String[]{"Buildings", "Walls", "Paths", "Camp", "Nature"}) {
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
