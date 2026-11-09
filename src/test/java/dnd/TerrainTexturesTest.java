package dnd;

import dnd.map.TerrainTextures;
import dnd.model.Terrain;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TerrainTexturesTest {
    private static final int S = 256;

    private static double diff(int a, int b) {
        return (Math.abs(((a >> 16) & 255) - ((b >> 16) & 255))
                + Math.abs(((a >> 8) & 255) - ((b >> 8) & 255))
                + Math.abs((a & 255) - (b & 255))) / 3.0;
    }

    @Test
    void everyTerrainHasRealTextureNotAFlatColour() {
        for (Terrain t : Terrain.values()) {
            BufferedImage img = TerrainTextures.render(t, S);
            Set<Integer> colours = new HashSet<>();
            double sum = 0, sumSq = 0;
            for (int y = 0; y < S; y += 2) {
                for (int x = 0; x < S; x += 2) {
                    int c = img.getRGB(x, y) & 0xffffff;
                    colours.add(c);
                    double lum = (((c >> 16) & 255) + ((c >> 8) & 255) + (c & 255)) / 3.0;
                    sum += lum;
                    sumSq += lum * lum;
                }
            }
            int n = (S / 2) * (S / 2);
            double std = Math.sqrt(sumSq / n - (sum / n) * (sum / n));
            assertTrue(colours.size() > 300, t + " has only " + colours.size() + " distinct colours");
            assertTrue(std > 6, t + " is nearly flat (std " + std + ")");
        }
    }

    @Test
    void texturesTileSeamlessly() {
        for (Terrain t : Terrain.values()) {
            BufferedImage img = TerrainTextures.render(t, S);
            double wrapH = 0, wrapV = 0, maxInnerH = 0, maxInnerV = 0;
            for (int i = 0; i < S; i++) {
                wrapH += diff(img.getRGB(S - 1, i), img.getRGB(0, i));
                wrapV += diff(img.getRGB(i, S - 1), img.getRGB(i, 0));
            }
            // Total difference between neighbouring pixel columns/rows, for many interior lines. Textures
            // with thin high-contrast lines (mortar, plank gaps) vary a lot from line to line, so the join
            // across the tile edge is compared with the worst interior line, not the average.
            for (int mid = 0; mid < S - 1; mid++) {
                double h = 0, v = 0;
                for (int i = 0; i < S; i++) {
                    h += diff(img.getRGB(mid, i), img.getRGB(mid + 1, i));
                    v += diff(img.getRGB(i, mid), img.getRGB(i, mid + 1));
                }
                maxInnerH = Math.max(maxInnerH, h);
                maxInnerV = Math.max(maxInnerV, v);
            }
            assertTrue(wrapH <= maxInnerH * 1.15, t + " visible vertical seam: " + wrapH + " vs worst interior " + maxInnerH);
            assertTrue(wrapV <= maxInnerV * 1.15, t + " visible horizontal seam: " + wrapV + " vs worst interior " + maxInnerV);
        }
    }

    @Test
    void terrainsLookDifferentFromEachOther() {
        Terrain[] all = Terrain.values();
        double[] avg = new double[all.length];
        for (int k = 0; k < all.length; k++) {
            BufferedImage img = TerrainTextures.render(all[k], 64);
            long r = 0, g = 0, b = 0;
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int c = img.getRGB(x, y);
                    r += (c >> 16) & 255;
                    g += (c >> 8) & 255;
                    b += c & 255;
                }
            }
            avg[k] = (r * 65536.0 + g * 256.0 + b) / 4096.0;
        }
        Set<Long> seen = new HashSet<>();
        for (double a : avg) assertTrue(seen.add(Math.round(a)), "two terrains render identically");
    }

    @Test
    void sameTerrainRendersTheSameEveryTime() {
        BufferedImage a = TerrainTextures.render(Terrain.GRASS, 128);
        BufferedImage b = TerrainTextures.render(Terrain.GRASS, 128);
        for (int y = 0; y < 128; y += 7) for (int x = 0; x < 128; x += 7) assertEquals(a.getRGB(x, y), b.getRGB(x, y));
    }
}
