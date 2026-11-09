package dnd.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Writes problems to {@code ~/DnDCampaignNotes/app.log} (kept to about 1 MB, plus the previous file) so there is
 * something to look at, or send, when the app misbehaves. If the file cannot be written it falls back to the console.
 */
public final class Log {
    private Log() { }

    private static Logger logger;

    private static synchronized Logger logger() {
        if (logger != null) return logger;
        Logger l = Logger.getLogger("dnd");
        l.setUseParentHandlers(true);
        try {
            Path dir = Paths.get(System.getProperty("user.home"), "DnDCampaignNotes");
            Files.createDirectories(dir);
            FileHandler h = new FileHandler(dir.resolve("app.log").toString(), 1_000_000, 2, true);
            h.setFormatter(new SimpleFormatter());
            l.addHandler(h);
        } catch (IOException | RuntimeException e) {
            System.err.println("Could not open the log file: " + e);
        }
        logger = l;
        return l;
    }

    /** Records an unexpected problem with what was being done. */
    public static void error(String what, Throwable t) {
        logger().log(Level.WARNING, what, t);
    }
}
