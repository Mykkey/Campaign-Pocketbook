package dnd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dnd.library.SrdImporter;
import dnd.library.SrdLibrary;
import dnd.model.Attack;
import dnd.model.Card;
import dnd.model.Spell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SrdImportTest {
    private static final Path RES = Paths.get("src", "test", "resources", "srd");

    private static JsonNode read(String name) throws IOException {
        return new ObjectMapper().readTree(RES.resolve(name).toFile());
    }

    @Test
    void detectsWhichKindOfFileItIs() throws Exception {
        assertEquals(SrdImporter.Kind.MONSTERS, SrdImporter.detect(read("monsters.json")));
        assertEquals(SrdImporter.Kind.SPELLS, SrdImporter.detect(read("spells.json")));
        assertEquals(SrdImporter.Kind.UNKNOWN, SrdImporter.detect(new ObjectMapper().readTree("[{\"x\":1}]")));
        assertEquals(SrdImporter.Kind.UNKNOWN, SrdImporter.detect(new ObjectMapper().readTree("{}")));
        assertEquals(SrdImporter.Kind.UNKNOWN, SrdImporter.detect(new ObjectMapper().readTree("[]")));
    }

    @Test
    void importsAGoblinWithItsAttacks() throws Exception {
        List<Card> cards = SrdImporter.monsters(read("monsters.json"));
        assertEquals(2, cards.size());
        Card g = cards.get(0);
        assertEquals("srd-test-goblin", g.id);
        assertEquals("Test Goblin", g.name);
        assertEquals(Card.Kind.MONSTER, g.kind);
        assertEquals("Small", g.size);
        assertEquals("humanoid (goblinoid)", g.creatureType);
        assertEquals(15, g.ac);
        assertEquals("armor", g.acNote);
        assertEquals(7, g.hpMax);
        assertEquals("2d6", g.hitDice);
        assertEquals("30 ft.", g.speed);
        assertEquals(8, g.str);
        assertEquals(14, g.dex);
        assertEquals(10, g.intel);
        assertEquals("Stealth +6", g.skills);
        assertEquals("DEX +4", g.saves);
        assertEquals("darkvision 60 ft., passive Perception 9", g.senses);
        assertEquals("Common, Goblin", g.languages);
        assertEquals("1/4", g.challenge);
        assertEquals(50, g.xp);
        assertEquals(2, g.proficiency);
        assertEquals("Nimble Escape", g.traits.get(0).name);

        assertEquals(2, g.attacks.size());
        Attack scimitar = g.attacks.get(0);
        assertEquals("Scimitar", scimitar.name);
        assertEquals(4, scimitar.toHit);
        assertEquals("1d6+2", scimitar.damage);
        assertEquals("slashing", scimitar.damageType);
        assertEquals("5 ft.", scimitar.reach);
        assertEquals(5, scimitar.averageDamage());
        Attack bow = g.attacks.get(1);
        assertEquals("80/320 ft.", bow.reach);
        assertTrue(bow.ranged());
        assertTrue(g.actions.isEmpty(), "both actions were attacks");
    }

    @Test
    void importsTheTrickyMonster() throws Exception {
        Card d = SrdImporter.monsters(read("monsters.json")).get(1);
        assertEquals("dragon", d.creatureType);
        assertEquals(18, d.ac, "armor_class given as a plain number");
        assertEquals("40 ft., fly 80 ft. (hover), swim 40 ft.", d.speed);
        assertEquals("cold", d.vulnerabilities);
        assertEquals("fire", d.immunities);
        assertEquals("Poisoned", d.conditionImmunities);
        assertEquals("blindsight 30 ft., darkvision 120 ft., passive Perception 17", d.senses);
        assertEquals("8", d.challenge);
        assertEquals(3, d.proficiency);

        Attack bite = d.attacks.get(0);
        assertEquals("Bite", bite.name);
        assertEquals("2d10+4+2d6", bite.damage);
        assertEquals("piercing plus fire", bite.damageType);
        assertEquals("10 ft.", bite.reach);
        assertEquals("The target is grappled.", bite.text);
        assertEquals(22, bite.averageDamage());

        assertEquals("Fire Aura (3/Day)", d.traits.get(0).name);
        assertEquals("Multiattack", d.actions.get(0).name);
        assertEquals("Fire Breath (Recharge 5-6)", d.actions.get(1).name);
        assertEquals("Tail Swat", d.reactions.get(0).name);
        assertEquals("Wing Attack", d.legendary.get(0).name);
    }

    @Test
    void challengeRatingsAreWrittenAsFractions() {
        assertEquals("1/8", SrdImporter.challenge(0.125));
        assertEquals("1/4", SrdImporter.challenge(0.25));
        assertEquals("1/2", SrdImporter.challenge(0.5));
        assertEquals("0", SrdImporter.challenge(0));
        assertEquals("21", SrdImporter.challenge(21));
    }

    @Test
    void importsSpells() throws Exception {
        List<Spell> spells = SrdImporter.spells(read("spells.json"));
        assertEquals(3, spells.size());
        Spell fb = spells.get(0);
        assertEquals("Test Fireball", fb.name);
        assertEquals(3, fb.level);
        assertEquals("3rd-level", fb.levelLabel());
        assertEquals("Evocation", fb.school);
        assertEquals("1 action", fb.castingTime);
        assertEquals("V, S, M", fb.components);
        assertEquals(List.of("Sorcerer", "Wizard"), fb.classes);
        assertTrue(fb.description.contains("\n\n"), "paragraphs are kept");
        String text = fb.toText();
        assertTrue(text.contains("3rd-level evocation"), text);
        assertTrue(text.contains("Components: V, S, M (A tiny ball of bat guano and sulfur)"), text);
        assertTrue(text.contains("At higher levels."), text);

        assertEquals("Cantrip", spells.get(1).levelLabel());
        assertTrue(spells.get(1).toText().contains("Evocation cantrip"));
        Spell detect = spells.get(2);
        assertTrue(detect.ritual);
        assertTrue(detect.toText().contains("(ritual)"));
        assertTrue(detect.toText().contains("Duration: Concentration, Up to 10 minutes"), detect.toText());
    }

    @Test
    void libraryStoresAnImportAndReloadsIt(@TempDir Path tmp) throws Exception {
        SrdLibrary lib = new SrdLibrary(tmp.resolve("srd"));
        assertTrue(lib.isEmpty());
        var r1 = lib.importFile(RES.resolve("monsters.json"));
        assertEquals(SrdImporter.Kind.MONSTERS, r1.kind());
        assertEquals(2, r1.count());
        var r2 = lib.importFile(RES.resolve("spells.json"));
        assertEquals(3, r2.count());
        assertFalse(lib.isEmpty());

        SrdLibrary again = new SrdLibrary(tmp.resolve("srd"));
        assertEquals(2, again.monsters().size());
        assertEquals("Test Drake", again.monsters().get(0).name, "listed A to Z");
        assertEquals(22, again.monsters().get(0).attacks.get(0).averageDamage());
        assertEquals(3, again.spells().size());
    }

    @Test
    void importingTheWrongFileIsRejected(@TempDir Path tmp) throws Exception {
        Path bad = tmp.resolve("bad.json");
        Files.writeString(bad, "[{\"hello\": \"world\"}]");
        SrdLibrary lib = new SrdLibrary(tmp.resolve("srd"));
        assertThrows(IOException.class, () -> lib.importFile(bad));
        assertTrue(lib.isEmpty());
    }
}