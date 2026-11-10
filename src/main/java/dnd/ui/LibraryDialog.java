package dnd.ui;

import dnd.library.CommonEnemies;
import dnd.library.SrdLibrary;
import dnd.model.Card;
import dnd.model.Spell;
import dnd.model.TreeNode;
import dnd.storage.ProjectStore;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Browser for the SRD monsters and spells the DM has imported. Monsters can be turned into cards in the open
 * project or added to the Common Enemies shelf; spells can be copied or put on the open whiteboard.
 */
public final class LibraryDialog {
    private LibraryDialog() { }

    private static Stage open;

    private static final String SOURCE_PAGE = "https://github.com/5e-bits/5e-database/tree/main/src/2014/en";
    private static final String MONSTERS_URL = "https://raw.githubusercontent.com/5e-bits/5e-database/main/src/2014/en/5e-SRD-Monsters.json";
    private static final String SPELLS_URL = "https://raw.githubusercontent.com/5e-bits/5e-database/main/src/2014/en/5e-SRD-Spells.json";

    private static final String[] CR_BANDS = {"Any CR", "CR 0 - 1/2", "CR 1 - 4", "CR 5 - 10", "CR 11 - 16", "CR 17+"};

    public static void show(Window owner, ProjectStore store, javafx.application.HostServices hostServices,
                            Consumer<String> openItem, Runnable treeChanged, Consumer<String> addToBoard) {
        if (open != null && open.isShowing()) {
            open.toFront();
            open.requestFocus();
            return;
        }
        SrdLibrary lib = SrdLibrary.shared();

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Label status = new Label();
        status.setStyle("-fx-text-fill: -t-text3;");

        Tab monstersTab = new Tab("Monsters");
        Tab spellsTab = new Tab("Spells");
        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            monstersTab.setContent(monstersPane(lib, store, openItem, treeChanged, owner));
            spellsTab.setContent(spellsPane(lib, store, openItem, treeChanged, addToBoard));
            status.setText(lib.monsters().size() + " monsters, " + lib.spells().size() + " spells imported");
        };
        rebuild[0].run();
        tabs.getTabs().addAll(monstersTab, spellsTab);

