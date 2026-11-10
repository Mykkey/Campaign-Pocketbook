package dnd.ui;

import dnd.storage.ProjectStore;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The settings of the app as a whole (not of the campaign that is open): a window with a tab for each group of settings.
 * Changes apply at once and are remembered; it can be opened before any campaign is, from the project list.
 */
public final class SettingsDialog {
    private static final String DEFAULT_FONT = "System default";
    private static final java.util.List<String> PREFERRED_FONTS = java.util.List.of("OpenDyslexic", "Atkinson Hyperlegible", "Lexend", "Verdana",
            "Tahoma", "Segoe UI", "Calibri", "Arial", "Trebuchet MS", "Georgia", "Comic Sans MS", "Helvetica Neue", "Helvetica", "DejaVu Sans", "Noto Sans");
    private static Stage open;
    private static TabPane tabs;

    private SettingsDialog() { }

    /** One tab of the dialog: its title, its content and what Reset does on it. */
    private record Page(String title, Node content, Runnable reset) { }

    /** Shows the settings window, or brings it to the front. */
    public static void show(Window owner) {
        show(owner, null);
    }

    /** Shows the settings window with the tab of that title selected, if there is one. */
    public static void show(Window owner, String tabTitle) {
        if (open == null) build(owner);
        else {
            open.toFront();
            open.requestFocus();
        }
        if (tabTitle != null) {
            for (Tab t : tabs.getTabs()) if (t.getText().equals(tabTitle)) tabs.getSelectionModel().select(t);
        }
    }

    private static void build(Window owner) {
        List<Page> pages = new ArrayList<>();
        pages.add(appearance());
        pages.add(accessibility());
        pages.add(new Page("Shortcuts", new ShortcutsPane(), ShortcutsPane::resetAll));
        pages.add(data());

        tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        for (Page p : pages) {
            Button reset = new Button("Reset this tab to the defaults");
            reset.setOnAction(e -> {
                p.reset().run();
                rebuild(owner);
            });
            VBox box = new VBox(12, p.content(), new Region(), reset);
            VBox.setVgrow(box.getChildren().get(1), Priority.ALWAYS);
            box.setPadding(new Insets(14));
            javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(box);
            scroll.setFitToWidth(true);
            Tab t = new Tab(p.title(), scroll);
            tabs.getTabs().add(t);
        }

        Stage stage = new Stage();
        stage.setTitle("Settings");
        if (owner != null) stage.initOwner(owner);
        Scene scene = new Scene(tabs, UiScale.px(760), UiScale.px(560));
        scene.setOnKeyPressed(e -> {
            if (dnd.keys.Shortcuts.matches(e, "dialog.close")) stage.close();
        });
        stage.setScene(scene);
        Runnable stopListening = AppSettings.addListener((k, v) -> {
            if (k.equals(UiScale.KEY) || k.equals("ui.font")) Platform.runLater(() -> rebuild(owner));
        });
        stage.setOnHidden(e -> {
            stopListening.run();
            open = null;
            tabs = null;
        });
        open = stage;
        stage.show();
    }

    /** Rebuilds the window so the controls show the values after a reset or an import. */
    private static void rebuild(Window owner) {
        if (open == null) return;
        int selected = tabs.getSelectionModel().getSelectedIndex();
        Stage old = open;
        double x = old.getX(), y = old.getY();
        old.close();
        build(owner);
        open.setX(x);
        open.setY(y);
        tabs.getSelectionModel().select(Math.max(0, selected));
    }

    private static Label heading(String text) {
        Label l = new Label(text.toUpperCase());
        l.setStyle("-fx-font-size: 0.917em; -fx-font-weight: bold; -fx-text-fill: -t-heading; -fx-padding: 6 0 0 0;");
        return l;
    }

    private static Label note(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 0.917em;");
        return l;
    }

    private static CheckBox check(String text, String key, boolean def) {
        CheckBox c = new CheckBox(text);
        c.setSelected(AppSettings.getBoolean(key, def));
        c.setOnAction(e -> AppSettings.setBoolean(key, c.isSelected()));
        return c;
    }

    // ================================================================ tabs

