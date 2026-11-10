package dnd.ui;

import dnd.storage.ProjectStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * The app's own settings (not a campaign's): one plain text file, {@code settings.properties}, next to the projects. It is
 * read once and cached, every change is written straight back, and anyone can listen for changes so open windows follow a
 * setting the moment it is changed. Values are stored as text; the typed getters fall back to the default for a missing or
 * damaged value, so a bad file never stops the app.
 */
public final class AppSettings {
    private AppSettings() { }

    private static Path override;
    private static Properties cache;
    private static final List<BiConsumer<String, String>> LISTENERS = new CopyOnWriteArrayList<>();

    /** Uses another file for the settings, and forgets what was read (for tests); {@code null} goes back to the normal place. */
    public static synchronized void useFile(Path path) {
        override = path;
        cache = null;
    }

    private static Path file() {
        return override != null ? override : ProjectStore.baseDir().resolve("settings.properties");
    }

    private static synchronized Properties props() {
        if (cache == null) cache = read(file());
        return cache;
    }

    private static Properties read(Path f) {
        Properties p = new Properties();
        if (Files.isRegularFile(f)) {
            try (InputStream in = Files.newInputStream(f)) {
                p.load(in);
            } catch (IOException | IllegalArgumentException ignored) {
                // start with the defaults
            }
        }
        return p;
    }

    private static void save(Properties p) {
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                p.store(out, "Campaign Pocketbook settings");
            }
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            dnd.util.Log.error("Problem in AppSettings", e); // the choice just won't be remembered
        }
    }

    // ---------------------------------------------------------------- reading

    public static synchronized boolean has(String key) {
        return props().getProperty(key) != null;
    }

    public static synchronized String getString(String key, String defaultValue) {
        String v = props().getProperty(key);
        return v == null ? defaultValue : v;
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String v = getString(key, null);
        if (v == null) return defaultValue;
        v = v.trim();
        if (v.equalsIgnoreCase("true")) return true;
        if (v.equalsIgnoreCase("false")) return false;
        return defaultValue;
    }

    public static int getInt(String key, int defaultValue) {
        String v = getString(key, null);
        if (v == null) return defaultValue;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static double getDouble(String key, double defaultValue) {
        String v = getString(key, null);
        if (v == null) return defaultValue;
        try {
            double d = Double.parseDouble(v.trim());
            return Double.isNaN(d) || Double.isInfinite(d) ? defaultValue : d;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Every key that starts with the prefix, sorted. */
    public static synchronized List<String> keys(String prefix) {
        TreeSet<String> out = new TreeSet<>();
        for (String k : props().stringPropertyNames()) if (k.startsWith(prefix)) out.add(k);
        return new ArrayList<>(out);
    }

    // ---------------------------------------------------------------- writing

    public static void setString(String key, String value) {
        if (value == null) {
            remove(key);
            return;
        }
        synchronized (AppSettings.class) {
            Properties p = props();
            if (value.equals(p.getProperty(key))) return;
            p.setProperty(key, value);
            save(p);
        }
        fire(key, value);
    }

    public static void setBoolean(String key, boolean value) {
        setString(key, String.valueOf(value));
    }

    public static void setInt(String key, int value) {
        setString(key, String.valueOf(value));
    }

    public static void setDouble(String key, double value) {
        setString(key, String.valueOf(value));
    }

    /** Forgets a setting, so it goes back to its default. */
    public static void remove(String key) {
        synchronized (AppSettings.class) {
            Properties p = props();
            if (p.getProperty(key) == null) return;
            p.remove(key);
            save(p);
        }
        fire(key, null);
    }

    /** Forgets every setting that starts with the prefix. */
    public static void resetPrefix(String prefix) {
        for (String k : keys(prefix)) remove(k);
    }

    /** Forgets every setting (back to the defaults), except the notes the app keeps about itself ({@code internal.}). */
    public static void resetAll() {
        for (String k : keys("")) if (!k.startsWith("internal.")) remove(k);
    }

    // ---------------------------------------------------------------- moving between computers

    /** Saves all settings to a file anywhere. */
    public static synchronized void exportTo(Path target) throws IOException {
        Properties p = new Properties();
        for (String k : props().stringPropertyNames()) if (!k.startsWith("internal.")) p.setProperty(k, props().getProperty(k));
        try (OutputStream out = Files.newOutputStream(target)) {
            p.store(out, "Campaign Pocketbook settings");
        }
    }

    /** Replaces all settings with those in a file saved by {@link #exportTo}; returns how many were read. */
    public static int importFrom(Path source) throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(source)) {
            p.load(in);
        } catch (IllegalArgumentException e) {
            throw new IOException("That is not a settings file.", e);
        }
        if (p.isEmpty()) throw new IOException("That file has no settings in it.");
        resetAll();
        int n = 0;
        for (String k : p.stringPropertyNames()) {
            if (k.startsWith("internal.")) continue;
            setString(k, p.getProperty(k));
            n++;
        }
        return n;
    }

    // ---------------------------------------------------------------- listening

    /** Calls the listener with (key, new value or null if removed) after every change. Returns something that stops listening. */
    public static Runnable addListener(BiConsumer<String, String> listener) {
        LISTENERS.add(listener);
        return () -> LISTENERS.remove(listener);
    }

    private static void fire(String key, String value) {
        for (BiConsumer<String, String> l : LISTENERS) {
            try {
                l.accept(key, value);
            } catch (RuntimeException e) {
                dnd.util.Log.error("Problem in a settings listener", e);
            }
        }
    }
}
