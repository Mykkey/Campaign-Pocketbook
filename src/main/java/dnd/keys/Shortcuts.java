package dnd.keys;

import dnd.ui.AppSettings;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every keyboard shortcut of the app in one place: what it does, where it works, its default keys, and the keys the user has
 * chosen instead (kept in the settings as {@code key.<id>}). Everything that reacts to a key asks this class, so changing a
 * binding in the settings changes it everywhere at once, and tooltips can show the real keys.
 */
public final class Shortcuts {
    private Shortcuts() { }

    /** Where an action works. Two actions can share a key only if their contexts never overlap. */
    public enum Context {
        /** In the main window, whatever is open. */
        GLOBAL("Everywhere"),
        /** On the open item, whichever kind it is. */
        ITEM("On the open item"),
        BOARD("Whiteboards"),
        BATTLEMAP("Battlemaps"),
        WORLDMAP("World maps"),
        /** During a battle, on the battlemap. */
        BATTLE("During a battle"),
        PLAYER("Player windows"),
        DIALOG("Pop-up windows");

        public final String label;

        Context(String label) {
            this.label = label;
        }

        /** True if actions of the two contexts can be used at the same moment (so they must not share a key). */
        public boolean overlaps(Context o) {
            if (this == o) return true;
            if (this == PLAYER || o == PLAYER || this == DIALOG || o == DIALOG) return false;
            if (this == GLOBAL || o == GLOBAL || this == ITEM || o == ITEM) return true;
            boolean battle = this == BATTLE || o == BATTLE;
            if (battle) return this == BATTLEMAP || o == BATTLEMAP;
            return false; // two different editors are never open at once
        }
    }

    /**
     * One thing a key can do.
     *
     * @param typing     also works while a text box has the focus (only for shortcuts with Ctrl or Alt)
     * @param canvasOnly only when the item itself, not a toolbar button, has the focus (so Enter still presses buttons)
     */
    public record Action(String id, String label, String category, Context context, List<String> defaults, boolean typing, boolean canvasOnly) { }

    // ---------------------------------------------------------------- the list

    private static final Map<String, Action> ALL = new LinkedHashMap<>();

    private static void add(String id, String label, String category, Context ctx, boolean typing, boolean canvasOnly, String... keys) {
        ALL.put(id, new Action(id, label, category, ctx, List.of(keys), typing, canvasOnly));
    }

    private static void add(String id, String label, String category, Context ctx, String... keys) {
        add(id, label, category, ctx, false, false, keys);
    }

    /** The tools of each editor in the order of their buttons: the first ten get the keys 1 to 9 and 0. */
    public static final List<String> BOARD_TOOLS = List.of("Select", "Hand", "Text", "Sticky", "Pen", "Eraser", "Rect", "Ellipse", "Line", "Arrow");
    public static final List<String> BATTLE_TOOLS = List.of("Select", "Hand", "Terrain", "Piece", "Token", "Ruler", "Fog", "Area", "Pin", "Light");
    public static final List<String> WORLD_TOOLS = List.of("Select", "Hand", "Land and sea", "Biomes", "Stamp", "Lines", "Region", "Scatter", "Label",
            "Ruler", "Bucket", "Reveal", "Elevation");
    private static final String[] DIGITS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
    private static final String[] WORLD_EXTRA = {"B", "R", "E"};