    private static Page appearance() {
        ComboBox<String> theme = new ComboBox<>();
        theme.getItems().addAll("Light", "Dark");
        theme.setValue(Theme.isDark() ? "Dark" : "Light");
        theme.setOnAction(e -> Theme.setDark("Dark".equals(theme.getValue())));
        javafx.beans.value.ChangeListener<Boolean> follow = (o, was, now) -> theme.setValue(now ? "Dark" : "Light"); // the top-bar button changes it too
        theme.getProperties().put("follow", follow); // keeps the listener alive as long as the box
        Theme.darkProperty().addListener(new javafx.beans.value.WeakChangeListener<>(follow));
        HBox themeRow = new HBox(10, new Label("Colours"), theme);
        themeRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        // ---- text size: changes every window and screen
        int current = UiScale.clampPercent(AppSettings.getInt(UiScale.KEY, UiScale.DEFAULT));
        javafx.scene.control.Slider size = new javafx.scene.control.Slider(UiScale.MIN, UiScale.MAX, current);
        size.setMajorTickUnit(20);
        size.setMinorTickCount(1);
        size.setSnapToTicks(false);
        size.setShowTickMarks(true);
        size.setPrefWidth(320);
        Label sizeLabel = new Label(current + "%");
        sizeLabel.setMinWidth(50);
        Label sample = new Label("The quick brown fox jumps over the lazy dog. 0123456789");
        sample.setStyle("-fx-font-size: " + UiScale.em(UiScale.DESIGN_PX) + ";");
        Runnable commit = () -> {
            int v = UiScale.clampPercent((int) Math.round(size.getValue() / 5.0) * 5);
            if (v != AppSettings.getInt(UiScale.KEY, UiScale.DEFAULT)) AppSettings.setInt(UiScale.KEY, v);
        };
        size.valueProperty().addListener((o, a1, n1) -> {
            sizeLabel.setText(Math.round(n1.doubleValue() / 5.0) * 5 + "%");
            if (!size.isValueChanging()) commit.run();
        });
        size.valueChangingProperty().addListener((o, was, now) -> {
            if (!now) commit.run();
        });
        Button smaller = new Button("Smaller"), bigger = new Button("Bigger"), normal = new Button("100%");
        smaller.setOnAction(e -> size.setValue(Math.max(UiScale.MIN, size.getValue() - 10)));
        bigger.setOnAction(e -> size.setValue(Math.min(UiScale.MAX, size.getValue() + 10)));
        normal.setOnAction(e -> size.setValue(UiScale.DEFAULT));
        HBox sizeRow = new HBox(8, smaller, size, bigger, sizeLabel, normal);
        sizeRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // ---- the font
        ComboBox<String> font = new ComboBox<>();
        font.setEditable(true);
        font.getItems().add(DEFAULT_FONT);
        java.util.List<String> installed = javafx.scene.text.Font.getFamilies();
        for (String f : PREFERRED_FONTS) if (installed.contains(f)) font.getItems().add(f);
        String chosen = AppSettings.getString("ui.font", "");
        font.setValue(chosen.isBlank() ? DEFAULT_FONT : chosen);
        font.setPrefWidth(260);
        font.setOnAction(e -> {
            String v = font.getValue() == null ? "" : font.getValue().trim();
            if (v.isEmpty() || v.equals(DEFAULT_FONT)) AppSettings.remove("ui.font");
            else if (installed.contains(v)) AppSettings.setString("ui.font", v);
            else font.setValue(AppSettings.getString("ui.font", DEFAULT_FONT)); // not an installed font: keep the old one
        });
        boolean friendly = installed.contains("OpenDyslexic") || installed.contains("Atkinson Hyperlegible") || installed.contains("Lexend");
        HBox fontRow = new HBox(10, new Label("Font"), font);
        fontRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox box = new VBox(10,
                heading("Text size"), sizeRow, sample,
                note("Makes the text and buttons in every window bigger or smaller. Whiteboards and maps keep their own zoom (use the wheel or Ctrl+= there)."),
                heading("Font"), fontRow,
                note(friendly ? "Fonts that are easier to read for many people are in the list."
                        : "Install a font such as OpenDyslexic, Atkinson Hyperlegible or Lexend on your computer and it appears in the list (you can also type the name of any installed font)."),
                heading("Colours"), themeRow,
                note("You can also switch with the sun or moon button in the top bar."),
                heading("Text and links"),
                check("Underline names of your cards in whiteboard text (name hints)", "nameHints", true),
                check("Show a preview when the mouse rests on a link or a note", "linkPreviews", true));
        return new Page("Appearance", box, () -> {
            AppSettings.remove(UiScale.KEY);
            AppSettings.remove("ui.font");
            AppSettings.remove("nameHints");
            AppSettings.remove("linkPreviews");
            Theme.setDark(false);
        });
    }

