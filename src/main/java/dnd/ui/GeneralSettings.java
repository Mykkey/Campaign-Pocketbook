package dnd.ui;

/**
 * The behaviour settings of the app in one place, each with its default and its safe limits, so every part of the app asks
 * here instead of using a number of its own. The values live in {@link AppSettings}.
 */
public final class GeneralSettings {
    private GeneralSettings() { }

    public static final String AUTOSAVE_MS = "general.autosaveMs";
    public static final String UNDO_DEPTH = "general.undoDepth";
    public static final String HOVER_DELAY = "general.hoverDelayMs";
    public static final String CONFIRM_DELETE = "general.confirmDelete";
    public static final String REOPEN_LAST = "general.reopenLast";
    public static final String LAST_PROJECT = "general.lastProject";
    public static final String UPDATE_CHECK = "update.check";
    public static final String BACKUP_ON_CLOSE = "backup.onClose";
    public static final String BACKUP_KEEP = "backup.keep";

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** How long after a change (milliseconds) the campaign is saved; default 1 second. */
    public static int autosaveMs() {
        return clamp(AppSettings.getInt(AUTOSAVE_MS, 1000), 200, 60_000);
    }

    /** How many steps undo remembers: the chosen number, or {@code standard} (what that editor always used) if none was chosen. */
    public static int undoDepth(int standard) {
        int v = AppSettings.getInt(UNDO_DEPTH, 0);
        return v <= 0 ? standard : clamp(v, 10, 2000);
    }

    /** How long (milliseconds) the pointer must rest on something with a note or link before its preview shows. */
    public static int hoverDelayMs(int standard) {
        int v = AppSettings.getInt(HOVER_DELAY, 0);
        return v <= 0 ? standard : clamp(v, 100, 3000);
    }

    /** Whether to ask before deleting an item from the project tree (a backup is made either way). */
    public static boolean confirmDelete() {
        return AppSettings.getBoolean(CONFIRM_DELETE, true);
    }

    /** Whether to open the campaign that was open last time straight away instead of showing the project list. */
    public static boolean reopenLast() {
        return AppSettings.getBoolean(REOPEN_LAST, false);
    }

    /** Whether the app looks for a newer version when it starts. */
    public static boolean updateCheck() {
        return AppSettings.getBoolean(UPDATE_CHECK, true);
    }

    /** Whether a backup is made when a campaign is closed. (A backup before deleting something is always made.) */
    public static boolean backupOnClose() {
        return AppSettings.getBoolean(BACKUP_ON_CLOSE, true);
    }

    /** How many automatic backups of each campaign are kept (1 to 50; the default is 5). */
    public static int backupKeep() {
        return clamp(AppSettings.getInt(BACKUP_KEEP, 5), 1, 50);
    }
}
