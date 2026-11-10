package dnd.map.world;

import dnd.map.AssetLibrary;
import dnd.model.WorldMap;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/** A small picture of a whole world map (land, sea and symbols), for previews. Must be called on the JavaFX thread. */
public final class WorldThumbnail {
    private WorldThumbnail() { }

    public static Image of(WorldMap map, AssetLibrary lib, int widthPx) {
        double scale = (double) widthPx / map.cols;
        int heightPx = Math.max(1, (int) Math.round(map.rows * scale));
        Canvas canvas = new Canvas(widthPx, heightPx);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#a79f89"));
        g.fillRect(0, 0, widthPx, heightPx);
        g.setImageSmoothing(true);
        g.drawImage(WorldTerrainCache.of(map).fullImage(), 0, 0, widthPx, heightPx);
        g.save();
        g.scale(scale, scale);
        WorldPathRenderer.draw(g, map, scale, 0, 0, map.cols, map.rows, false);
        WorldStampRenderer.draw(g, map, lib, scale, 0, 0, map.cols, map.rows, false);
        g.restore();
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        WritableImage out = new WritableImage(widthPx, heightPx);
        canvas.snapshot(params, out);
        return out;
    }
}