    private static Page accessibility() {
        ComboBox<String> speed = new ComboBox<>();
        int[] ms = {150, 500, 1000, 2500};
        String[] names = {"Fast (a moment)", "Normal (half a second)", "Slow (a second)", "Very slow (2 seconds and a half)"};
        speed.getItems().addAll(names);
        int now = AppSettings.getInt("ui.tooltipDelay", 150);
        int pick = 0;
        for (int i = 0; i < ms.length; i++) if (Math.abs(ms[i] - now) < Math.abs(ms[pick] - now)) pick = i;
        speed.setValue(names[pick]);
        speed.setOnAction(e -> {
            int i = speed.getItems().indexOf(speed.getValue());
            if (i >= 0) AppSettings.setInt("ui.tooltipDelay", ms[i]);
        });
        HBox speedRow = new HBox(10, new Label("Hints on buttons appear"), speed);
        speedRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox box = new VBox(10,
                heading("Seeing the screen"),
                check("High contrast: black and white with heavy outlines and a strong focus ring (works with light and dark)", "ui.highContrast", false),
                check("Colour-blind-safe colours: other colours for conditions, with a two-letter code on tokens and in the turn order", "ui.colourBlind", false),
                note("Bigger text and a different font are on the Appearance tab."),
                heading("Movement"),
                check("Reduce motion: no gliding or fading (the turn order jumps to the current turn, the hint in the player window just disappears)", "ui.reduceMotion", false),
                heading("Hints"), speedRow,
                note("Hints already on screen change the next time you open a tab. Everything in the app can be done with the keyboard, and every key can be changed on the Shortcuts tab. "
                        + "Maps and whiteboards are pictures, so a screen reader cannot read what is drawn on them."));
        return new Page("Accessibility", box, () -> {
            AppSettings.remove("ui.highContrast");
            AppSettings.remove("ui.colourBlind");
            AppSettings.remove("ui.reduceMotion");
            AppSettings.remove("ui.tooltipDelay");
        });
    }

    private static Page data() {
        Button openFolder = new Button("Open the folder where your campaigns are kept");
        openFolder.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().open(ProjectStore.baseDir().toFile());
            } catch (IOException | RuntimeException ex) {
                new Alert(Alert.AlertType.INFORMATION, "Your campaigns are kept in:\n" + ProjectStore.baseDir()).show();
            }
        });
        Button export = new Button("Save these settings to a file...");
        export.setOnAction(e -> exportSettings());
        Button imp = new Button("Load settings from a file...");
        imp.setOnAction(e -> importSettings());
        Button all = new Button("Reset every setting to the defaults...");
        all.setOnAction(e -> {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Every setting goes back to its default, including your shortcuts. Your campaigns are not touched.", ButtonType.OK, ButtonType.CANCEL);
            a.setHeaderText("Reset all settings?");
            if (open != null) a.initOwner(open);
            Optional<ButtonType> r = a.showAndWait();
            if (r.isPresent() && r.get() == ButtonType.OK) {
                AppSettings.resetAll();
                Theme.setDark(false);
                rebuild(open == null ? null : open.getOwner());
            }
        });
        Label where = new Label("Settings file: " + ProjectStore.baseDir().resolve("settings.properties"));
        where.setWrapText(true);
        where.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 0.917em;");
        VBox box = new VBox(10,
                heading("Your data"), openFolder,
                note("Campaigns, backups and these settings are all kept in that folder, apart from the campaigns' own files."),
                heading("Moving to another computer"), export, imp,
                note("A settings file holds everything on these tabs, including your shortcuts. Loading one replaces the current settings."),
                heading("Start again"), all, where);
        return new Page("Data & updates", box, () -> { });
    }

    private static void exportSettings() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save settings");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Settings file", "*.properties"));
        fc.setInitialFileName("campaign-pocketbook-settings.properties");
        File f = fc.showSaveDialog(open);
        if (f == null) return;
        try {
            AppSettings.exportTo(f.toPath());
        } catch (IOException ex) {
            new Alert(Alert.AlertType.ERROR, "Could not save the file: " + ex.getMessage()).show();
        }
    }

    private static void importSettings() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Load settings");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Settings file", "*.properties"));
        File f = fc.showOpenDialog(open);
        if (f == null) return;
        try {
            int n = AppSettings.importFrom(f.toPath());
            Theme.setDark("dark".equals(AppSettings.getString("theme", "light")));
            Alert a = new Alert(Alert.AlertType.INFORMATION, n + " settings loaded.", ButtonType.OK);
            if (open != null) a.initOwner(open);
            a.showAndWait();
            rebuild(open == null ? null : open.getOwner());
        } catch (IOException ex) {
            new Alert(Alert.AlertType.ERROR, "Could not load the file: " + ex.getMessage()).show();
        }
    }
}
