package dnd.map.world;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Writes a PNG picture (8-bit colour, no transparency) a few rows at a time, so a huge picture never has to exist in memory
 * all at once. Rows must be written top to bottom, exactly as many as the height.
 */
public final class PngStream implements AutoCloseable {
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};

    private final OutputStream out;
    private final int width, height;
    private final Deflater deflater = new Deflater(6);
    private final DeflaterOutputStream packed;
    private final IdatOut idat = new IdatOut();
    private int rowsWritten;
    private boolean closed;

    public PngStream(OutputStream out, int width, int height) throws IOException {
        if (width < 1 || height < 1) throw new IllegalArgumentException("empty picture");
        this.out = out;
        this.width = width;
        this.height = height;
        out.write(SIGNATURE);
        byte[] ihdr = new byte[13];
        putInt(ihdr, 0, width);
        putInt(ihdr, 4, height);
        ihdr[8] = 8; // bits per channel
        ihdr[9] = 2; // colour type: RGB
        chunk(out, "IHDR", ihdr, 0, ihdr.length);
        packed = new DeflaterOutputStream(idat, deflater, 8192);
    }

    /** Adds rows of pixels, three bytes (red, green, blue) per pixel, left to right. */
    public void writeRows(byte[] rgb, int rows) throws IOException {
        if (rowsWritten + rows > height) throw new IllegalStateException("more rows than the picture is tall");
        int stride = width * 3;
        for (int r = 0; r < rows; r++) {
            packed.write(0); // no row filter
            packed.write(rgb, r * stride, stride);
        }
        rowsWritten += rows;
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        try {
            if (rowsWritten != height) throw new IllegalStateException("picture is not finished: " + rowsWritten + " of " + height + " rows");
            packed.finish();
            idat.flushChunk();
            chunk(out, "IEND", new byte[0], 0, 0);
            out.flush();
        } finally {
            deflater.end();
        }
    }

    /** Collects the compressed data and writes it out as IDAT chunks. */
    private final class IdatOut extends OutputStream {
        private final byte[] buf = new byte[32 * 1024];
        private int n;

        @Override
        public void write(int b) throws IOException {
            if (n == buf.length) flushChunk();
            buf[n++] = (byte) b;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            while (len > 0) {
                if (n == buf.length) flushChunk();
                int k = Math.min(len, buf.length - n);
                System.arraycopy(b, off, buf, n, k);
                n += k;
                off += k;
                len -= k;
            }
        }

        @Override
        public void flush() throws IOException {
            flushChunk();
        }

        private void flushChunk() throws IOException {
            if (n == 0) return;
            chunk(out, "IDAT", buf, 0, n);
            n = 0;
        }
    }

    private static void chunk(OutputStream out, String type, byte[] data, int off, int len) throws IOException {
        byte[] head = new byte[8];
        putInt(head, 0, len);
        byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(t, 0, head, 4, 4);
        CRC32 crc = new CRC32();
        crc.update(t);
        crc.update(data, off, len);
        byte[] tail = new byte[4];
        putInt(tail, 0, (int) crc.getValue());
        out.write(head);
        out.write(data, off, len);
        out.write(tail);
    }

    private static void putInt(byte[] b, int at, int v) {
        b[at] = (byte) (v >>> 24);
        b[at + 1] = (byte) (v >>> 16);
        b[at + 2] = (byte) (v >>> 8);
        b[at + 3] = (byte) v;
    }
}
