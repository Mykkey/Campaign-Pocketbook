package dnd.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The folder where the app keeps everything of the user's (projects, backups, libraries, settings, the log).
 * It used to be {@code ~/DnDCampaignNotes}; on first start after the rename to Campaign Pocketbook an existing old
 * folder is moved to {@code ~/CampaignPocketbook} so nothing is lost.
 */
public final class AppHome {
    private AppHome() { }

    public static final String NAME = "CampaignPocketbook";
    public static final String OLD_NAME = "DnDCampaignNotes";

    private static Path dir;

    /** The data folder of the current user (moving the old one first, once). */
    public static synchronized Path dir() {
        if (dir == null) dir = resolve(Paths.get(System.getProperty("user.home")));
        return dir;
    }

    /**
     * The data folder inside {@code home}: the new one, after moving the old one to its place if only the old one exists.
     * If the old folder cannot be moved (something has it open) it is used as it is, so nothing is ever lost or hidden.
     */
    public static Path resolve(Path home) {
        Path fresh = home.resolve(NAME), old = home.resolve(OLD_NAME);
        if (!Files.exists(fresh) && Files.isDirectory(old)) {
            try {
                Files.move(old, fresh);
            } catch (IOException | RuntimeException e) {
                System.err.println("Could not move " + old + " to " + fresh + ": " + e);
                return old;
            }
        }
        return fresh;
    }
}
