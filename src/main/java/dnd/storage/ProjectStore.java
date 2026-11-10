package dnd.storage;

import dnd.model.BattleMap;
import dnd.model.Board;
import dnd.model.Card;
import dnd.model.MagicItem;
import dnd.model.ProjectEntry;
import dnd.model.Quest;
import dnd.model.Session;
import dnd.model.WorldMap;
import dnd.model.Encounter;
import dnd.model.Spell;
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
 *   cards/&lt;id&gt;.card      one file per character / creature card
 *   encounters/&lt;id&gt;.encounter  one file per planned fight
 *   spells/&lt;id&gt;.spell        one file per spell card
 *   items/&lt;id&gt;.item          one file per magic item card
 *   quests/&lt;id&gt;.quest        one file per quest
 *   sessions/&lt;id&gt;.session    one file per session log
 *   worldmaps/&lt;id&gt;.worldmap  one file per world map
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
    private final Map<String, Card> cardCache = new HashMap<>();
    private final Set<String> dirtyCards = new HashSet<>();
    private final Map<String, Encounter> encounterCache = new HashMap<>();
    private final Set<String> dirtyEncounters = new HashSet<>();
    private final Map<String, Spell> spellCache = new HashMap<>();
    private final Set<String> dirtySpells = new HashSet<>();
    private int namesVersion;
    private boolean projectDirty;
    private boolean savedThisSession;
    /** Why an item's file could not be read (item id -> file and message); the file itself is left untouched. */
    private final Map<String, String> loadErrors = new HashMap<>();

    /** The kinds of item that are plain "one JSON file each" entries (see ProjectEntry). */
    private final List<Kind<?>> kinds = new ArrayList<>();
    private final Kind<dnd.model.MagicItem> magicItems = register(new Kind<>("items", "item", TreeNode.KIND_ITEM, dnd.model.MagicItem.class));
    private final Kind<Quest> quests = register(new Kind<>("quests", "quest", TreeNode.KIND_QUEST, Quest.class));
    private final Kind<Session> sessions = register(new Kind<>("sessions", "session", TreeNode.KIND_SESSION, Session.class));
    private final Kind<WorldMap> worldMaps = register(new Kind<>("worldmaps", "worldmap", TreeNode.KIND_WORLDMAP, WorldMap.class));

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
        Files.createDirectories(dir.resolve("cards"));
        Files.createDirectories(dir.resolve("encounters"));
        Files.createDirectories(dir.resolve("spells"));
        Files.createDirectories(dir.resolve("items"));
        Files.createDirectories(dir.resolve("quests"));
        Files.createDirectories(dir.resolve("sessions"));
        Files.createDirectories(dir.resolve("worldmaps"));
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
            loadFailed(id, p, e);
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

    /** The name of the item with this id, or null if there is no such item (deleted, or never existed). */
    public String itemNameOrNull(String id) {
        TreeNode n = id == null ? null : project.root.find(id);
        return n == null ? null : n.name;
    }

    // ---------------------------------------------------------------- cards

    public boolean isCard(String id) {
        TreeNode n = id == null ? null : project.root.find(id);
        return n != null && n.isCard();
    }

    private Path cardPath(String id) {
        return dir.resolve("cards").resolve(id + ".card");
    }

    /** Loads (and caches) a card; null if it doesn't exist or can't be read. */
    public Card card(String id) {
        Card c = cardCache.get(id);
        if (c != null) return c;
        Path p = cardPath(id);
        if (!Files.isRegularFile(p)) return null;
        try {
            c = Json.readFile(p, Card.class);
        } catch (IOException e) {
            loadFailed(id, p, e);
            return null;
        }
        cardCache.put(id, c);
        return c;
    }

    /** Adds a card to the project under {@code parent}. The card gets a fresh id and becomes the tree node's id. */
    public Card createCard(TreeNode parent, Card card) {
        Card c = card;
        c.id = java.util.UUID.randomUUID().toString();
        TreeNode n = new TreeNode(c.name, false);
        n.id = c.id;
        n.kind = TreeNode.KIND_CARD;
        parent.children.add(n);
        cardCache.put(c.id, c);
        markCardDirty(c);
        markProjectDirty();
        return c;
    }

    /** Name of the folder that holds cards made from the shared Common Enemies library. */
    public static final String COMMON_FOLDER = "Common Enemies";

    /**
     * Brings a common enemy into this project so it can fight: the first time it makes a card in a "Common Enemies"
     * folder, and every time after that it refreshes that same card from the library, so changes made to the
     * library creature apply and nothing is duplicated. (To change a creature for just one project, duplicate its card.)
     */
    public Card useCommonEnemy(Card lib) {
        for (String id : allCardIds()) {
            Card c = card(id);
            if (c != null && lib.id.equals(c.libraryId)) {
                String oldName = c.name;
                c.copyFrom(lib);
                c.libraryId = lib.id;
                TreeNode n = project.root.find(c.id);
                if (n != null && !c.name.equals(oldName)) renameNode(n, c.name);
                markCardDirty(c);
                return c;
            }
        }
        TreeNode folder = null;
        for (TreeNode ch : project.root.children) if (ch.folder && COMMON_FOLDER.equals(ch.name)) folder = ch;
        if (folder == null) folder = createFolder(project.root, COMMON_FOLDER);
        Card c = lib.copy();
        c.libraryId = lib.id;
        return createCard(folder, c);
    }

    /** Copies a card next to the original (name + " (copy)"). */
    public Card duplicateCard(TreeNode node) {
        Card original = card(node.id);
        if (original == null) return null;
        Card copy = original.copy();
        copy.name = original.name + " (copy)";
        TreeNode parent = project.root.parentOf(node.id);
        return createCard(parent == null ? project.root : parent, copy);
    }

    public List<String> allCardIds() {
        List<TreeNode> nodes = new ArrayList<>();
        project.root.collectCards(nodes);
        List<String> ids = new ArrayList<>();
        for (TreeNode n : nodes) ids.add(n.id);
        return ids;
    }

    public void markCardDirty(Card c) {
        namesVersion++;
        dirtyCards.add(c.id);
        onDirty.run();
    }

    // ---------------------------------------------------------------- encounters

    public boolean isEncounter(String id) {
        TreeNode n = id == null ? null : project.root.find(id);
        return n != null && n.isEncounter();
    }

    private Path encounterPath(String id) {
        return dir.resolve("encounters").resolve(id + ".encounter");
    }

    /** Loads (and caches) an encounter; null if it doesn't exist or can't be read. */
    public Encounter encounter(String id) {
        Encounter e = encounterCache.get(id);
        if (e != null) return e;
        Path p = encounterPath(id);
        if (!Files.isRegularFile(p)) return null;
        try {
            e = Json.readFile(p, Encounter.class);
        } catch (IOException ex) {
            loadFailed(id, p, ex);
            return null;
        }
        encounterCache.put(id, e);
        return e;
    }

    public Encounter createEncounter(TreeNode parent, String name) {
        Encounter e = new Encounter();
        e.name = name;
        return addEncounter(parent, e);
    }

    private Encounter addEncounter(TreeNode parent, Encounter e) {
        e.id = java.util.UUID.randomUUID().toString();
        TreeNode n = new TreeNode(e.name, false);
        n.id = e.id;
        n.kind = TreeNode.KIND_ENCOUNTER;
        parent.children.add(n);
        encounterCache.put(e.id, e);
        markEncounterDirty(e);
        markProjectDirty();
        return e;
    }

    /** Copies an encounter next to the original (name + " (copy)"). */
    public Encounter duplicateEncounter(TreeNode node) {
        Encounter original = encounter(node.id);
        if (original == null) return null;
        Encounter copy = original.copy();
        copy.name = original.name + " (copy)";
        TreeNode parent = project.root.parentOf(node.id);
        return addEncounter(parent == null ? project.root : parent, copy);
    }

    public List<String> allEncounterIds() {
        List<TreeNode> nodes = new ArrayList<>();
        project.root.collectEncounters(nodes);
        List<String> ids = new ArrayList<>();
        for (TreeNode n : nodes) ids.add(n.id);
        return ids;
    }

    public void markEncounterDirty(Encounter e) {
        dirtyEncounters.add(e.id);
        onDirty.run();
    }

    // ---------------------------------------------------------------- spell cards

    public boolean isSpell(String id) {
        TreeNode n = id == null ? null : project.root.find(id);
        return n != null && n.isSpell();
    }

    private Path spellPath(String id) {
        return dir.resolve("spells").resolve(id + ".spell");
    }

    /** Loads (and caches) a spell card; null if it doesn't exist or can't be read. */
    public Spell spell(String id) {
        Spell s = spellCache.get(id);
        if (s != null) return s;
        Path p = spellPath(id);
        if (!Files.isRegularFile(p)) return null;
        try {
            s = Json.readFile(p, Spell.class);
        } catch (IOException ex) {
            loadFailed(id, p, ex);
            return null;
        }
        s.id = id;
        spellCache.put(id, s);
        return s;
    }

    /** Adds a spell card to the project under {@code parent}; it gets a fresh id that becomes the tree node's id. */
    public Spell createSpell(TreeNode parent, Spell spell) {
        spell.id = java.util.UUID.randomUUID().toString();
        TreeNode n = new TreeNode(spell.name, false);
        n.id = spell.id;
        n.kind = TreeNode.KIND_SPELL;
        parent.children.add(n);
        spellCache.put(spell.id, spell);
        markSpellDirty(spell);
        markProjectDirty();
        return spell;
    }

    /** Copies a spell card next to the original (name + " (copy)"); the copy is not tied to the SRD library. */
    public Spell duplicateSpell(TreeNode node) {
        Spell original = spell(node.id);
        if (original == null) return null;
        Spell copy = original.copy();
        copy.name = original.name + " (copy)";
        copy.libraryId = null;
        TreeNode parent = project.root.parentOf(node.id);
        return createSpell(parent == null ? project.root : parent, copy);
    }

    public List<String> allSpellIds() {
        List<TreeNode> nodes = new ArrayList<>();
        project.root.collectSpells(nodes);
        List<String> ids = new ArrayList<>();
        for (TreeNode n : nodes) ids.add(n.id);
        return ids;
    }

    /** The spell card already made from this SRD library spell, or null. */
    public Spell spellFromLibrary(String libraryId) {
        if (libraryId == null) return null;
        for (String id : allSpellIds()) {
            Spell s = spell(id);
            if (s != null && libraryId.equals(s.libraryId)) return s;
        }
        return null;
    }

    public void markSpellDirty(Spell s) {
        dirtySpells.add(s.id);
        namesVersion++;
        onDirty.run();
    }

    /** Changes whenever a card or spell is added, renamed, edited or removed (name hints rebuild when it moves). */
    public int namesVersion() {
        return namesVersion;
    }

    // ---------------------------------------------------------------- magic items, quests, sessions

    /** Cache, files and tree plumbing for one kind of {@link ProjectEntry}. */
    private final class Kind<T extends ProjectEntry> {
        final String folder, ext, nodeKind;
        final Class<T> type;
        final Map<String, T> cache = new HashMap<>();
        final Set<String> dirty = new HashSet<>();

        Kind(String folder, String ext, String nodeKind, Class<T> type) {
            this.folder = folder;
            this.ext = ext;
            this.nodeKind = nodeKind;
            this.type = type;
        }

        Path path(String id) {
            return dir.resolve(folder).resolve(id + "." + ext);
        }

        boolean is(String id) {
            TreeNode n = id == null ? null : project.root.find(id);
            return n != null && !n.folder && nodeKind.equals(n.kind);
        }

        /** Loads (and caches) one; null if it doesn't exist or can't be read. */
        T get(String id) {
            T t = cache.get(id);
            if (t != null || id == null) return t;
            Path p = path(id);
            if (!Files.isRegularFile(p)) return null;
            try {
                t = Json.readFile(p, type);
            } catch (IOException ex) {
                loadFailed(id, p, ex);
                return null;
            }
            t.id = id;
            cache.put(id, t);
            return t;
        }

        /** Adds it to the project under {@code parent}; it gets a fresh id that becomes the tree node's id. */
        T create(TreeNode parent, T entry) {
            entry.id = UUID.randomUUID().toString();
            TreeNode n = new TreeNode(entry.name, false);
            n.id = entry.id;
            n.kind = nodeKind;
            parent.children.add(n);
            cache.put(entry.id, entry);
            markDirty(entry);
            markProjectDirty();
            return entry;
        }

        void markDirty(T entry) {
            dirty.add(entry.id);
            namesVersion++;
            onDirty.run();
        }

        List<String> ids() {
            List<TreeNode> nodes = new ArrayList<>();
            project.root.collectKind(nodeKind, nodes);
            List<String> ids = new ArrayList<>();
            for (TreeNode n : nodes) ids.add(n.id);
            return ids;
        }

        void rename(String id, String name) {
            T t = get(id);
            if (t != null) {
                t.name = name;
                markDirty(t);
            }
        }

        void forget(String id) {
            cache.remove(id);
            dirty.remove(id);
            try {
                Files.deleteIfExists(path(id));
            } catch (IOException e) {
                dnd.util.Log.error("Problem in ProjectStore", e);
            }
        }

        void flush() throws IOException {
            for (String id : new ArrayList<>(dirty)) {
                T t = cache.get(id);
                if (t != null) Json.writeFile(path(id), t);
            }
            dirty.clear();
        }
    }

    private <T extends ProjectEntry> Kind<T> register(Kind<T> k) {
        kinds.add(k);
        return k;
    }

    private Kind<?> kindFor(String nodeKind) {
        for (Kind<?> k : kinds) if (k.nodeKind.equals(nodeKind)) return k;
        return null;
    }

    public boolean isMagicItem(String id) {
        return magicItems.is(id);
    }

    /** Loads (and caches) a magic item card; null if it doesn't exist or can't be read. */
    public MagicItem magicItem(String id) {
        return magicItems.get(id);
    }

    public MagicItem createMagicItem(TreeNode parent, MagicItem item) {
        return magicItems.create(parent, item);
    }

    /** Copies a magic item card next to the original (name + " (copy)"). */
    public MagicItem duplicateMagicItem(TreeNode node) {
        MagicItem original = magicItem(node.id);
        if (original == null) return null;
        MagicItem copy = original.copy();
        copy.name = original.name + " (copy)";
        TreeNode parent = project.root.parentOf(node.id);
        return createMagicItem(parent == null ? project.root : parent, copy);
    }

    public List<String> allMagicItemIds() {
        return magicItems.ids();
    }

    public void markMagicItemDirty(MagicItem item) {
        magicItems.markDirty(item);
    }

    public boolean isQuest(String id) {
        return quests.is(id);
    }

    /** Loads (and caches) a quest; null if it doesn't exist or can't be read. */
    public Quest quest(String id) {
        return quests.get(id);
    }

    public Quest createQuest(TreeNode parent, Quest quest) {
        return quests.create(parent, quest);
    }

    public List<String> allQuestIds() {
        return quests.ids();
    }

    public void markQuestDirty(Quest quest) {
        quests.markDirty(quest);
    }

    public boolean isSession(String id) {
        return sessions.is(id);
    }

    /** Loads (and caches) a session log; null if it doesn't exist or can't be read. */
    public Session session(String id) {
        return sessions.get(id);
    }

    public Session createSession(TreeNode parent, Session session) {
        return sessions.create(parent, session);
    }

    public List<String> allSessionIds() {
        return sessions.ids();
    }

    /** Every session, in the order they were played. */
    public List<Session> allSessions() {
        List<Session> out = new ArrayList<>();
        for (String id : sessions.ids()) {
            Session s = sessions.get(id);
            if (s != null) out.add(s);
        }
        return Session.inOrder(out);
    }

    public void markSessionDirty(Session session) {
        sessions.markDirty(session);
    }

    public boolean isWorldMap(String id) {
        return worldMaps.is(id);
    }

    /** Loads (and caches) a world map; null if it doesn't exist or can't be read. */
    public WorldMap worldMap(String id) {
        return worldMaps.get(id);
    }

    public WorldMap createWorldMap(TreeNode parent, WorldMap map) {
        return worldMaps.create(parent, map);
    }

    /** Copies a world map next to the original (name + " (copy)"). */
    public WorldMap duplicateWorldMap(TreeNode node) {
        WorldMap original = worldMap(node.id);
        if (original == null) return null;
        WorldMap copy = original.copy();
        copy.name = original.name + " (copy)";
        TreeNode parent = project.root.parentOf(node.id);
        return createWorldMap(parent == null ? project.root : parent, copy);
    }

    public List<String> allWorldMapIds() {
        return worldMaps.ids();
    }

    /**
     * Saves a world map soon. Pass {@code content = true} when the painted map or what is on it changed (cached
     * pictures are then redrawn); false for a camera move, which only needs saving.
     */
    public void markWorldMapDirty(WorldMap map, boolean content) {
        if (content) map.revision++;
        worldMaps.markDirty(map);
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
            loadFailed(id, p, e);
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
            else if (isCard(id)) card(id);
            else if (isEncounter(id)) encounter(id);
            else if (isSpell(id)) spell(id);
            else if (isMagicItem(id)) magicItem(id);
            else if (isQuest(id)) quest(id);
            else if (isSession(id)) session(id);
            else if (isWorldMap(id)) worldMap(id);
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
        } else if (node.isCard()) {
            Card c = card(node.id);
            if (c != null) {
                c.name = name;
                markCardDirty(c);
            }
        } else if (node.isSpell()) {
            Spell s = spell(node.id);
            if (s != null) {
                s.name = name;
                markSpellDirty(s);
            }
        } else if (node.isEncounter()) {
            Encounter e = encounter(node.id);
            if (e != null) {
                e.name = name;
                markEncounterDirty(e);
            }
        } else if (!node.folder && node.kind != null && kindFor(node.kind) != null) {
            kindFor(node.kind).rename(node.id, name);
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
            cardCache.remove(b.id);
            dirtyCards.remove(b.id);
            encounterCache.remove(b.id);
            dirtyEncounters.remove(b.id);
            spellCache.remove(b.id);
            dirtySpells.remove(b.id);
            try {
                Files.deleteIfExists(boardPath(b.id));
                Files.deleteIfExists(mapPath(b.id));
                Files.deleteIfExists(cardPath(b.id));
                Files.deleteIfExists(encounterPath(b.id));
                Files.deleteIfExists(spellPath(b.id));
            } catch (IOException e) {
                dnd.util.Log.error("Problem in ProjectStore", e);
            }
            for (Kind<?> k : kinds) if (k.nodeKind.equals(b.kind)) k.forget(b.id);
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

    private void loadFailed(String id, Path file, IOException ex) {
        dnd.util.Log.error("Problem in ProjectStore", ex);
        loadErrors.put(id, file + " (" + ex.getMessage() + ")");
    }

    /** Why the item's file could not be read, or null if it loaded (or was never tried). */
    public String loadError(String id) {
        return loadErrors.get(id);
    }

    /** True if anything has been written to disk since this project was opened. */
    public boolean savedThisSession() {
        return savedThisSession;
    }

    private boolean hasUnsavedChanges() {
        if (projectDirty || !dirtyBoards.isEmpty() || !dirtyMaps.isEmpty() || !dirtyCards.isEmpty()
                || !dirtyEncounters.isEmpty() || !dirtySpells.isEmpty()) return true;
        for (Kind<?> k : kinds) if (!k.dirty.isEmpty()) return true;
        return false;
    }

    /** Writes everything that changed since the last flush. */
    public void flush() {
        if (hasUnsavedChanges()) savedThisSession = true;
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
            for (String id : new ArrayList<>(dirtyCards)) {
                Card c = cardCache.get(id);
                if (c != null) Json.writeFile(cardPath(id), c);
            }
            dirtyCards.clear();
            for (String id : new ArrayList<>(dirtyEncounters)) {
                Encounter e = encounterCache.get(id);
                if (e != null) Json.writeFile(encounterPath(id), e);
            }
            dirtyEncounters.clear();
            for (String id : new ArrayList<>(dirtySpells)) {
                Spell s = spellCache.get(id);
                if (s != null) Json.writeFile(spellPath(id), s);
            }
            dirtySpells.clear();
            for (Kind<?> k : kinds) k.flush();
            if (projectDirty) {
                Json.writeFile(dir.resolve(PROJECT_FILE), project);
                projectDirty = false;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