    static {
        String g = "Everywhere";
        add("global.back", "Go back", g, Context.GLOBAL, true, false, "Alt+Left");
        add("global.forward", "Go forward", g, Context.GLOBAL, true, false, "Alt+Right");
        add("global.closeTab", "Close this tab", g, Context.GLOBAL, true, false, "Shortcut+W");
        add("global.search", "Search the campaign", g, Context.GLOBAL, true, false, "Shortcut+F");
        add("global.names", "Name generator", g, Context.GLOBAL, true, false, "Shortcut+G");
        add("global.loot", "Loot generator", g, Context.GLOBAL, true, false, "Shortcut+L");
        add("global.rules", "Quick reference (rules)", g, Context.GLOBAL, true, false, "Shortcut+R");
        add("global.npc", "NPC generator", g, Context.GLOBAL, true, false, "Shortcut+Shift+N");
        add("global.settings", "Settings", g, Context.GLOBAL, true, false, "Shortcut+Comma");

        String it = "On the open item";
        add("item.undo", "Undo", it, Context.ITEM, "Shortcut+Z");
        add("item.redo", "Redo", it, Context.ITEM, "Shortcut+Y", "Shortcut+Shift+Z");
        add("item.copy", "Copy", it, Context.ITEM, "Shortcut+C");
        add("item.cut", "Cut", it, Context.ITEM, "Shortcut+X");
        add("item.paste", "Paste", it, Context.ITEM, "Shortcut+V");
        add("item.duplicate", "Duplicate", it, Context.ITEM, "Shortcut+D");
        add("item.selectAll", "Select all", it, Context.ITEM, "Shortcut+A");
        add("item.link", "Link and note", it, Context.ITEM, "Shortcut+K");
        add("item.delete", "Delete the selection", it, Context.ITEM, "Delete", "Backspace");
        add("item.escape", "Deselect / cancel", it, Context.ITEM, "Esc");
        add("item.edit", "Edit the selected text / finish drawing", it, Context.ITEM, false, true, "Enter", "F2");
        add("item.fit", "Fit everything in view", it, Context.ITEM, "Shortcut+0");
        add("item.actualSize", "Actual size (100%)", it, Context.ITEM, "Shortcut+1");
        add("item.zoomIn", "Zoom in", it, Context.ITEM, "Shortcut+Equals", "Shortcut+Plus", "Shortcut+Add");
        add("item.zoomOut", "Zoom out", it, Context.ITEM, "Shortcut+Minus", "Shortcut+Subtract");
        add("item.rotateLeft", "Rotate left 15 degrees", it, Context.ITEM, "Open Bracket");
        add("item.rotateRight", "Rotate right 15 degrees", it, Context.ITEM, "Close Bracket");
        add("item.flip", "Flip", it, Context.ITEM, "F");
        add("item.hide", "Hide from / show to the players", it, Context.ITEM, "H");

        add("battle.next", "Next turn", "During a battle", Context.BATTLE, "N");
        add("battle.previous", "Previous turn", "During a battle", Context.BATTLE, "P");

        for (int i = 0; i < BOARD_TOOLS.size(); i++) add("tool.board." + (i + 1), "Tool: " + BOARD_TOOLS.get(i), "Whiteboard tools", Context.BOARD, i < 10 ? DIGITS[i] : "");
        for (int i = 0; i < BATTLE_TOOLS.size(); i++) add("tool.battle." + (i + 1), "Tool: " + BATTLE_TOOLS.get(i), "Battlemap tools", Context.BATTLEMAP, i < 10 ? DIGITS[i] : "");
        for (int i = 0; i < WORLD_TOOLS.size(); i++) {
            String key = i < 10 ? DIGITS[i] : WORLD_EXTRA[i - 10];
            add("tool.world." + (i + 1), "Tool: " + WORLD_TOOLS.get(i), "World map tools", Context.WORLDMAP, key);
        }

        String pl = "Player windows";
        add("player.fullscreen", "Full screen", pl, Context.PLAYER, "F11");
        add("player.fit", "Fit the whole map", pl, Context.PLAYER, "F");
        add("player.grid", "Show or hide the grid", pl, Context.PLAYER, "G");
        add("player.turnOrder", "Show or hide the turn order", pl, Context.PLAYER, "I");
        add("player.zoomIn", "Zoom in", pl, Context.PLAYER, "Equals", "Plus", "Add");
        add("player.zoomOut", "Zoom out", pl, Context.PLAYER, "Minus", "Subtract");

        add("dialog.close", "Close the window", "Pop-up windows", Context.DIALOG, "Esc");
    }

    public static List<Action> all() {
        return new ArrayList<>(ALL.values());
    }

    public static Action get(String id) {
        return ALL.get(id);
    }

    /** The categories in the order of the list. */
    public static List<String> categories() {
        List<String> out = new ArrayList<>();
        for (Action a : ALL.values()) if (!out.contains(a.category())) out.add(a.category());
        return out;
    }

    // ---------------------------------------------------------------- bindings

    private static String settingKey(String id) {
        return "key." + id;
    }

