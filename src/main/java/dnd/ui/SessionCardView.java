package dnd.ui;

import dnd.model.Session;
import dnd.storage.ProjectStore;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** A read-only summary of a session: when it was, the recap, what happened and what came up. */
public class SessionCardView extends VBox {
    private final ProjectStore store;

    public SessionCardView(ProjectStore store) {
        this.store = store;
        setSpacing(5);
        setPadding(new Insets(12));
        setMaxWidth(380);
        setStyle("-fx-background-color: -t-surface; -fx-border-color: -t-border; -fx-border-width: 1;");
    }

    public SessionCardView(ProjectStore store, Session s) {
        this(store);
        refresh(s);
    }

    /** "12 March 2026  -  14th of Mirtul (day 31)", or whatever parts are filled in. */
    public static String datesText(Session s) {
        StringBuilder sb = new StringBuilder();
        if (s.realDate != null && !s.realDate.isBlank()) sb.append(s.realDate.trim());
        String world = s.inWorldDate == null ? "" : s.inWorldDate.trim();
        if (s.campaignDay > 0) world += (world.isEmpty() ? "" : " ") + "(day " + s.campaignDay + ")";
        if (!world.isEmpty()) sb.append(sb.length() > 0 ? "  -  " : "").append(world);
        return sb.toString();
    }

    public void refresh(Session s) {
        getChildren().clear();
        Label title = new Label(s.label());
        title.setWrapText(true);
        title.setStyle("-fx-font-size: 1.417em; -fx-font-weight: bold; -fx-text-fill: -t-text;");
        getChildren().add(title);
        String dates = datesText(s);
        if (!dates.isEmpty()) {
            Label d = new Label(dates);
            d.setStyle("-fx-font-size: 0.917em; -fx-text-fill: -t-text3;");
            getChildren().add(d);
        }
        block("Last time on...", s.recap);
        block("What happened", s.summary);
        block("Next", s.nextSteps);
        if (!s.related.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String id : s.related) {
                String n = store.itemNameOrNull(id);
                if (n == null) continue;
                if (sb.length() > 0) sb.append(", ");
                sb.append(n);
            }
            if (sb.length() > 0) block("Came up", sb.toString());
        }
    }

    private void block(String head, String text) {
        if (text == null || text.isBlank()) return;
        Label h = new Label(head);
        h.setStyle("-fx-font-size: 0.917em; -fx-text-fill: -t-text3; -fx-padding: 4 0 0 0;");
        String shown = text.trim();
        if (shown.length() > 600) shown = shown.substring(0, 597) + "...";
        Label t = new Label(shown);
        t.setWrapText(true);
        t.setMaxWidth(350);
        t.setStyle("-fx-text-fill: -t-text;");
        getChildren().addAll(h, t);
    }
}
