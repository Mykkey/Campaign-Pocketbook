package dnd.storage;

import dnd.model.Board;
import dnd.model.Project;
import dnd.model.TreeNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Stream;

/**
 * A project is a folder on disk:
 * <pre>
 *   project.json          name + folder/board tree + open tabs
 *   boards/&lt;id&gt;.json     one file per whiteboard
 *   images/&lt;file&gt;        imported pictures
 * </pre>
 */
public final class ProjectStore {
    private static final String PROJECT_FILE = "project.json";

    public final Path dir;
    public final Project project;
    /** Called (on the calling thread) whenever something becomes dirty; the UI uses it to schedule autosave. */
    public Runnable onDirty = () -> { };

    private final Map<String, Board> cache = new HashMap<>();
    private final Set<String> dirtyBoards = new HashSet<>();
    private boolean projectDirty;

    private ProjectStore(Path dir, Project project) {
        this.dir = dir;
        this.project = project;
    }

    // ---------------------------------------------------------------- project management

    public static Path baseDir() {
        return Paths.get(System.getProperty("user.home"), "DnDCampaignNotes");
    }

    public static List<String> listProjects(Path base) {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(base)) return names;
        try (Stream<Path> s = Files.list(base)) {
            s.filter(p -> Files.isRegularFile(p.resolve(PROJECT_FILE)))
                    .forEach(p -> names.add(p.getFileName().toString()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public static ProjectStore create(Path base, String name) throws IOException {
        String clean = sanitize(name);
        Path dir = base.resolve(clean);
        int n = 2;
        while (Files.exists(dir)) dir = base.resolve(clean + " (" + n++ + ")");
        Files.createDirectories(dir.resolve("boards"));
        Files.createDirectories(dir.resolve("images"));

        Project p = new Project();
        p.name = dir.getFileName().toString();
        p.root = new TreeNode(p.name, true);
        ProjectStore store = new ProjectStore(dir, p);
        Board first = store.createBoard(p.root, "Campaign Overview");
        p.openBoards.add(first.id);
        p.activeBoard = first.id;
        store.flush();
        return store;
    }

    public static ProjectStore open(Path base, String folderName) throws IOException {
        Path dir = base.resolve(folderName);
        Project p = Json.readFile(dir.resolve(PROJECT_FILE), Project.class);
        Files.createDirectories(dir.resolve("boards"));
        Files.createDirectories(dir.resolve("images"));
        return new ProjectStore(dir, p);
    }

    public static void deleteProject(Path base, String folderName) throws IOException {
        Path dir = base.resolve(folderName);
        if (!Files.exists(dir.resolve(PROJECT_FILE))) return; // refuse to delete things that aren't projects
        try (Stream<Path> s = Files.walk(dir)) {
            List<Path> all = new ArrayList<>();
            s.forEach(all::add);
            all.sort(Comparator.reverseOrder());
            for (Path p : all) Files.delete(p);
        }
    }

    static String sanitize(String name) {
        String s = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return s.isEmpty() ? "Campaign" : s;
    }

    // ---------------------------------------------------------------- boards & tree

    private Path boardPath(String id) {
        return dir.resolve("boards").resolve(id + ".json");
    }

    /** Loads (and caches) a board; null if it doesn't exist or can't be read. */
    public Board board(String id) {
        Board b = cache.get(id);
        if (b != null) return b;
        Path p = boardPath(id);
        if (!Files.isRegularFile(p)) return null;
        try {
            b = Json.readFile(p, Board.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        cache.put(id, b);
        return b;
    }

    public boolean boardExists(String id) {
        if (id == null) return false;
        TreeNode n = project.root.find(id);
        return n != null && !n.folder;
    }

    public String boardName(String id) {
        TreeNode n = project.root.find(id);
        return n == null ? "(deleted board)" : n.name;
    }

    public List<String> allBoardIds() {
        List<TreeNode> nodes = new ArrayList<>();
        project.root.collectBoards(nodes);
        List<String> ids = new ArrayList<>();
        for (TreeNode n : nodes) ids.add(n.id);
        return ids;
    }

    public Board createBoard(TreeNode parent, String name) {
        Board b = new Board();
        b.name = name;
        TreeNode n = new TreeNode(name, false);
        n.id = b.id;
        parent.children.add(n);
        cache.put(b.id, b);
        markDirty(b);
        markProjectDirty();
        return b;
    }

    public TreeNode createFolder(TreeNode parent, String name) {
        TreeNode n = new TreeNode(name, true);
        parent.children.add(n);
        markProjectDirty();
        return n;
    }

    public void renameNode(TreeNode node, String name) {
        node.name = name;
        if (!node.folder) {
            Board b = board(node.id);
            if (b != null) {
                b.name = name;
                markDirty(b);
            }
        } else if (node == project.root) {
            project.name = name;
        }
        markProjectDirty();
    }

    /** Deletes a node and everything below it. Links pointing at deleted boards simply become "broken". */
    public void deleteNode(TreeNode node) {
        if (node == project.root) return;
        List<TreeNode> boards = new ArrayList<>();
        node.collectBoards(boards);
        for (TreeNode b : boards) {
            cache.remove(b.id);
            dirtyBoards.remove(b.id);
            try {
                Files.deleteIfExists(boardPath(b.id));
            } catch (IOException e) {
                e.printStackTrace();
            }
            project.openBoards.remove(b.id);
        }
        TreeNode parent = project.root.parentOf(node.id);
        if (parent != null) parent.children.remove(node);
        markProjectDirty();
    }

    /** Moves a node into a folder. Returns false if that would be invalid (e.g. into itself). */
    public boolean moveNode(String nodeId, TreeNode targetFolder) {
        TreeNode node = project.root.find(nodeId);
        if (node == null || node == project.root || !targetFolder.folder) return false;
        if (node.find(targetFolder.id) != null) return false; // into itself or a descendant
        TreeNode parent = project.root.parentOf(nodeId);
        if (parent == null || parent == targetFolder) return false;
        parent.children.remove(node);
        targetFolder.children.add(node);
        markProjectDirty();
        return true;
    }

    // ---------------------------------------------------------------- images

    public Path imagePath(String name) {
        return dir.resolve("images").resolve(name);
    }

    /** Copies an image file into the project and returns the stored file name. */
    public String importImage(Path source) throws IOException {
        String base = sanitize(source.getFileName().toString());
        String name = UUID.randomUUID().toString().substring(0, 8) + "_" + base;
        Files.copy(source, imagePath(name), StandardCopyOption.REPLACE_EXISTING);
        return name;
    }

    // ---------------------------------------------------------------- persistence

    public void markDirty(Board b) {
        dirtyBoards.add(b.id);
        onDirty.run();
    }

    public void markProjectDirty() {
        projectDirty = true;
        onDirty.run();
    }

    /** Writes everything that changed since the last flush. */
    public void flush() {
        try {
            for (String id : new ArrayList<>(dirtyBoards)) {
                Board b = cache.get(id);
                if (b != null) Json.writeFile(boardPath(id), b);
            }
            dirtyBoards.clear();
            if (projectDirty) {
                Json.writeFile(dir.resolve(PROJECT_FILE), project);
                projectDirty = false;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
