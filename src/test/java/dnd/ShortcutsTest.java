package dnd;

import dnd.keys.Shortcuts;
import dnd.keys.Shortcuts.Action;
import dnd.keys.Shortcuts.Context;
import dnd.ui.AppSettings;
import dnd.ui.MapTool;
import dnd.ui.Tool;
import dnd.ui.WorldTool;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShortcutsTest {
    @BeforeEach
    void freshSettings(@TempDir Path tmp) {
        AppSettings.useFile(tmp.resolve("settings.properties"));
    }

    @AfterEach
    void back() {
        AppSettings.useFile(null);
    }

    private static KeyEvent key(KeyCode code, boolean ctrl, boolean shift, boolean alt) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, ctrl, alt, false);
    }

    @Test
    void everyDefaultKeyCanBeReadAndEveryActionHasALabel() {
        for (Action a : Shortcuts.all()) {
            assertFalse(a.label().isBlank(), a.id());
            assertFalse(a.category().isBlank(), a.id());
            assertFalse(a.defaults().isEmpty(), a.id());
            assertFalse(Shortcuts.bindings(a.id()).isEmpty(), "no default key could be read for " + a.id() + " " + a.defaults());
            assertFalse(Shortcuts.label(a.id()).isBlank(), a.id());
        }
        assertEquals(Shortcuts.all().size(), Shortcuts.all().stream().map(Action::id).distinct().count(), "ids are unique");
        assertTrue(Shortcuts.categories().size() >= 8);
    }

    @Test
    void noTwoDefaultsShareAKeyWhereBothCanBeUsedAtTheSameTime() {
        List<Action> all = Shortcuts.all();
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                Action a = all.get(i), b = all.get(j);
                if (!a.context().overlaps(b.context())) continue;
                for (KeyCombination ka : Shortcuts.bindings(a.id())) {
                    for (KeyCombination kb : Shortcuts.bindings(b.id())) {
                        assertNotEquals(ka, kb, a.id() + " and " + b.id() + " both use " + ka.getDisplayText());
                    }
                }
            }
        }
    }

    @Test
    void theToolKeysFollowTheOrderOfEachEditorsTools() {
        assertEquals(Tool.values().length, Shortcuts.BOARD_TOOLS.size());
        for (Tool t : Tool.values()) assertEquals(t.label, Shortcuts.BOARD_TOOLS.get(t.ordinal()), "board tool " + t);
        assertEquals(MapTool.values().length, Shortcuts.BATTLE_TOOLS.size());
        for (MapTool t : MapTool.values()) assertEquals(t.label, Shortcuts.BATTLE_TOOLS.get(t.ordinal()), "battle tool " + t);
        assertEquals(WorldTool.values().length, Shortcuts.WORLD_TOOLS.size());
        for (WorldTool t : WorldTool.values()) assertEquals(t.label, Shortcuts.WORLD_TOOLS.get(t.ordinal()), "world tool " + t);
        assertEquals("1", Shortcuts.label("tool.world.1"));
        assertEquals("0", Shortcuts.label("tool.world.10"));
        assertEquals("B", Shortcuts.label("tool.world.11"), "Bucket");
        assertEquals("R", Shortcuts.label("tool.world.12"), "Reveal");
        assertEquals("E", Shortcuts.label("tool.world.13"), "Elevation");
        assertEquals("0", Shortcuts.label("tool.battle.10"));
        assertEquals("1", Shortcuts.label("tool.board.1"));
    }

    @Test
    void theApprovedDefaultsAreWhatTheyShouldBe() {
        assertEquals("Ctrl+K", Shortcuts.label("item.link"));
        assertEquals("Ctrl+L", Shortcuts.label("global.loot"));
        assertEquals("Ctrl+G", Shortcuts.label("global.names"));
        assertEquals("Ctrl+R", Shortcuts.label("global.rules"));
        assertEquals("Ctrl+Shift+N", Shortcuts.label("global.npc"));
        assertEquals("Ctrl+,", Shortcuts.label("global.settings"));
        assertEquals("Ctrl+Z", Shortcuts.label("item.undo"));
        assertEquals("Ctrl+Y / Ctrl+Shift+Z", Shortcuts.label("item.redo"));
        assertEquals("Ctrl+D", Shortcuts.label("item.duplicate"));
        assertEquals("Ctrl+0", Shortcuts.label("item.fit"));
        assertEquals("Ctrl+1", Shortcuts.label("item.actualSize"));
        assertEquals("[", Shortcuts.label("item.rotateLeft"));
        assertEquals("]", Shortcuts.label("item.rotateRight"));
        assertEquals("F", Shortcuts.label("item.flip"));
        assertEquals("H", Shortcuts.label("item.hide"));
        assertEquals("N", Shortcuts.label("battle.next"));
        assertEquals("F11", Shortcuts.label("player.fullscreen"));
        assertEquals("Escape", Shortcuts.label("dialog.close"));
        assertTrue(Shortcuts.label("global.back").startsWith("Alt+"));
    }

    @Test
    void keyEventsMatchTheirActionsAndOnlyThem() {
        assertTrue(Shortcuts.matches(key(KeyCode.K, true, false, false), "item.link"));
        assertFalse(Shortcuts.matches(key(KeyCode.K, false, false, false), "item.link"), "plain K is not Link");
        assertFalse(Shortcuts.matches(key(KeyCode.K, true, true, false), "item.link"), "Ctrl+Shift+K is not Link");
        assertFalse(Shortcuts.matches(key(KeyCode.L, true, false, false), "item.link"), "Ctrl+L is Loot now");
        assertTrue(Shortcuts.matches(key(KeyCode.L, true, false, false), "global.loot"));
        assertTrue(Shortcuts.matches(key(KeyCode.Z, true, true, false), "item.redo"));
        assertFalse(Shortcuts.matches(key(KeyCode.Z, true, true, false), "item.undo"));
        assertTrue(Shortcuts.matches(key(KeyCode.OPEN_BRACKET, false, false, false), "item.rotateLeft"));
        assertTrue(Shortcuts.matches(key(KeyCode.DIGIT3, false, false, false), "tool.world.3"));
        assertTrue(Shortcuts.matches(key(KeyCode.ESCAPE, false, false, false), "item.escape"));
        assertTrue(Shortcuts.matches(key(KeyCode.BACK_SPACE, false, false, false), "item.delete"));
        assertTrue(Shortcuts.matches(key(KeyCode.EQUALS, true, false, false), "item.zoomIn"));
        Action found = Shortcuts.find(key(KeyCode.G, true, false, false), a -> a.context() == Context.GLOBAL);
        assertEquals("global.names", found.id());
        assertNull(Shortcuts.find(key(KeyCode.G, true, false, false), a -> a.context() == Context.PLAYER));
    }

    @Test
    void aBindingIsRememberedChangedAndPutBackToTheDefault() {
        assertTrue(Shortcuts.isDefault("item.link"));
        Shortcuts.set("item.link", List.of(KeyCombination.valueOf("Shortcut+J")));
        assertFalse(Shortcuts.isDefault("item.link"));
        assertEquals("Ctrl+J", Shortcuts.label("item.link"));
        assertTrue(Shortcuts.matches(key(KeyCode.J, true, false, false), "item.link"));
        assertFalse(Shortcuts.matches(key(KeyCode.K, true, false, false), "item.link"), "the old key stops working");
    }

    @Test
    void settingTheDefaultsAgainOrResettingForgetsTheChoice() {
        Shortcuts.set("item.link", List.of(KeyCombination.valueOf("Shortcut+J")));
        Shortcuts.set("item.link", List.of(KeyCombination.valueOf("Shortcut+K")));
        assertTrue(Shortcuts.isDefault("item.link"), "choosing the default key is the same as not choosing");
        Shortcuts.set("item.copy", List.of());
        assertTrue(Shortcuts.bindings("item.copy").isEmpty(), "an action can be left without a key");
        assertEquals("", Shortcuts.label("item.copy"));
        assertEquals("Copy", Shortcuts.tip("Copy", "item.copy"), "no key, no brackets in the tooltip");
        assertEquals("Paste (Ctrl+V)", Shortcuts.tip("Paste", "item.paste"));
        assertTrue(Shortcuts.unassigned().stream().anyMatch(a -> a.id().equals("item.copy")));
        Shortcuts.reset("item.copy");
        assertEquals("Ctrl+C", Shortcuts.label("item.copy"));
        Shortcuts.set("item.cut", List.of(KeyCombination.valueOf("Shortcut+Q")));
        Shortcuts.set("item.paste", List.of(KeyCombination.valueOf("Shortcut+W"), KeyCombination.valueOf("Shortcut+E")));
        assertEquals(2, Shortcuts.bindings("item.paste").size());
        Shortcuts.resetAll();
        assertEquals("Ctrl+X", Shortcuts.label("item.cut"));
        assertEquals("Ctrl+V", Shortcuts.label("item.paste"));
    }

    @Test
    void aDamagedSavedBindingIsIgnoredAndUnknownActionsHaveNoKeys() {
        AppSettings.setString("key.item.copy", "NotAKey|Shortcut+Q");
        assertEquals(1, Shortcuts.bindings("item.copy").size());
        assertEquals("Ctrl+Q", Shortcuts.label("item.copy"));
        assertTrue(Shortcuts.bindings("no.such.action").isEmpty());
        assertNull(Shortcuts.get("no.such.action"));
        assertFalse(Shortcuts.matches(key(KeyCode.A, true, false, false), "no.such.action"));
    }

    @Test
    void conflictsAreFoundOnlyWhereTheActionsCanBeUsedTogether() {
        KeyCombination ctrlD = KeyCombination.valueOf("Shortcut+D");
        List<Action> c = Shortcuts.conflicts("item.link", ctrlD);
        assertEquals(List.of("item.duplicate"), c.stream().map(Action::id).toList());
        assertTrue(Shortcuts.conflicts("item.duplicate", ctrlD).isEmpty(), "an action does not conflict with itself");
        // a world map tool and a whiteboard tool may both use the key B: they are never open at the same moment
        KeyCombination b = KeyCombination.valueOf("B");
        assertTrue(Shortcuts.conflicts("tool.board.3", b).stream().noneMatch(a -> a.id().startsWith("tool.world.")));
        // but two world map tools may not
        KeyCombination one = KeyCombination.valueOf("1");
        assertEquals(List.of("tool.world.1"), Shortcuts.conflicts("tool.world.2", one).stream().map(Action::id).toList());
        // a global key clashes with an editor key
        assertEquals(List.of("global.loot"), Shortcuts.conflicts("item.link", KeyCombination.valueOf("Shortcut+L")).stream().map(Action::id).toList());
        // player windows are separate
        assertTrue(Shortcuts.conflicts("player.grid", KeyCombination.valueOf("F")).isEmpty() || Shortcuts.conflicts("player.grid", KeyCombination.valueOf("F")).stream().allMatch(a -> a.context() == Context.PLAYER));
        assertTrue(Shortcuts.conflicts("item.flip", KeyCombination.valueOf("G")).isEmpty(), "G is free in the editors even though player windows use it");
    }

    @Test
    void takingAKeySwapsItAndLeavesTheLoserWithoutOne() {
        KeyCombination ctrlD = KeyCombination.valueOf("Shortcut+D");
        List<Action> lost = Shortcuts.takeKey("item.link", ctrlD);
        assertEquals(List.of("item.duplicate"), lost.stream().map(Action::id).toList());
        assertTrue(Shortcuts.bindings("item.link").contains(ctrlD));
        assertTrue(Shortcuts.bindings("item.link").contains(KeyCombination.valueOf("Shortcut+K")), "it keeps its other key");
        assertTrue(Shortcuts.bindings("item.duplicate").isEmpty());
        assertTrue(Shortcuts.unassigned().stream().anyMatch(a -> a.id().equals("item.duplicate")));
        assertTrue(Shortcuts.conflicts("item.link", ctrlD).isEmpty());
    }

    @Test
    void theNamesOfAwkwardKeysCanBeSavedAndReadBack() {
        for (String name : new String[]{"Open Bracket", "Close Bracket", "Shortcut+Comma", "Shortcut+Equals", "F11", "Esc", "Backspace", "Delete"}) {
            KeyCombination k = Shortcuts.parse(name);
            assertNotNull(k, name);
            assertEquals(k, Shortcuts.parse(k.getName()), name);
        }
        assertNull(Shortcuts.parse(""));
        assertNull(Shortcuts.parse("Hyper+Q"));
    }

    @Test
    void pressedKeysBecomeCombinationsAndModifierKeysAloneAreIgnored() {
        KeyCombination k = Shortcuts.fromEvent(key(KeyCode.K, true, false, false));
        assertEquals(KeyCombination.valueOf("Shortcut+K"), k);
        assertEquals(KeyCombination.valueOf("Shortcut+Shift+N"), Shortcuts.fromEvent(key(KeyCode.N, true, true, false)));
        assertEquals(KeyCombination.valueOf("Alt+Left"), Shortcuts.fromEvent(key(KeyCode.LEFT, false, false, true)));
        assertEquals(KeyCombination.valueOf("F"), Shortcuts.fromEvent(key(KeyCode.F, false, false, false)));
        assertEquals(KeyCombination.valueOf("1"), Shortcuts.fromEvent(key(KeyCode.DIGIT1, false, false, false)));
        assertNull(Shortcuts.fromEvent(key(KeyCode.CONTROL, true, false, false)), "Ctrl on its own is not a key");
        assertNull(Shortcuts.fromEvent(key(KeyCode.SHIFT, false, true, false)));
        assertNull(Shortcuts.fromEvent(key(KeyCode.ALT, false, false, true)));
        // what was pressed matches itself once saved and read back
        KeyCombination pressed = Shortcuts.fromEvent(key(KeyCode.OPEN_BRACKET, true, false, false));
        assertEquals(pressed, Shortcuts.parse(pressed.getName()));
        assertTrue(pressed.match(key(KeyCode.OPEN_BRACKET, true, false, false)));
    }
}
