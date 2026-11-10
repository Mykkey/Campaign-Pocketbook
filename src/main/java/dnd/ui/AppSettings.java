package dnd.ui;

import dnd.storage.ProjectStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Small remembered preferences, kept in {@code settings.properties} next to the projects (the theme lives there too). */
public final class AppSettings {
    private AppSettings() { }

    private static Path file() {
        return ProjectStore.baseDir().resolve("settings.properties");
    }

    private static Properties load() {
        Properties p = new Properties();
        Path f = file();
        if (Files.isRegularFile(f)) {
            try (InputStream in = Files.newInputStream(f)) {
                p.load(in);
            } catch (IOException ignored) {
                // start with the defaults
            }
        }
        return p;
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String v = load().getProperty(key);
        return v == null ? defaultValue : Boolean.parseBoolean(v);
    }

    public static void setBoolean(String key, boolean value) {
        Properties p = load();
        p.setProperty(key, String.valueOf(value));
        try {
            Files.createDirectories(file().getParent());
            try (OutputStream out = Files.newOutputStream(file())) {
                p.store(out, "Campaign Pocketbook settings");
            }
        } catch (IOException e) {
            dnd.util.Log.error("Problem in AppSettings", e); // the choice just won't be remembered
        }
    }
}