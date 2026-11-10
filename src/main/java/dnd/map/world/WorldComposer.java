package dnd.map.world;

import dnd.map.AssetLibrary;
import dnd.model.PaintLayer;
import dnd.model.WorldMap;
import dnd.model.WorldStyle;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

import java.util.HashMap;
import java.util.Map;

/**
 * Puts a whole world map together for somebody to look at who is not editing it: the players' window and the exported
 * pictures. The players' version leaves out everything hidden from them and covers the parts of the map that have not been
 * explored yet; none of the editor's own marks (selection, link badges, the ruler) are ever drawn here.
 */
public final class WorldComposer {
    private WorldComposer() { }

    /** Who the picture is for. */
    public enum Audience { DM, PLAYERS }

    private record Cover(WorldMap map, int version, WorldStyle style, WritableImage image) { }

    private static final Map<String, Cover> COVERS = new HashMap<>();

    /**
     * Draws the map (or the part of it that touches x0..x1, y0..y1, in map cells) with the transform already set to map cells.
     *
     * @param pxPerCell how many output pixels one map cell is (picks how sharp the symbols are)
     * @param grid       draw the grid, if the map has one
     * @param legend     draw the scale bar and the legend, where the map has them turned on
     */
    public static void draw(GraphicsContext g, WorldMap map, AssetLibrary lib, Audience who, double pxPerCell,
                            double x0, double y0, double x1, double y1, boolean grid, boolean legend) {
        boolean dm = who == Audience.DM;
        Image terrain = WorldTerrainCache.of(map).image(x0, y0, x1, y1);
        g.setImageSmoothing(map.style != WorldStyle.RETRO_PIXEL);
        g.drawImage(terrain, 0, 0, map.cols, map.rows);
        WorldPathRenderer.draw(g, map, pxPerCell, x0, y0, x1, y1, dm);
        if (grid) WorldGrid.draw(g, map, pxPerCell, x0, y0, x1, y1);
        WorldStampRenderer.draw(g, map, lib, pxPerCell, x0, y0, x1, y1, dm);
        if (!dm) drawCover(g, map, 1);
        if (legend) WorldLegend.draw(g, map, lib, pxPerCell, !dm);
    }

    /**
     * Covers the parts of the map that are still unexplored with blank parchment. The editor draws it see-through so the DM can
     * still see what lies underneath; for the players it is solid.
     */
    public static void drawCover(GraphicsContext g, WorldMap map, double opacity) {
        if (!map.hideUnexplored) return;
        Image cover = coverImage(map);
        g.save();
        g.beginPath();
        g.rect(0, 0, map.cols, map.rows);
        g.clip();
        g.setImageSmoothing(true);
        g.setGlobalAlpha(opacity);
        g.drawImage(cover, 0, 0, cover.getWidth() * WorldMap.REVEAL_STEP, cover.getHeight() * WorldMap.REVEAL_STEP);
        g.restore();
    }

    /** The picture of the unexplored area: one pixel per reveal cell, solid parchment where unexplored and clear where revealed. */
    private static synchronized Image coverImage(WorldMap map) {
        Cover c = COVERS.get(map.id);
        if (c != null && c.map == map && c.version == map.revealVersion && c.style == map.style
                && c.image.getWidth() == map.revealCols() && c.image.getHeight() == map.revealRows()) {
            return c.image;
        }
        PaintLayer reveal = map.reveal();
        int w = reveal.w, h = reveal.h;
        int rgb = WorldPalette.of(map.style).parchment & 0xffffff;
        int paper = 0xff000000 | rgb;
        int[] px = new int[w * h];
        // clear pixels keep the parchment colour, so the soft edge fades to parchment and not through black
        for (int i = 0; i < px.length; i++) px[i] = reveal.cells[i] != 0 ? rgb : paper;
        WritableImage img = new WritableImage(w, h);
        img.getPixelWriter().setPixels(0, 0, w, h, javafx.scene.image.PixelFormat.getIntArgbInstance(), px, 0, w);
        COVERS.put(map.id, new Cover(map, map.revealVersion, map.style, img));
        return img;
    }
}
