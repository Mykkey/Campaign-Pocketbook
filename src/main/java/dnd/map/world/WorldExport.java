package dnd.map.world;

import dnd.map.AssetLibrary;
import dnd.model.WorldMap;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Saves a world map as a PNG picture. The picture is drawn in horizontal bands and written out as it goes, so even a huge
 * export needs only a little memory. A job does one band per {@link Job#step()}, so the window can stay alive between bands.
 * Must be used on the JavaFX thread.
 */
public final class WorldExport {
    private WorldExport() { }

    /** Widest picture offered (the drawing surface cannot be wider than this on most machines). */
    public static final int MAX_WIDTH = 8192;

    /** What to draw. */
    public record Options(WorldComposer.Audience who, int widthPx, boolean grid, boolean legend) { }

    public static int heightFor(WorldMap map, int widthPx) {
        return Math.max(1, (int) Math.round(map.rows * (double) widthPx / map.cols));
    }

    /** How many rows of pixels are drawn at once. */
    static int bandHeight(int widthPx) {
        return Math.max(32, Math.min(512, 3_000_000 / Math.max(1, widthPx)));
    }

    /** One export in progress. */
    public static final class Job implements dnd.map.BandJob {
        private final WorldMap map;
        private final AssetLibrary lib;
        private final Options options;
        private final PngStream png;
        private final int width, height, band;
        private final double scale;
        private int done;

        public Job(WorldMap map, AssetLibrary lib, Options options, OutputStream out) throws IOException {
            this.map = map;
            this.lib = lib;
            this.options = options;
            this.width = Math.max(16, Math.min(MAX_WIDTH, options.widthPx()));
            this.height = heightFor(map, width);
            this.band = bandHeight(width);
            this.scale = (double) width / map.cols;
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

        /** How far along the picture is, 0 to 1. */
        @Override
        public double progress() {
            return (double) done / height;
        }

        public boolean finished() {
            return done >= height;
        }

        /** Draws and writes the next band; returns true once the whole picture has been written. */
        @Override
        public boolean step() throws IOException {
            if (finished()) return true;
            int rows = Math.min(band, height - done);
            Canvas canvas = new Canvas(width, rows);
            GraphicsContext g = canvas.getGraphicsContext2D();
            g.setTransform(scale, 0, 0, scale, 0, -done);
            double y0 = done / scale, y1 = (done + rows) / scale;
            WorldComposer.draw(g, map, lib, options.who(), scale, 0, y0, map.cols, y1, options.grid(), options.legend());
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.web("#a79f89"));
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

        /** Finishes the file if every band was written; the output stream is left for the caller to close. */
        @Override
        public void close() throws IOException {
            if (finished()) png.close();
        }
    }
}
