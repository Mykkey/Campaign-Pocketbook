package dnd.storage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dnd.model.TreeNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The deleted items of one project: what is deleted from the project tree is moved here (its files into {@code trash/files/},
 * and a note of what it was and where it came from into {@code trash/index.json}) instead of being erased, so it can be brought
 * back. Anything left in the bin for longer than {@link #KEEP_DAYS} is removed for good when the project is opened.
 */
public final class TrashBin {
    /** How long a deleted item stays in the bin. */
    public static final int KEEP_DAYS = 30;

    /** One deleted item (or folder with everything in it). */
    public static class Entry {
        public String id = UUID.randomUUID().toString();
        /** The deleted node, with everything below it if it was a folder. */
        public TreeNode node;
        /** The folder it was in, and its place among that folder's children. */
        public String parentId;
        public int index;
        public long deletedAt;
        /** The files that were moved here, as paths inside the project folder ("cards/abc.card"). */
        public List<String> files = new ArrayList<>();

        public Entry() { }

        /** How many items (not folders) this holds. */
        @JsonIgnore
        public int itemCount() {
            List<TreeNode> items = new ArrayList<>();
            if (node != null) node.collectItems(items);
            return items.size();
        }
    }

    /** The file {@code trash/index.json}. */
    public static class Index {
        public List<Entry> entries = new ArrayList<>();
    }

    private final Path projectDir;
    private Index index;

    public TrashBin(Path projectDir) {
        this.projectDir = projectDir;
    }

    private Path dir() {
        return projectDir.resolve("trash");
    }

    private Path filesDir() {
        return dir().resolve("files");
    }

    private Path indexFile() {
        return dir().resolve("index.json");
    }

    private Index load() {
        if (index == null) {
            index = new Index();
            if (Files.isRegularFile(indexFile())) {
                try {
                    Index read = Json.readFile(indexFile(), Index.class);
                    if (read != null && read.entries != null) index = read;
                } catch (IOException | RuntimeException e) {
                    dnd.util.Log.error("Could not read the bin of deleted items", e); // an unreadable note just means an empty bin
                }
            }
        }
        return index;
    }

    private void save() {
        try {
            Files.createDirectories(dir());
            Json.writeFile(indexFile(), load());
        } catch (IOException e) {
            dnd.util.Log.error("Could not save the bin of deleted items", e);
        }
    }

    /** What is in the bin, most recently deleted first. */
    public List<Entry> entries() {
        List<Entry> out = new ArrayList<>(load().entries);
        out.sort(Comparator.comparingLong((Entry e) -> e.deletedAt).reversed());
        return out;
    }

    public boolean isEmpty() {
        return load().entries.isEmpty();
    }

    public Entry find(String entryId) {
        for (Entry e : load().entries) if (e.id.equals(entryId)) return e;
        return null;
    }

    /**
     * Puts a deleted node in the bin, moving its files here.
     *
     * @param files the item files to move: absolute paths inside the project folder; ones that do not exist are skipped
     */
    public Entry add(TreeNode node, String parentId, int index, List<Path> files, long now) {
        Entry e = new Entry();
        e.node = node;
        e.parentId = parentId;
        e.index = index;
        e.deletedAt = now;
        for (Path f : files) {
            if (!Files.isRegularFile(f)) continue;
            String rel = projectDir.relativize(f).toString().replace('\\', '/');
            try {
                Path target = filesDir().resolve(rel);
                Files.createDirectories(target.getParent());
                Files.move(f, target, StandardCopyOption.REPLACE_EXISTING);
                e.files.add(rel);
            } catch (IOException ex) {
                dnd.util.Log.error("Could not move a deleted file to the bin", ex);
            }
        }
        load().entries.add(e);
        save();
        return e;
    }

    /** Moves an entry's files back to where they were and takes it out of the bin. Files that cannot be put back are left in the bin's folder. */
    public void restoreFiles(Entry e) {
        for (String rel : e.files) {
            Path from = filesDir().resolve(rel), to = projectDir.resolve(rel);
            try {
                if (!Files.isRegularFile(from) || Files.exists(to)) continue;
                Files.createDirectories(to.getParent());
                Files.move(from, to);
            } catch (IOException ex) {
                dnd.util.Log.error("Could not put a file back from the bin", ex);
            }
        }
        load().entries.remove(e);
        save();
        cleanEmptyFolders();
    }

    /** Erases an entry and its files for good. */
    public void deleteForever(Entry e) {
        for (String rel : e.files) {
            try {
                Files.deleteIfExists(filesDir().resolve(rel));
            } catch (IOException ex) {
                dnd.util.Log.error("Could not erase a file from the bin", ex);
            }
        }
        load().entries.remove(e);
        save();
        cleanEmptyFolders();
    }

    public void empty() {
        for (Entry e : new ArrayList<>(load().entries)) deleteForever(e);
    }

    /** Erases what has been in the bin longer than {@code days} days; returns how many entries went. */
    public int purgeOlderThan(int days, long now) {
        long cutoff = now - days * 24L * 60 * 60 * 1000;
        int n = 0;
        for (Entry e : new ArrayList<>(load().entries)) {
            if (e.deletedAt < cutoff) {
                deleteForever(e);
                n++;
            }
        }
        return n;
    }

    private void cleanEmptyFolders() {
        if (!Files.isDirectory(filesDir())) return;
        try (Stream<Path> s = Files.walk(filesDir())) {
            s.sorted(Comparator.reverseOrder()).filter(Files::isDirectory).filter(p -> !p.equals(filesDir())).forEach(p -> {
                try {
                    Files.deleteIfExists(p); // only succeeds when empty
                } catch (IOException ignored) {
                    // not empty: leave it
                }
            });
        } catch (IOException ignored) {
            // tidying is optional
        }
    }
}
