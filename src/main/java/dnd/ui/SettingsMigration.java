package dnd.ui;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Earlier versions kept a few remembered choices (the choices in the export dialogs, a dismissed update) in the system's
 * preferences store (the registry on Windows). They now live in {@code settings.properties} with everything else; this
 * copies the old values over once, so nobody has to choose them again. Failures are ignored: it must never stop the app.
 */
public final class SettingsMigration {
    private SettingsMigration() { }

    private static final String DONE = "internal.migratedPrefs";

    /** The old preferences node of the UI package (where the app used to remember things). */
    public static Preferences oldRoot() {
        return Preferences.userRoot().node("dnd/ui");
    }

    /** Copies the old values from the given preferences node into the settings, unless that was already done. */
    public static void run(Preferences old) {
        if (AppSettings.getBoolean(DONE, false)) return;
        try {
            copyString(old, "dismissedUpdate", "update.dismissed");
            copyNode(old, "battleExport", "export.battle.", new String[]{"dir"}, new String[]{"width"}, new String[]{"players"});
            copyNode(old, "worldExport", "export.world.", new String[]{"dir"}, new String[]{"width"}, new String[]{"players", "grid", "legend"});
            copyNode(old, "itemExport", "export.item.", new String[]{"dir"}, new String[0], new String[0]);
        } catch (RuntimeException | BackingStoreException ignored) {
            // the old values are only a convenience
        }
        AppSettings.setBoolean(DONE, true);
    }

    private static void copyString(Preferences node, String oldKey, String newKey) {
        String v = node.get(oldKey, null);
        if (v != null && !v.isEmpty() && !AppSettings.has(newKey)) AppSettings.setString(newKey, v);
    }

    private static void copyNode(Preferences parent, String name, String prefix, String[] strings, String[] ints, String[] booleans)
            throws BackingStoreException {
        if (!parent.nodeExists(name)) return;
        Preferences node = parent.node(name);
        for (String k : strings) copyString(node, k, prefix + k);
        for (String k : ints) {
            String v = node.get(k, null);
            if (v != null && !AppSettings.has(prefix + k)) {
                try {
                    AppSettings.setInt(prefix + k, Integer.parseInt(v.trim()));
                } catch (NumberFormatException ignored) {
                    // a damaged old value is just skipped
                }
            }
        }
        for (String k : booleans) {
            String v = node.get(k, null);
            if (v != null && !AppSettings.has(prefix + k)) AppSettings.setBoolean(prefix + k, Boolean.parseBoolean(v));
        }
    }
}
