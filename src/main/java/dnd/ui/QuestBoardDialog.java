package dnd.ui;

import dnd.model.Quest;
import dnd.storage.ProjectStore;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** All the quests in the project as columns by status; double-click one to open it, right-click to move it. */
public final class QuestBoardDialog {
    private QuestBoardDialog() { }

    private static Stage open;

    public static void show(Window owner, ProjectStore store, Consumer<String> openItem) {
        if (open != null) {
            open.toFront();
            return;
        }
        TextField filter = new TextField();
        filter.setPromptText("Filter by name, giver or step...");
        filter.setPrefWidth(280);
        Label hint = new Label("Double-click a quest to open it. Right-click to change its status.");
        hint.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 11px;");
        HBox top = new HBox(12, filter, hint);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(10, 12, 6, 12));

        Map<Quest.Status, ListView<Quest>> lists = new EnumMap<>(Quest.Status.class);
        Map<Quest.Status, Label> heads = new EnumMap<>(Quest.Status.class);
        Map<Quest.Status, FilteredList<Quest>> filtered = new EnumMap<>(Quest.Status.class);
        HBox columns = new HBox(10);
        columns.setPadding(new Insets(6, 12, 12, 12));
        for (Quest.Status st : Quest.Status.values()) {
            Label head = new Label(st.label);
            head.setStyle("-fx-font-weight: bold; -fx-text-fill: " + QuestCardView.statusColor(st) + ";");
            heads.put(st, head);
            ListView<Quest> list = new ListView<>();
            list.setPlaceholder(new Label("-"));
            list.setCellFactory(v -> new ListCell<>() {
                @Override
                protected void updateItem(Quest q, boolean empty) {
                    super.updateItem(q, empty);
                    if (empty || q == null) {
                        setText(null);
                        return;
                    }
                    String progress = q.progressText();
                    String from = q.giverName(store::itemNameOrNull);
                    setText(q.name + (progress.isEmpty() ? "" : "\n" + progress) + (from.isBlank() ? "" : "\nfrom " + from));
                }
            });
            list.setOnMouseClicked(e -> {
                Quest q = list.getSelectionModel().getSelectedItem();
                if (e.getClickCount() == 2 && q != null) openItem.accept(q.id);
            });
            ContextMenu menu = new ContextMenu();
            for (Quest.Status target : Quest.Status.values()) {
                MenuItem mi = new MenuItem("Mark as " + target.label.toLowerCase(Locale.ROOT));
                mi.setOnAction(e -> {
                    Quest q = list.getSelectionModel().getSelectedItem();
                    if (q == null || q.status == target) return;
                    q.status = target;
                    store.markQuestDirty(q);
                    load(store, filtered, lists, heads, filter.getText());
                });
                menu.getItems().add(mi);
            }
            list.setContextMenu(menu);
            VBox.setVgrow(list, Priority.ALWAYS);
            VBox col = new VBox(6, head, list);
            col.setPrefWidth(230);
            HBox.setHgrow(col, Priority.ALWAYS);
            columns.getChildren().add(col);
            lists.put(st, list);
        }
        load(store, filtered, lists, heads, "");
        filter.textProperty().addListener((o, a, n) -> load(store, filtered, lists, heads, n));

        BorderPane root = new BorderPane(columns);
        root.setTop(top);
        Stage stage = new Stage();
        stage.setTitle("Quests");
        if (owner != null) stage.initOwner(owner);
        Scene scene = new Scene(root, 1180, 560);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) stage.close();
        });
        stage.setScene(scene);
        stage.setOnHidden(e -> open = null);
        open = stage;
        stage.show();
    }

    /** Fills every column from the project, keeping only quests that match the filter text. */
    private static void load(ProjectStore store, Map<Quest.Status, FilteredList<Quest>> filtered,
                             Map<Quest.Status, ListView<Quest>> lists, Map<Quest.Status, Label> heads, String text) {
        String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        Map<Quest.Status, ObservableList<Quest>> by = new EnumMap<>(Quest.Status.class);
        for (Quest.Status s : Quest.Status.values()) by.put(s, FXCollections.observableArrayList());
        for (String id : store.allQuestIds()) {
            Quest quest = store.quest(id);
            if (quest != null && matches(quest, q, store::itemNameOrNull)) by.get(quest.status).add(quest);
        }
        for (Quest.Status s : Quest.Status.values()) {
            lists.get(s).setItems(by.get(s));
            heads.get(s).setText(s.label + "  (" + by.get(s).size() + ")");
        }
    }

    /** True when the quest's name, giver, reward, summary or any step contains the filter text. */
    public static boolean matches(Quest quest, String lowerFilter) {
        return matches(quest, lowerFilter, id -> null);
    }

    /** As {@link #matches(Quest, String)}, also matching the current name of the character who gave the quest. */
    public static boolean matches(Quest quest, String lowerFilter, java.util.function.Function<String, String> nameOf) {
        if (lowerFilter == null || lowerFilter.isEmpty()) return true;
        java.util.List<String> fields = new ArrayList<>(java.util.List.of(nz(quest.name), nz(quest.giver), nz(quest.giverName(nameOf)), nz(quest.reward), nz(quest.summary)));
        for (Quest.Step s : quest.steps) fields.add(nz(s.text));
        for (String f : fields) if (f.toLowerCase(Locale.ROOT).contains(lowerFilter)) return true;
        return false;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
