package dnd.ui;

/** What the workspace needs from whatever is shown in a tab (a whiteboard or a battlemap). */
public interface ItemView {
    /** Id of the whiteboard / battlemap shown. */
    String itemId();

    void undo();

    void redo();

    /** Called when the tab becomes the selected one: restore cursor, take keyboard focus. */
    void activated();

    /**
     * Does what a keyboard shortcut asks (see {@link dnd.keys.Shortcuts}): the id of the action, for example {@code item.copy}
     * or {@code tool.world.3}. Called by the workspace for every key that is bound to an action that applies to this kind of
     * item, even if keyboard focus is on a toolbar control. Returns true if the action was done (the key is then used up);
     * false lets the key go on to whatever else wanted it.
     */
    default boolean perform(String actionId) {
        return false;
    }

    /** Which editor this is, so that only its own shortcuts (tool keys, rotate...) apply: whiteboard, battlemap, world map. */
    default dnd.keys.Shortcuts.Context keyContext() {
        return dnd.keys.Shortcuts.Context.ITEM;
    }

    /** True while the picture itself (not a toolbar button) has the keyboard focus. */
    default boolean hasCanvasFocus() {
        return false;
    }

    /**
     * Delete / Backspace pressed while the tab is showing (even if focus is on a toolbar control): removes the
     * selected objects. Returns true if there was a selection and it was deleted.
     */
    default boolean deleteSelection() {
        return false;
    }

    /**
     * Escape pressed while the tab is showing (even if focus is on a toolbar control): drop the selection and
     * any armed tool. Returns true if it handled the key.
     */
    default boolean escape() {
        return false;
    }

    /** Number-key tool shortcut: switch to the tool at {@code index} (0-based). Returns true if handled. */
    boolean selectTool(int index);
}
