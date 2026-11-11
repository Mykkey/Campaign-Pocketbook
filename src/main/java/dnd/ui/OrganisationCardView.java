package dnd.ui;

import dnd.model.Organisation;
import dnd.storage.ProjectStore;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** A read-only summary of an organisation: what it is, where, what it wants and who belongs to it. */
public class OrganisationCardView extends VBox {
    private final ProjectStore store;

    public OrganisationCardView(ProjectStore store) {
        this.store = store;
        setSpacing(5);
        setPadding(new Insets(12));
        setMaxWidth(380);
        setStyle("-fx-background-color: -t-surface; -fx-border-color: -t-border; -fx-border-width: 1;");
    }

    public OrganisationCardView(ProjectStore store, Organisation o) {
        this(store);
        refresh(o);
    }

    /** What a member is, for display: "Player", "NPC", "Monster" for a card, "Organisation" for a group, "" if it is gone. */
    static String memberKind(ProjectStore store, String id) {
        if (store.isOrganisation(id)) return "Organisation";
        if (store.isCard(id)) {
            dnd.model.Card c = store.card(id);
            if (c == null) return "";
            return switch (c.kind) {
                case PLAYER -> "Player";
                case NPC -> "NPC";
                case MONSTER -> "Monster";
            };
        }
        return "";
    }

    public void refresh(Organisation o) {
        getChildren().clear();
        Label title = new Label(o.name == null || o.name.isBlank() ? "(unnamed organisation)" : o.name);
        title.setWrapText(true);
        title.setStyle("-fx-font-size: 1.417em; -fx-font-weight: bold; -fx-text-fill: -t-text;");
        getChildren().add(title);
        if (o.type != null && !o.type.isBlank()) {
            Label type = new Label(o.type.trim().toUpperCase(java.util.Locale.ROOT));
            type.setStyle("-fx-font-size: 0.833em; -fx-font-weight: bold; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 1 7 1 7; -fx-background-color: "
                    + NodeColors.ORGANISATION + ";");
            getChildren().add(type);
        }
        line("Based in", o.base);
        line("Wants", o.goals);
        if (o.summary != null && !o.summary.isBlank()) {
            Label s = new Label(o.summary.trim());
            s.setWrapText(true);
            s.setMaxWidth(350);
            s.setStyle("-fx-text-fill: -t-text2;");
            getChildren().add(s);
        }
        if (!o.members.isEmpty()) {
            Label head = new Label("Members  (" + o.sizeText() + ")");
            head.setStyle("-fx-font-size: 0.917em; -fx-text-fill: -t-text3; -fx-padding: 4 0 0 0;");
            getChildren().add(head);
            for (Organisation.Member m : o.members) {
                String name = store.itemNameOrNull(m.id);
                String role = m.role == null || m.role.isBlank() ? "" : "  -  " + m.role.trim();
                Label l = new Label("•  " + (name == null ? "(deleted item)" : name) + role);
                l.setWrapText(true);
                l.setMaxWidth(350);
                l.setStyle(name == null ? "-fx-text-fill: -t-text3;" : "-fx-text-fill: -t-text;");
                getChildren().add(l);
            }
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
