package dnd.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Automatic safety copies of a campaign: when a project is closed (if anything was saved in the session) and just
 * before something is deleted, the whole project is zipped into {@code ~/CampaignPocketbook/.backups/<project>/}. The
 * newest few are kept. A copy can be brought back with Import backup..., which always makes a new project.
 */
public final class ProjectBackups {
    private ProjectBackups() { }

    /** How many backups are kept by default; the settings can change it (see {@link #keep()}). */
    public static final int KEEP = 5;

    /** How many backups of each campaign are kept now (the setting, 1 to 50). */
    public static int keep() {
        return Math.max(1, Math.min(50, dnd.ui.AppSettings.getInt("backup.keep", KEEP)));
    }
    private static final Duration DELETE_BACKUP_GAP = Duration.ofMinutes(10);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");
    private static final Map<Path, Long> LAST_BEFORE_DELETE = new HashMap<>();

    /** The folder holding every project's automatic backups. */
    public static Path root(Path base) {
        return base.resolve(".backups");
    }

    public static Path dirFor(ProjectStore store) {
        return root(store.dir.getParent()).resolve(store.dir.getFileName().toString());
    }

    /**
     * Saves any pending changes and writes a backup zip, then removes the oldest ones beyond {@link #KEEP}.
     *
     * @param force write one even if nothing was saved this session
     * @return the zip, or null if none was written (nothing changed, or it failed; a backup never stops the app)
     */
    public static Path backup(ProjectStore store, boolean force) {
        try {
            store.flush();
            if (!force && !store.savedThisSession()) return null;
            Path dir = dirFor(store);
            Files.createDirectories(dir);
            String stamp = LocalDateTime.now().format(STAMP);
            Path zip = dir.resolve(stamp + ".zip");
            int n = 2;
            while (Files.exists(zip)) zip = dir.resolve(stamp + "-" + n++ + ".zip");
            ProjectArchive.export(store.dir, zip);
            prune(dir);
            return zip;
        } catch (IOException | RuntimeException e) {
            dnd.util.Log.error("Problem in ProjectBackups", e);
            return null;
        }
    }

    /** A backup before a delete, at most one every ten minutes so deleting several things is not slow. */
    public static Path beforeDelete(ProjectStore store) {
        long now = System.currentTimeMillis();
        Long last = LAST_BEFORE_DELETE.get(store.dir);
        if (last != null && now - last < DELETE_BACKUP_GAP.toMillis()) return null;
        Path zip = backup(store, true);
        if (zip != null) LAST_BEFORE_DELETE.put(store.dir, now);
        return zip;
    }

    /** Backups of a project, newest first. */
    public static List<Path> list(ProjectStore store) {
        List<Path> out = new ArrayList<>();
        Path dir = dirFor(store);
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().endsWith(".zip")).forEach(out::add);
        } catch (IOException e) {
            dnd.util.Log.error("Problem in ProjectBackups", e);
        }
        out.sort((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()));
        return out;
    }

    private static void prune(Path dir) throws IOException {
        List<Path> all = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().endsWith(".zip")).forEach(all::add);
        }
        all.sort((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()));
        for (int i = keep(); i < all.size(); i++) Files.deleteIfExists(all.get(i));
    }
}
