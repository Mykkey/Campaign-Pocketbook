package dnd.library;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dnd.model.Card;
import dnd.model.Spell;
import dnd.storage.Json;
import dnd.storage.ProjectStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Monsters and spells imported from the SRD JSON files, kept in one folder shared by all projects
 * ({@code ~/DnDCampaignNotes/SRD Library}). Importing a file replaces what was there for that kind.
 */
public final class SrdLibrary {
    private static SrdLibrary shared;

    private final Path dir;
    private List<Card> monsters;
    private List<Spell> spells;

    public SrdLibrary(Path dir) {
        this.dir = dir;
    }

    public static synchronized SrdLibrary shared() {
        if (shared == null) shared = new SrdLibrary(ProjectStore.baseDir().resolve("SRD Library"));
        return shared;
    }

    /** Wrapper classes so the lists can be written with the app's normal JSON helper. */
    public static class MonsterFile {
        public List<Card> monsters = new ArrayList<>();
    }

    public static class SpellFile {
        public List<Spell> spells = new ArrayList<>();
    }

    public record ImportResult(SrdImporter.Kind kind, int count) { }

    public synchronized List<Card> monsters() {
        if (monsters == null) {
            monsters = new ArrayList<>();
            Path p = dir.resolve("monsters.json");
            if (Files.isRegularFile(p)) {
                try {
                    monsters.addAll(Json.readFile(p, MonsterFile.class).monsters);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            monsters.sort(Comparator.comparing((Card c) -> c.name.toLowerCase()));
        }
        return monsters;
    }

    public synchronized List<Spell> spells() {
        if (spells == null) {
            spells = new ArrayList<>();
            Path p = dir.resolve("spells.json");
            if (Files.isRegularFile(p)) {
                try {
                    spells.addAll(Json.readFile(p, SpellFile.class).spells);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            spells.sort(Comparator.comparing((Spell s) -> s.name.toLowerCase()));
        }
        return spells;
    }

    public boolean isEmpty() {
        return monsters().isEmpty() && spells().isEmpty();
    }

    /**
     * Reads an SRD monsters or spells file (the kind is detected from its contents) and stores it, replacing
     * what was imported before for that kind.
     *
     * @throws IOException if the file can't be read or is not an SRD monsters/spells list
     */
    public synchronized ImportResult importFile(Path file) throws IOException {
        JsonNode root = new ObjectMapper().readTree(file.toFile());
        SrdImporter.Kind kind = SrdImporter.detect(root);
        Files.createDirectories(dir);
        switch (kind) {
            case MONSTERS -> {
                MonsterFile f = new MonsterFile();
                f.monsters = SrdImporter.monsters(root);
                Json.writeFile(dir.resolve("monsters.json"), f);
                monsters = null;
                return new ImportResult(kind, f.monsters.size());
            }
            case SPELLS -> {
                SpellFile f = new SpellFile();
                f.spells = SrdImporter.spells(root);
                Json.writeFile(dir.resolve("spells.json"), f);
                spells = null;
                return new ImportResult(kind, f.spells.size());
            }
            default -> throw new IOException(file.getFileName() + " is not an SRD monsters or spells file. "
                    + "Expected 5e-SRD-Monsters.json or 5e-SRD-Spells.json.");
        }
    }
}
