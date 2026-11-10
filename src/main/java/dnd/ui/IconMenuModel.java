package dnd.ui;

/** The wording of icon-dropdown rows, kept apart from the screen code so it can be tested. */
public final class IconMenuModel {
    private IconMenuModel() { }

    /** A row's text: the label, with the current key of its action added ("Loot generator  (Ctrl+L)") if it has one. */
    public static String rowText(String label, String keyLabel) {
        return keyLabel == null || keyLabel.isBlank() ? label : label + "  (" + keyLabel + ")";
    }

    /** Looks the key up from the shortcut registry for the action id (null id means no key). */
    public static String rowTextFor(String label, String actionId) {
        return rowText(label, actionId == null ? null : dnd.keys.Shortcuts.label(actionId));
    }
}
