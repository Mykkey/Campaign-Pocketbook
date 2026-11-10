package dnd.map;

import dnd.map.world.PngStream;
import dnd.model.BattleMap;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.io.IOException;
import java.io.OutputStream;

/** Saves a battlemap as a PNG picture, drawn in strips like {@link dnd.map.world.WorldExport}. Used on the JavaFX thread. */
public final class BattleMapExport {
    private BattleMapExport() { }

    public static final int MAX_WIDTH = 8192;

    /** What to draw: the DM's version (hidden things faded, fog shaded) or the players' (what they can see), with or without the grid. */
    public record Options(MapRenderer.Audience who, int widthPx, boolean grid) { }

    public static int heightFor(BattleMap map, int widthPx) {
        Grid g = Grid.of(map);
        return Math.max(1, (int) Math.round(g.height() * widthPx / Math.max(1, g.width())));
    }

    static int bandHeight(int widthPx) {
        return Math.max(32, Math.min(512, 3_000_000 / Math.max(1, widthPx)));
    }

    public static final class Job implements BandJob {
        private final BattleMap map;
        private final Grid grid;
        private final AssetLibrary lib;
        private final Options options;
        private final PngStream png;
        private final int width, height, band;
        private final double scale;
        private int done;

        public Job(BattleMap map, AssetLibrary lib, Options options, OutputStream out) throws IOException {
            this.map = map;
            this.grid = Grid.of(map);
            this.lib = lib;
            this.options = options;
            this.width = Math.max(16, Math.min(MAX_WIDTH, options.widthPx()));
            this.height = heightFor(map, width);
            this.band = bandHeight(width);
            this.scale = width / Math.max(1, grid.width());
            this.png = new PngStream(out, width, height);
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public double progress() {
            return (double) done / height;
        }

        public boolean finished() {
            return done >= height;
        }

        @Override
        public boolean step() throws IOException {
            if (finished()) return true;
            int rows = Math.min(band, height - done);
            Canvas canvas = new Canvas(width, rows);
            GraphicsContext g = canvas.getGraphicsContext2D();
            g.setTransform(scale, 0, 0, scale, 0, -done);
            g.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
            MapRenderer.draw(g, map, grid, lib, scale, options.grid(), options.who());
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.web("#5b574d"));
            WritableImage shot = new WritableImage(width, rows);
            canvas.snapshot(params, shot);
            int[] argb = new int[width * rows];
            shot.getPixelReader().getPixels(0, 0, width, rows, PixelFormat.getIntArgbInstance(), argb, 0, width);
            byte[] rgb = new byte[width * rows * 3];
            for (int i = 0, o = 0; i < argb.length; i++) {
                int p = argb[i];
                rgb[o++] = (byte) (p >> 16);
                rgb[o++] = (byte) (p >> 8);
                rgb[o++] = (byte) p;
            }
            png.writeRows(rgb, rows);
            done += rows;
            return finished();
        }

        @Override
        public void close() throws IOException {
            if (finished()) png.close();
        }
    }
}
