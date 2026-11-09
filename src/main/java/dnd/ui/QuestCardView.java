package dnd.ui;

import dnd.model.Quest;
import dnd.storage.ProjectStore;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** A read-only summary of a quest: status, who gave it, the steps so far and the items it involves. */
public class QuestCardView extends VBox {
    private final ProjectStore store;

    public QuestCardView(ProjectStore store) {
        this.store = store;
        setSpacing(5);
        setPadding(new Insets(12));
        setMaxWidth(380);
        setStyle("-fx-background-color: -t-surface; -fx-border-color: -t-border; -fx-border-width: 1;");
    }

    public QuestCardView(ProjectStore store, Quest q) {
        this(store);
        refresh(q);
    }

    /** The colour of a status ("#RRGGBB"). */
    public static String statusColor(Quest.Status s) {
        return switch (s) {
            case OPEN -> "#6b7280";
            case ACTIVE -> "#2563eb";
            case ON_HOLD -> "#c2820a";
            case DONE -> "#15803d";
            case FAILED -> "#b91c1c";
        };
    }

    public void refresh(Quest q) {
        getChildren().clear();
        Label title = new Label(q.name == null || q.name.isBlank() ? "(unnamed quest)" : q.name);
        title.setWrapText(true);
        title.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: -t-text;");
        Label status = new Label(q.status.label.toUpperCase(java.util.Locale.ROOT));
        status.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 1 7 1 7; -fx-background-color: "
                + statusColor(q.status) + ";");
        getChildren().addAll(title, status);
        line("Given by", q.giverName(store::itemNameOrNull));
        line("Reward", q.reward);
        if (q.summary != null && !q.summary.isBlank()) {
            Label s = new Label(q.summary.trim());
            s.setWrapText(true);
            s.setMaxWidth(350);
            s.setStyle("-fx-text-fill: -t-text2;");
            getChildren().add(s);
        }
        if (!q.steps.isEmpty()) {
            Label head = new Label("Steps  (" + q.progressText() + ")");
            head.setStyle("-fx-font-size: 11px; -fx-text-fill: -t-text3; -fx-padding: 4 0 0 0;");
            getChildren().add(head);
            for (Quest.Step st : q.steps) {
                Label l = new Label((st.done ? "✓  " : "•  ") + st.text);
                l.setWrapText(true);
                l.setMaxWidth(350);
                l.setStyle(st.done ? "-fx-text-fill: -t-text3;" : "-fx-text-fill: -t-text;");
                if (st.done) l.setStyle(l.getStyle() + " -fx-strikethrough: true;");
                getChildren().add(l);
            }
        }
        if (!q.related.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String id : q.related) {
                String n = store.itemNameOrNull(id);
                if (n == null) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append(n);
            }
            if (sb.length() > 0) line("Involves", sb.toString());
        }
    }

    private void line(String label, String value) {
        if (value == null || value.isBlank()) return;
        Label l = new Label(label + ": " + value.trim());
        l.setWrapText(true);
        l.setMaxWidth(350);
        l.setStyle("-fx-text-fill: -t-text2;");
        getChildren().add(l);
    }
}
