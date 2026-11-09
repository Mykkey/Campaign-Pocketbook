package dnd.ui;

import dnd.model.Session;
import dnd.storage.ProjectStore;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A tab for one session: it opens with the previous session's recap ("last time on...") to read out, then the
 * date, what happened, a recap for next time, plans, and the items that came up.
 */
public class SessionEditor extends BorderPane implements ItemView {
    private final Session session;
    private final ProjectStore store;
    private final Consumer<String> openItem;
    private final SessionCardView preview;
    private final ListView<String> related = new ListView<>();
    private final Label lastTimeTitle = new Label();
    private final Label lastTimeText = new Label();

    public SessionEditor(Session session, ProjectStore store, Consumer<String> onRename, Consumer<String> openItem,
                         Supplier<Optional<String>> picker) {
        this.session = session;
        this.store = store;
        this.openItem = openItem;
        this.preview = new SessionCardView(store);

        TextField name = new TextField(session.name);
        name.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        name.textProperty().addListener((o, a, n) -> {
            if (n.isBlank()) return;
            session.name = n.trim();
            changed();
            onRename.accept(session.name);
        });

        Spinner<Integer> number = new Spinner<>(1, 9999, Math.max(1, session.number));
        number.setEditable(true);
        number.setPrefWidth(90);
        number.valueProperty().addListener((o, a, n) -> {
            session.number = n == null ? 1 : n;
            changed();
            refreshLastTime();
        });

        DatePicker played = new DatePicker(parse(session.realDate));
        played.setPromptText("yyyy-mm-dd");
        played.valueProperty().addListener((o, a, n) -> {
            session.realDate = n == null ? "" : n.toString();
            changed();
        });
        TextField world = field(session.inWorldDate, x -> session.inWorldDate = x, "e.g. 14th of Mirtul, 1492");
        Spinner<Integer> day = new Spinner<>(0, 999999, Math.max(0, session.campaignDay));
        day.setEditable(true);
        day.setPrefWidth(110);
        day.valueProperty().addListener((o, a, n) -> {
            session.campaignDay = n == null ? 0 : n;
            changed();
        });

        // ---- the previous session's recap
        lastTimeTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: -t-text;");
        lastTimeText.setWrapText(true);
        lastTimeText.setStyle("-fx-text-fill: -t-text2;");
        Button copy = new Button("Copy");
        copy.setTooltip(new Tooltip("Copy this recap to the clipboard"));
        copy.setOnAction(e -> {
            ClipboardContent c = new ClipboardContent();
            c.putString(lastTimeText.getText());
            Clipboard.getSystemClipboard().setContent(c);
        });
        Button openPrev = new Button("Open that session");
        openPrev.setOnAction(e -> {
            Session prev = Session.previous(store.allSessions(), session);
            if (prev != null) openItem.accept(prev.id);
        });
        HBox lastButtons = new HBox(6, copy, openPrev);
        VBox lastTime = new VBox(4, lastTimeTitle, lastTimeText, lastButtons);
        lastTime.setPadding(new Insets(10));
        lastTime.setStyle("-fx-background-color: -t-surface; -fx-border-color: -t-border; -fx-border-width: 1 1 1 4;");
        lastTime.visibleProperty().bind(lastTimeTitle.textProperty().isNotEmpty());
        lastTime.managedProperty().bind(lastTime.visibleProperty());
        refreshLastTime();

        TextArea recap = area(session.recap, x -> session.recap = x, "A few lines to read at the start of the next session", 4);
        TextArea summary = area(session.summary, x -> session.summary = x, "What happened: who they met, what they decided, how the fight went...", 10);
        TextArea next = area(session.nextSteps, x -> session.nextSteps = x, "Loose ends and what you plan for next time", 4);

        related.setPrefHeight(110);
        related.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(String id, boolean empty) {
                super.updateItem(id, empty);
                if (empty || id == null) {
                    setText(null);
                    return;
                }
                String n = store.itemName(id);
                setText(n == null ? "(deleted item)" : n);
            }
        });
        related.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) openSelected();
        });
        rebuildRelated();
        Button addRel = new Button("Add...");
        addRel.setTooltip(new Tooltip("Add a character, map, quest or whiteboard that came up in this session"));
        addRel.setOnAction(e -> picker.get().ifPresent(id -> {
            if (!session.related.contains(id)) {
                session.related.add(id);
                rebuildRelated();
                changed();
            }
        }));
        Button openRel = new Button("Open");
        openRel.setOnAction(e -> openSelected());
        Button removeRel = new Button("Remove");
        removeRel.setOnAction(e -> {
            String sel = related.getSelectionModel().getSelectedItem();
            if (sel != null) {
                session.related.remove(sel);
                rebuildRelated();
                changed();
            }
        });

        VBox form = new VBox(12,
                lastTime,
                labelled("Title", name),
                new HBox(12, labelled("Session no.", number), labelled("Played on", played),
                        labelled("In the game world", world), labelled("Campaign day", day)),
                labelled("Recap for next time (\"last time on...\")", recap),
                labelled("What happened", summary),
                labelled("Next steps", next),
                labelled("Came up", new VBox(6, related, new HBox(6, addRel, openRel, removeRel))));
        form.setPadding(new Insets(14));
        ScrollPane formScroll = new ScrollPane(form);
        formScroll.setFitToWidth(true);

        ScrollPane previewScroll = new ScrollPane(preview);
        previewScroll.setPadding(new Insets(10));
        previewScroll.setFitToWidth(true);
        previewScroll.setStyle("-fx-background: -t-paperbg;");

        SplitPane split = new SplitPane(formScroll, previewScroll);
        split.setDividerPositions(0.64);
        setCenter(split);
        preview.refresh(session);
    }

    private static LocalDate parse(String s) {
        try {
            return s == null || s.isBlank() ? null : LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Shows the recap of the session played before this one, or hides the box if there is none. */
    private void refreshLastTime() {
        Session prev = Session.previous(store.allSessions(), session);
        if (prev == null) {
            lastTimeTitle.setText("");
            lastTimeText.setText("");
            return;
        }
        String gap = Session.gapText(prev, session);
        lastTimeTitle.setText("Last time on... (" + prev.label() + (gap == null ? "" : ", " + gap) + ")");
        lastTimeText.setText(prev.recap == null || prev.recap.isBlank() ? "(no recap was written for that session)" : prev.recap.trim());
    }

    private void openSelected() {
        String sel = related.getSelectionModel().getSelectedItem();
        if (sel != null && store.itemExists(sel)) openItem.accept(sel);
    }

    private void rebuildRelated() {
        related.setItems(FXCollections.observableArrayList(session.related));
    }

    private void changed() {
        store.markSessionDirty(session);
        preview.refresh(session);
    }

    private TextField field(String value, Consumer<String> set, String prompt) {
        TextField t = new TextField(value == null ? "" : value);
        t.setPromptText(prompt);
        t.textProperty().addListener((o, a, n) -> {
            set.accept(n);
            changed();
        });
        return t;
    }

    private TextArea area(String value, Consumer<String> set, String prompt, int rows) {
        TextArea t = new TextArea(value == null ? "" : value);
        t.setPromptText(prompt);
        t.setWrapText(true);
        t.setPrefRowCount(rows);
        t.textProperty().addListener((o, a, n) -> {
            set.accept(n);
            changed();
        });
        return t;
    }

    private static VBox labelled(String label, Node n) {
        Label l = new Label(label);
        l.setStyle("-fx-font-size: 11px; -fx-text-fill: -t-text3;");
        VBox box = new VBox(3, l, n);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    // ================================================================ ItemView

    @Override public String itemId() { return session.id; }

    @Override public void undo() { }

    @Override public void redo() { }

    @Override public void activated() { refreshLastTime(); }

    @Override public boolean selectTool(int index) { return false; }
}