        Stage stage = new Stage();
        Button importBtn = new Button("Import SRD file...");
        importBtn.setStyle("-fx-font-weight: bold;");
        importBtn.setTooltip(IconButtons.tip("Choose 5e-SRD-Monsters.json and/or 5e-SRD-Spells.json (you can pick both at once)"));
        importBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Choose the SRD JSON file(s)");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON files", "*.json"));
            List<File> files = fc.showOpenMultipleDialog(stage);
            if (files == null || files.isEmpty()) return;
            StringBuilder msg = new StringBuilder();
            Alert.AlertType type = Alert.AlertType.INFORMATION;
            for (File f : files) {
                try {
                    SrdLibrary.ImportResult r = lib.importFile(f.toPath());
                    msg.append("Imported ").append(r.count()).append(r.kind() == dnd.library.SrdImporter.Kind.MONSTERS ? " monsters" : " spells")
                            .append(" from ").append(f.getName()).append(".\n");
                } catch (IOException ex) {
                    type = Alert.AlertType.WARNING;
                    msg.append(ex.getMessage()).append('\n');
                }
            }
            Alert a = new Alert(type, msg.toString().trim());
            a.initOwner(stage);
            a.showAndWait();
            rebuild[0].run();
        });
        Label hint = new Label("The SRD data is not bundled with the app. Download both files once (your browser will show the "
                + "text: press Ctrl+S to save it as a .json file), then press Import.");
        hint.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 0.917em;");
        hint.setWrapText(true);
        Hyperlink dlMonsters = link("Download monsters (5e-SRD-Monsters.json)", MONSTERS_URL, hostServices);
        Hyperlink dlSpells = link("Download spells (5e-SRD-Spells.json)", SPELLS_URL, hostServices);
        Hyperlink page = link("Open the folder on GitHub", SOURCE_PAGE, hostServices);
        HBox top = new HBox(12, importBtn, status);
        top.setAlignment(Pos.CENTER_LEFT);
        HBox links = new HBox(14, new Label("Get the files:"), dlMonsters, dlSpells, page);
        links.setAlignment(Pos.CENTER_LEFT);
        VBox header = new VBox(4, top, links, hint);
        header.setPadding(new Insets(8, 10, 8, 10));

        BorderPane root = new BorderPane(tabs);
        root.setTop(header);
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("Monster and spell library");
        Scene scene = new Scene(root, UiScale.px(1150), UiScale.px(720));
        scene.setOnKeyPressed(e -> {
            if (dnd.keys.Shortcuts.matches(e, "dialog.close") && !(scene.getFocusOwner() instanceof TextInputControl)) stage.close();
        });
        stage.setScene(scene);
        stage.setOnHidden(e -> open = null);
        open = stage;
        stage.show();
    }

    private static Hyperlink link(String text, String url, javafx.application.HostServices hostServices) {
        Hyperlink h = new Hyperlink(text);
        h.setTooltip(IconButtons.tip(url));
        h.setOnAction(e -> {
            if (hostServices != null) hostServices.showDocument(url);
        });
        return h;
    }

    // ================================================================ monsters

    private static Node monstersPane(SrdLibrary lib, ProjectStore store, Consumer<String> openItem, Runnable treeChanged, Window owner) {
        List<Card> all = lib.monsters();
        if (all.isEmpty()) return emptyNote("No monsters imported yet. Use the download links above, then press Import SRD file... and choose 5e-SRD-Monsters.json.");

        TextField search = new TextField();
        search.setPromptText("Search by name...");
        Set<String> typeSet = new LinkedHashSet<>();
        typeSet.add("Any type");
        all.stream().map(c -> baseType(c.creatureType)).filter(t -> !t.isBlank()).sorted().forEach(typeSet::add);
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList(typeSet));
        type.setValue("Any type");
        ComboBox<String> cr = new ComboBox<>(FXCollections.observableArrayList(CR_BANDS));
        cr.setValue(CR_BANDS[0]);
        Label count = new Label();

        FilteredList<Card> shown = new FilteredList<>(FXCollections.observableArrayList(all), c -> true);
        Runnable filter = () -> {
            String q = search.getText().trim().toLowerCase();
            String t = type.getValue();
            int band = cr.getItems().indexOf(cr.getValue());
            shown.setPredicate(c -> (q.isEmpty() || c.name.toLowerCase().contains(q))
                    && ("Any type".equals(t) || baseType(c.creatureType).equals(t))
                    && inBand(c.challenge, band));
            count.setText(shown.size() + " of " + all.size());
        };
        search.textProperty().addListener((o, a, n) -> filter.run());
        type.setOnAction(e -> filter.run());
        cr.setOnAction(e -> filter.run());
        filter.run();

        ListView<Card> list = new ListView<>(shown);
        list.setPrefWidth(300);
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Card c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : c.name + "   (CR " + c.challenge + ")");
            }
        });

        StatBlockView preview = new StatBlockView(store);
        ScrollPane previewScroll = new ScrollPane(preview);
        previewScroll.setFitToWidth(true);
        previewScroll.setPadding(new Insets(10));
        previewScroll.setStyle("-fx-background: -t-paperbg;");
        Button toProject = new Button("Add to project as a card");
        toProject.setStyle("-fx-font-weight: bold;");
        toProject.setTooltip(IconButtons.tip("Makes a card in the open project (in an \"" + SRD_FOLDER + "\" folder) that you can edit freely"));
        Button toCommon = new Button("Add to common enemies");
        toCommon.setTooltip(IconButtons.tip("Adds it to the dropdown in the encounter builder and the battle's Add dialog, in every project"));
        toProject.setDisable(true);
        toCommon.setDisable(true);
        list.getSelectionModel().selectedItemProperty().addListener((o, a, c) -> {
            toProject.setDisable(c == null);
            toCommon.setDisable(c == null);
            if (c != null) preview.refresh(c);
        });
        toProject.setOnAction(e -> {
            Card c = list.getSelectionModel().getSelectedItem();
            if (c == null) return;
            TreeNode folder = null;
            for (TreeNode ch : store.project.root.children) if (ch.folder && SRD_FOLDER.equals(ch.name)) folder = ch;
            if (folder == null) folder = store.createFolder(store.project.root, SRD_FOLDER);
            Card made = store.createCard(folder, c.copy());
            treeChanged.run();
            openItem.accept(made.id);
        });
        toCommon.setOnAction(e -> {
            Card c = list.getSelectionModel().getSelectedItem();
            if (c == null) return;
            CommonEnemies shelf = CommonEnemies.shared();
            boolean already = shelf.list().stream().anyMatch(x -> x.name.equalsIgnoreCase(c.name));
            if (already) {
                Alert a = new Alert(Alert.AlertType.CONFIRMATION, "There is already a common enemy called " + c.name + ". Add another copy?",
                        ButtonType.OK, ButtonType.CANCEL);
                a.initOwner(owner);
                if (a.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            }
            shelf.add(c);
            Alert a = new Alert(Alert.AlertType.INFORMATION, c.name + " is now in your common enemies.");
            a.initOwner(owner);
            a.show();
        });

        HBox buttons = new HBox(8, toProject, toCommon);
        buttons.setAlignment(Pos.CENTER_LEFT);
        buttons.setPadding(new Insets(8));
        BorderPane right = new BorderPane(previewScroll);
        right.setBottom(buttons);

        HBox filters = new HBox(6, type, cr);
        VBox left = new VBox(6, search, filters, count, list);
        left.setPadding(new Insets(8));
        VBox.setVgrow(list, Priority.ALWAYS);
        left.setPrefWidth(320);
        left.setMinWidth(180);
        return Panels.sideSplit(left, right, true, "dialog.library.monsters", 0.28);
    }

    private static final String SRD_FOLDER = "SRD Monsters";

    private static String baseType(String creatureType) {
        if (creatureType == null) return "";
        int i = creatureType.indexOf(" (");
        return (i > 0 ? creatureType.substring(0, i) : creatureType).trim().toLowerCase();
    }

    /** Whether a challenge rating such as "1/4" or "5" falls in the chosen band (0 = any). */
    static boolean inBand(String challenge, int band) {
        if (band <= 0) return true;
        double v = crValue(challenge);
        return switch (band) {
            case 1 -> v <= 0.5;
            case 2 -> v >= 1 && v <= 4;
            case 3 -> v >= 5 && v <= 10;
            case 4 -> v >= 11 && v <= 16;
            default -> v >= 17;
        };
    }

    static double crValue(String cr) {
        if (cr == null) return 0;
        String s = cr.trim();
        try {
            if (s.contains("/")) {
                String[] p = s.split("/");
                return Double.parseDouble(p[0]) / Double.parseDouble(p[1]);
            }
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ================================================================ spells

    private static Node spellsPane(SrdLibrary lib, ProjectStore store, Consumer<String> openItem, Runnable treeChanged, Consumer<String> addToBoard) {
        List<Spell> all = lib.spells();
        if (all.isEmpty()) return emptyNote("No spells imported yet. Use the download links above, then press Import SRD file... and choose 5e-SRD-Spells.json.");

        TextField search = new TextField();
        search.setPromptText("Search by name...");
        ComboBox<String> level = new ComboBox<>(FXCollections.observableArrayList("Any level", "Cantrip", "1st", "2nd", "3rd",
                "4th", "5th", "6th", "7th", "8th", "9th"));
        level.setValue("Any level");
        Set<String> classSet = new LinkedHashSet<>();
        classSet.add("Any class");
        all.stream().flatMap(s -> s.classes.stream()).sorted().forEach(classSet::add);
        ComboBox<String> cls = new ComboBox<>(FXCollections.observableArrayList(classSet));
        cls.setValue("Any class");
        Set<String> schoolSet = new LinkedHashSet<>();
        schoolSet.add("Any school");
        all.stream().map(s -> s.school).filter(s -> !s.isBlank()).sorted().forEach(schoolSet::add);
        ComboBox<String> school = new ComboBox<>(FXCollections.observableArrayList(schoolSet));
        school.setValue("Any school");
        Label count = new Label();

        FilteredList<Spell> shown = new FilteredList<>(FXCollections.observableArrayList(all), s -> true);
        Runnable filter = () -> {
            String q = search.getText().trim().toLowerCase();
            int lv = level.getItems().indexOf(level.getValue()) - 1; // -1 = any
            String c = cls.getValue(), sc = school.getValue();
            shown.setPredicate(s -> (q.isEmpty() || s.name.toLowerCase().contains(q))
                    && (lv < 0 || s.level == lv)
                    && ("Any class".equals(c) || s.classes.contains(c))
                    && ("Any school".equals(sc) || s.school.equals(sc)));
            count.setText(shown.size() + " of " + all.size());
        };
        search.textProperty().addListener((o, a, n) -> filter.run());
        level.setOnAction(e -> filter.run());
        cls.setOnAction(e -> filter.run());
        school.setOnAction(e -> filter.run());
        filter.run();

        ListView<Spell> list = new ListView<>(shown);
        list.setPrefWidth(300);
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Spell s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : s.name + "   (" + (s.level == 0 ? "cantrip" : "level " + s.level) + ")");
            }
        });

        TextArea text = new TextArea();
        text.setEditable(false);
        text.setWrapText(true);
        text.setPromptText("Pick a spell on the left.");
        Button copy = new Button("Copy");
        Button add = new Button("Add to board");
        Button asCard = new Button("Add as spell card");
        asCard.setStyle("-fx-font-weight: bold;");
        asCard.setTooltip(IconButtons.tip("Makes an editable spell card in this project (in a \"Spells\" folder). If you already made one from this spell, it is opened instead."));
        asCard.setDisable(true);
        copy.setDisable(true);
        add.setDisable(true);
        list.getSelectionModel().selectedItemProperty().addListener((o, a, s) -> {
            text.setText(s == null ? "" : s.toText());
            copy.setDisable(s == null);
            asCard.setDisable(s == null);
            add.setDisable(s == null || addToBoard == null);
        });
        copy.setOnAction(e -> {
            ClipboardContent c = new ClipboardContent();
            c.putString(text.getText());
            Clipboard.getSystemClipboard().setContent(c);
        });
        add.setOnAction(e -> {
            if (addToBoard != null && !text.getText().isBlank()) addToBoard.accept(text.getText());
        });
        asCard.setOnAction(e -> {
            dnd.model.Spell s = list.getSelectionModel().getSelectedItem();
            if (s == null) return;
            dnd.model.Spell existing = store.spellFromLibrary(s.id);
            if (existing != null) {
                openItem.accept(existing.id);
                return;
            }
            TreeNode folder = null;
            for (TreeNode ch : store.project.root.children) if (ch.folder && "Spells".equals(ch.name)) folder = ch;
            if (folder == null) folder = store.createFolder(store.project.root, "Spells");
            dnd.model.Spell card = s.copy();
            card.libraryId = s.id;
            store.createSpell(folder, card);
            treeChanged.run();
            openItem.accept(card.id);
        });
        HBox buttons = new HBox(8, asCard, add, copy);
        buttons.setAlignment(Pos.CENTER_LEFT);
        buttons.setPadding(new Insets(8));
        BorderPane right = new BorderPane(text);
        right.setBottom(buttons);
        right.setPadding(new Insets(8, 8, 0, 8));

        HBox row1 = new HBox(6, level, cls);
        VBox left = new VBox(6, search, row1, school, count, list);
        left.setPadding(new Insets(8));
        VBox.setVgrow(list, Priority.ALWAYS);
        left.setPrefWidth(320);
        left.setMinWidth(180);
        return Panels.sideSplit(left, right, true, "dialog.library.spells", 0.28);
    }

    private static Node emptyNote(String message) {
        Label l = new Label(message);
        l.setWrapText(true);
        l.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 1.167em;");
        VBox box = new VBox(l);
        box.setPadding(new Insets(30));
        return box;
    }
}
