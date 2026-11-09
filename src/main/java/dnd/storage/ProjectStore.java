package dnd.storage;

import dnd.model.BattleMap;
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
 *   maps/&lt;id&gt;.battlemap  one file per battlemap
 *   assets/&lt;file&gt;        imported SVG/PNG pieces for battlemaps
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
    private final Map<String, BattleMap> mapCache = new HashMap<>();
    private final Set<String> dirtyMaps = new HashSet<>();
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
        createSubfolders(dir);

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
        createSubfolders(dir);
        return new ProjectStore(dir, p);
    }

    private static void createSubfolders(Path dir) throws IOException {
        Files.createDirectories(dir.resolve("boards"));
        Files.createDirectories(dir.resolve("maps"));
        Files.createDirectories(dir.resolve("images"));
        Files.createDirectories(dir.resolve("assets"));
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

    /** True if {@code id} is a whiteboard. */
    public boolean boardExists(String id) {
        if (id == null) return false;
        TreeNode n = project.root.find(id);
        return n != null && n.isBoard();
    }

    /** True if {@code id} is a whiteboard or a battlemap (what links may point at). */
    public boolean itemExists(String id) {
        if (id == null) return false;
        TreeNode n = project.root.find(id);
        return n != null && !n.folder;
    }

    public boolean isBattleMap(String id) {
        TreeNode n = id == null ? null : project.root.find(id);
        return n != null && n.isBattleMap();
    }

    public String boardName(String id) {
        TreeNode n = project.root.find(id);
        return n == null ? "(deleted item)" : n.name;
    }

    public String itemName(String id) {
        return boardName(id);
    }

    // ---------------------------------------------------------------- battlemaps

    private Path mapPath(String id) {
        return dir.resolve("maps").resolve(id + ".battlemap");
    }

    /** Loads (and caches) a battlemap; null if it doesn't exist or can't be read. */
    public BattleMap battleMap(String id) {
        BattleMap m = mapCache.get(id);
        if (m != null) return m;
        Path p = mapPath(id);
        if (!Files.isRegularFile(p)) return null;
        try {
            m = Json.readFile(p, BattleMap.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        mapCache.put(id, m);
        return m;
    }

    public BattleMap createBattleMap(TreeNode parent, String name, BattleMap.GridType type, int cols, int rows, double cellSize) {
        BattleMap m = new BattleMap();
        m.name = name;
        m.gridType = type;
        m.cols = cols;
        m.rows = rows;
        m.cellSize = cellSize;
        TreeNode n = new TreeNode(name, false);
        n.id = m.id;
        n.kind = TreeNode.KIND_BATTLEMAP;
        parent.children.add(n);
        mapCache.put(m.id, m);
        markMapDirty(m);
        markProjectDirty();
        return m;
    }

    public List<String> allMapIds() {
        List<TreeNode> nodes = new ArrayList<>();
        project.root.collectMaps(nodes);
        List<String> ids = new ArrayList<>();
        for (TreeNode n : nodes) ids.add(n.id);
        return ids;
    }

    /** Marks a map as changed: schedules a save and bumps its revision so previews refresh. */
    public void markMapDirty(BattleMap m) {
        m.revision++;
        dirtyMaps.add(m.id);
        onDirty.run();
    }

    // ---------------------------------------------------------------- imported assets

    public Path assetsDir() {
        return dir.resolve("assets");
    }

    /**
     * Reads everything needed to show the tabs that were open last time (their files and the piece library).
     * Safe to call on a background thread; the results are cached for when the UI is built.
     */
    public void preload() {
        for (String id : new ArrayList<>(project.openBoards)) {
            if (isBattleMap(id)) battleMap(id);
            else if (boardExists(id)) board(id);
        }
        assets();
    }

    private dnd.map.AssetLibrary assetLibrary;

    /** The shared asset library for this project (built-ins plus the project's imports). */
    public dnd.map.AssetLibrary assets() {
        if (assetLibrary == null) assetLibrary = new dnd.map.AssetLibrary(assetsDir());
        return assetLibrary;
    }

    /** Copies an SVG/PNG/JPG into the project's assets folder and returns the stored file name. */
    public String importAsset(Path source) throws IOException {
        String base = sanitize(source.getFileName().toString());
        Path target = assetsDir().resolve(base);
        int n = 2;
        while (Files.exists(target)) {
            int dot = base.lastIndexOf('.');
            String stem = dot > 0 ? base.substring(0, dot) : base;
            String ext = dot > 0 ? base.substring(dot) : "";
            target = assetsDir().resolve(stem + " (" + n++ + ")" + ext);
        }
        Files.copy(source, target);
        return target.getFileName().toString();
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
        if (node.isBoard()) {
            Board b = board(node.id);
            if (b != null) {
                b.name = name;
                markDirty(b);
            }
        } else if (node.isBattleMap()) {
            BattleMap m = battleMap(node.id);
            if (m != null) {
                m.name = name;
                markMapDirty(m);
            }
        } else if (node == project.root) {
            project.name = name;
        }
        markProjectDirty();
    }

    /** Deletes a node and everything below it. Links pointing at deleted boards simply become "broken". */
    public void deleteNode(TreeNode node) {
        if (node == project.root) return;
        List<TreeNode> items = new ArrayList<>();
        node.collectItems(items);
        for (TreeNode b : items) {
            cache.remove(b.id);
            dirtyBoards.remove(b.id);
            mapCache.remove(b.id);
            dirtyMaps.remove(b.id);
            try {
                Files.deleteIfExists(boardPath(b.id));
                Files.deleteIfExists(mapPath(b.id));
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
            for (String id : new ArrayList<>(dirtyMaps)) {
                BattleMap m = mapCache.get(id);
                if (m != null) Json.writeFile(mapPath(id), m);
            }
            dirtyMaps.clear();
            if (projectDirty) {
                Json.writeFile(dir.resolve(PROJECT_FILE), project);
                projectDirty = false;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
