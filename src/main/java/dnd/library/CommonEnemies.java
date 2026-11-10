package dnd.library;

import dnd.model.Card;
import dnd.rules.CardTemplates;
import dnd.storage.Json;
import dnd.storage.ProjectStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The DM's own shelf of creatures that can be dropped into any encounter or battle without first creating a card in
 * the project. It lives in one folder shared by all projects ({@code ~/CampaignPocketbook/Common Enemies}), one
 * {@code .card} file per creature. The first time it is used it is filled with the bundled starter creatures
 * (goblin, orc, wolf...); after that the DM can edit their stats, delete them, and add homebrew creatures.
 */
public final class CommonEnemies {
    private static final String SEEDED = ".seeded";
    private static CommonEnemies shared;

    private final Path dir;
    private Map<String, Card> cache;

    public CommonEnemies(Path dir) {
        this.dir = dir;
    }

    /** The library every project uses. */
    public static synchronized CommonEnemies shared() {
        if (shared == null) shared = new CommonEnemies(ProjectStore.baseDir().resolve("Common Enemies"));
        return shared;
    }

    /** Ids of the bundled starter creatures (also the file names of their library entries). */
    public static List<String> bundledIds() {
        List<String> ids = new ArrayList<>();
        for (CardTemplates.Template t : CardTemplates.list()) {
            if (!CardTemplates.BLANK_PLAYER.equals(t.id) && !CardTemplates.BLANK_MONSTER.equals(t.id)) ids.add(t.id);
        }
        return ids;
    }

    public boolean isBundled(String id) {
        return bundledIds().contains(id);
    }

    // ---------------------------------------------------------------- reading

    private Path path(String id) {
        return dir.resolve(id + ".card");
    }

    private synchronized Map<String, Card> load() {
        if (cache != null) return cache;
        Map<String, Card> map = new LinkedHashMap<>();
        try {
            Files.createDirectories(dir);
            if (!Files.exists(dir.resolve(SEEDED))) {
                for (String id : bundledIds()) writeBundled(id);
                Files.writeString(dir.resolve(SEEDED), "starter creatures were added once; deleting them keeps them deleted");
            }
            List<Path> files;
            try (Stream<Path> s = Files.list(dir)) {
                files = s.filter(x -> x.getFileName().toString().endsWith(".card")).toList();
            }
            {
                for (Path p : files) {
                    try {
                        Card c = Json.readFile(p, Card.class);
                        String fileId = p.getFileName().toString().replaceFirst("\\.card$", "");
                        c.id = fileId;
                        map.put(fileId, c);
                    } catch (IOException e) {
                        dnd.util.Log.error("Problem in CommonEnemies", e); // skip a damaged file rather than losing the whole library
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        cache = map;
        return cache;
    }

    private void writeBundled(String id) throws IOException {
        Card c = CardTemplates.create(id, null);
        c.id = id;
        Json.writeFile(path(id), c);
    }

    /** Every creature, A to Z. */
    public List<Card> list() {
        List<Card> out = new ArrayList<>(load().values());
        out.sort(Comparator.comparing((Card c) -> c.name.toLowerCase()).thenComparing(c -> c.id));
        return out;
    }

    /** The creature with this id, or null if it was deleted. */
    public Card get(String id) {
        return id == null ? null : load().get(id);
    }

    // ---------------------------------------------------------------- changing

    /** Saves a creature after its stats were edited. */
    public void save(Card c) {
        load().put(c.id, c);
        try {
            Json.writeFile(path(c.id), c);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Adds a new creature (copy of {@code card}, with a fresh id) and returns it. */
    public Card add(Card card) {
        Card c = card.copy();
        c.id = UUID.randomUUID().toString();
        c.libraryId = null;
        save(c);
        return c;
    }

    public void delete(String id) {
        load().remove(id);
        try {
            Files.deleteIfExists(path(id));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Puts every bundled creature back to its original stats, re-adding any that were deleted. */
    public void restoreDefaults() {
        try {
            for (String id : bundledIds()) writeBundled(id);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        cache = null;
    }

    /** Forgets what is cached, so the next read comes from disk (used by tests). */
    public synchronized void reload() {
        cache = null;
    }
}
