package dnd.model;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * One layer of a world map painted cell by cell: a grid of small numbers (0 = nothing painted). It is saved as
 * deflate-compressed, Base64 text, which is tiny for maps made of large areas of the same thing.
 */
public final class PaintLayer {
    public final int w, h;
    public final byte[] cells;

    public PaintLayer(int w, int h) {
        this.w = w;
        this.h = h;
        this.cells = new byte[w * h];
    }

    public boolean inside(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h;
    }

    public byte get(int x, int y) {
        return inside(x, y) ? cells[y * w + x] : 0;
    }

    /** Sets a cell; returns true if its value changed. */
    public boolean set(int x, int y, byte value) {
        if (!inside(x, y)) return false;
        int i = y * w + x;
        if (cells[i] == value) return false;
        cells[i] = value;
        return true;
    }

    public PaintLayer copy() {
        PaintLayer c = new PaintLayer(w, h);
        System.arraycopy(cells, 0, c.cells, 0, cells.length);
        return c;
    }

    /** A copy of the cells inside a rectangle, row by row (for undo patches). */
    public byte[] copyRect(int x, int y, int rw, int rh) {
        byte[] out = new byte[rw * rh];
        for (int j = 0; j < rh; j++) System.arraycopy(cells, (y + j) * w + x, out, j * rw, rw);
        return out;
    }

    /** Puts cells saved by {@link #copyRect} back. */
    public void putRect(int x, int y, int rw, int rh, byte[] data) {
        for (int j = 0; j < rh; j++) System.arraycopy(data, j * rw, cells, (y + j) * w + x, rw);
    }

    /** True if no cell has been painted. */
    public boolean isEmpty() {
        for (byte b : cells) if (b != 0) return false;
        return true;
    }

    public String encode() {
        Deflater d = new Deflater(Deflater.BEST_SPEED);
        d.setInput(cells);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, cells.length / 8));
        byte[] buf = new byte[16 * 1024];
        while (!d.finished()) out.write(buf, 0, d.deflate(buf));
        d.end();
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    /** Reads a layer saved by {@link #encode}; a missing or damaged string gives an empty layer, and a size change is tolerated. */
    public static PaintLayer decode(int w, int h, String data) {
        PaintLayer layer = new PaintLayer(w, h);
        if (data == null || data.isBlank()) return layer;
        Inflater inf = new Inflater();
        try {
            inf.setInput(Base64.getDecoder().decode(data));
            int total = 0;
            while (total < layer.cells.length && !inf.finished()) {
                int n = inf.inflate(layer.cells, total, layer.cells.length - total);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break;
                total += n;
            }
        } catch (DataFormatException | IllegalArgumentException e) {
            return new PaintLayer(w, h);
        } finally {
            inf.end();
        }
        return layer;
    }
}