    /** The keys the user has chosen for an action (or the defaults); empty if the action has been left without a key. */
    public static List<KeyCombination> bindings(String id) {
        Action a = ALL.get(id);
        if (a == null) return List.of();
        String saved = AppSettings.getString(settingKey(id), null);
        List<String> names = saved == null ? a.defaults() : saved.equals("none") ? List.of() : List.of(saved.split("\\|"));
        List<KeyCombination> out = new ArrayList<>();
        for (String n : names) {
            KeyCombination k = parse(n);
            if (k != null) out.add(k);
        }
        return out;
    }

    /** A key combination from its saved name, or null if the text is empty or damaged. */
    public static KeyCombination parse(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            KeyCombination k = KeyCombination.valueOf(name.trim());
            return k instanceof javafx.scene.input.KeyCodeCombination ? k : null; // only real keys; a word that is not a key name is damaged text
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isDefault(String id) {
        return !AppSettings.has(settingKey(id));
    }

    /** Gives an action these keys (none at all leaves it without a key). Choosing exactly the defaults goes back to the defaults. */
    public static void set(String id, List<KeyCombination> keys) {
        Action a = ALL.get(id);
        if (a == null) return;
        List<String> names = new ArrayList<>();
        for (KeyCombination k : keys) names.add(k.getName());
        if (names.equals(normalised(a.defaults()))) {
            AppSettings.remove(settingKey(id));
            return;
        }
        AppSettings.setString(settingKey(id), names.isEmpty() ? "none" : String.join("|", names));
    }

    private static List<String> normalised(List<String> defaults) {
        List<String> out = new ArrayList<>();
        for (String d : defaults) {
            KeyCombination k = parse(d);
            if (k != null) out.add(k.getName());
        }
        return out;
    }

    public static void reset(String id) {
        AppSettings.remove(settingKey(id));
    }

    public static void resetAll() {
        AppSettings.resetPrefix("key.");
    }

    // ---------------------------------------------------------------- using them

    /** True if the key event is one of the keys of the action. */
    public static boolean matches(KeyEvent e, String id) {
        for (KeyCombination k : bindings(id)) if (k.match(e)) return true;
        return false;
    }

    /** The first action (in list order) among those with the given context that the key event triggers, or null. */
    public static Action find(KeyEvent e, java.util.function.Predicate<Action> usable) {
        for (Action a : ALL.values()) {
            if (usable.test(a) && matches(e, a.id())) return a;
        }
        return null;
    }

    /** What to show for the action's keys, for example "Ctrl+K" or "Ctrl+Y / Ctrl+Shift+Z"; empty if it has none. */
    public static String label(String id) {
        List<String> parts = new ArrayList<>();
        for (KeyCombination k : bindings(id)) parts.add(k.getDisplayText());
        return String.join(" / ", parts);
    }

    /** {@code text (Ctrl+K)}: a tooltip with the current key of the action added, or just the text if it has none. */
    public static String tip(String text, String id) {
        String l = label(id);
        return l.isEmpty() ? text : text + " (" + l + ")";
    }

    // ---------------------------------------------------------------- conflicts

    /** The other actions that already use this key combination in a context that overlaps the action's own. */
    public static List<Action> conflicts(String id, KeyCombination key) {
        Action me = ALL.get(id);
        List<Action> out = new ArrayList<>();
        if (me == null || key == null) return out;
        for (Action other : ALL.values()) {
            if (other.id().equals(id) || !other.context().overlaps(me.context())) continue;
            for (KeyCombination k : bindings(other.id())) {
                if (k.equals(key)) {
                    out.add(other);
                    break;
                }
            }
        }
        return out;
    }

    /** Adds the key to the action, taking it away from any action that overlaps and uses it (that action may end up with no key). */
    public static List<Action> takeKey(String id, KeyCombination key) {
        List<Action> losers = conflicts(id, key);
        for (Action l : losers) {
            List<KeyCombination> rest = new ArrayList<>(bindings(l.id()));
            rest.removeIf(k -> k.equals(key));
            set(l.id(), rest);
        }
        List<KeyCombination> mine = new ArrayList<>(bindings(id));
        if (!mine.contains(key)) mine.add(key);
        set(id, mine);
        return losers;
    }

    /** Actions that currently have no key at all. */
    public static List<Action> unassigned() {
        List<Action> out = new ArrayList<>();
        for (Action a : ALL.values()) if (bindings(a.id()).isEmpty() && !a.defaults().stream().allMatch(String::isEmpty)) out.add(a);
        return out;
    }
}
