package dnd.npc;

import dnd.npc.NpcData.Profession;
import dnd.storage.Json;
import dnd.storage.ProjectStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The DM's own professions, kept beside the projects ({@code npc-professions.json}) so every project offers them
 * next to the built-in ones. A custom profession cannot reuse the name of a built-in one.
 */
public final class CustomProfessions {
    private static CustomProfessions shared;

    private final Path file;
    private List<Profession> custom;

    public CustomProfessions(Path file) {
        this.file = file;
    }

    public static synchronized CustomProfessions shared() {
        if (shared == null) shared = new CustomProfessions(ProjectStore.baseDir().resolve("npc-professions.json"));
        return shared;
    }

    /** File form: plain fields, so older and newer versions of the app can read each other's files. */
    public static class Entry {
        public String name = "";
        public String category = "";
        public String template = "commoner";
        public List<String> knows = new ArrayList<>();
    }

    public static class FileData {
        public List<Entry> professions = new ArrayList<>();
    }

    private synchronized List<Profession> load() {
        if (custom != null) return custom;
        custom = new ArrayList<>();
        if (Files.isRegularFile(file)) {
            try {
                for (Entry e : Json.readFile(file, FileData.class).professions) {
                    if (e.name != null && !e.name.isBlank()) custom.add(toProfession(e));
                }
            } catch (IOException ex) {
                ex.printStackTrace(); // a damaged file just means no custom professions
            }
        }
        return custom;
    }

    private static Profession toProfession(Entry e) {
        return new Profession(e.name.trim(), e.category == null || e.category.isBlank() ? "Custom" : e.category.trim(),
                e.template == null || e.template.isBlank() ? "commoner" : e.template, List.copyOf(e.knows == null ? List.of() : e.knows));
    }

    private synchronized void save() {
        FileData data = new FileData();
        for (Profession p : load()) {
            Entry e = new Entry();
            e.name = p.name();
            e.category = p.category();
            e.template = p.template();
            e.knows = new ArrayList<>(p.knows());
            data.professions.add(e);
        }
        try {
            Files.createDirectories(file.getParent());
            Json.writeFile(file, data);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** Only the DM's own professions. */
    public synchronized List<Profession> custom() {
        return new ArrayList<>(load());
    }

    /** Built-in professions followed by the DM's own. */
    public synchronized List<Profession> all() {
        List<Profession> out = new ArrayList<>(NpcData.PROFESSIONS);
        out.addAll(load());
        return out;
    }

    public static boolean isBuiltIn(String name) {
        for (Profession p : NpcData.PROFESSIONS) if (p.name().equalsIgnoreCase(name == null ? "" : name.trim())) return true;
        return false;
    }

    /**
     * Adds a profession, or replaces the custom one called {@code replacing} (pass null to add).
     *
     * @throws IllegalArgumentException if the name is blank, clashes with a built-in or another custom profession
     */
    public synchronized void put(String replacing, Profession p) {
        String name = p.name() == null ? "" : p.name().trim();
        if (name.isEmpty()) throw new IllegalArgumentException("A profession needs a name.");
        if (isBuiltIn(name)) throw new IllegalArgumentException("\"" + name + "\" is already a built-in profession.");
        List<Profession> list = load();
        for (Profession other : list) {
            boolean same = replacing != null && other.name().equalsIgnoreCase(replacing);
            if (!same && other.name().equalsIgnoreCase(name)) throw new IllegalArgumentException("You already have a profession called \"" + name + "\".");
        }
        Profession clean = new Profession(name, p.category() == null || p.category().isBlank() ? "Custom" : p.category().trim(),
                p.template() == null || p.template().isBlank() ? "commoner" : p.template(), List.copyOf(p.knows()));
        if (replacing != null) list.removeIf(x -> x.name().equalsIgnoreCase(replacing));
        list.add(clean);
        save();
    }

    public synchronized void remove(String name) {
        load().removeIf(x -> x.name().equalsIgnoreCase(name));
        save();
    }
}