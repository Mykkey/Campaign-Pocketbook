package dnd.map;

import java.io.IOException;

/** A picture drawn and written a strip at a time (so even a huge one needs little memory); used on the JavaFX thread. */
public interface BandJob extends AutoCloseable {
    int width();

    int height();

    /** How far along the picture is, 0 to 1. */
    double progress();

    /** Draws and writes the next strip; returns true once the whole picture has been written. */
    boolean step() throws IOException;

    /** Finishes the file if every strip was written. */
    @Override
    void close() throws IOException;
}
