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
     * Gives the item first go at a Ctrl+key shortcut (copy, paste, ...) even if keyboard focus is on a
     * toolbar control. Returns true if it handled the key.
     */
    default boolean shortcut(javafx.scene.input.KeyEvent e) {
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
