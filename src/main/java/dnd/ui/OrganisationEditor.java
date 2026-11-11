package dnd.ui;

import dnd.model.Organisation;
import dnd.model.TreeNode;
import dnd.storage.ProjectStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;

import static dnd.ui.FormKit.labelled;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A tab for editing one organisation: a form on the left with the members (double-click a member to open its card)
 * and a live summary on the right.
 */
public class OrganisationEditor extends BorderPane implements ItemView {
    private final Organisation org;
    private final ProjectStore store;
    private final Consumer<String> openItem;
    private final OrganisationCardView preview;
    private final VBox memberRows = new VBox(4);

    /**
     * @param openItem     opens a project item in a tab
     * @param memberPicker lets the user pick a character card or another organisation to add as a member
     */
    public OrganisationEditor(Organisation org, ProjectStore store, Consumer<String> onRename, Consumer<String> openItem,
                              Supplier<List<String>> memberPicker) {
        this.org = org;
        this.store = store;
        this.openItem = openItem;
        this.preview = new OrganisationCardView(store);

        TextField name = new TextField(org.name);
        name.setStyle("-fx-font-size: 1.500em; -fx-font-weight: bold;");
        name.textProperty().addListener((o, a, n) -> {
            if (n.isBlank()) return;
            org.name = n.trim();
            changed();
            onRename.accept(org.name);
        });
        TextField type = FormKit.field(org.type, x -> org.type = x, "e.g. Thieves guild, Noble house, Adventuring party", this::changed);
        TextField base = FormKit.field(org.base, x -> org.base = x, "Where they are found", this::changed);
        TextArea summary = FormKit.area(org.summary, x -> org.summary = x, "Who they are, in a few lines", 4, this::changed);
        TextArea goals = FormKit.area(org.goals, x -> org.goals = x, "What the group wants", 3, this::changed);
        TextArea notes = FormKit.area(org.notes, x -> org.notes = x, "Your own notes: secrets, rivals, who really runs it...", 4, this::changed);

        Button add = new Button("+ Add member...");
        add.setTooltip(IconButtons.tip("Add player characters, NPCs, monsters or other organisations to this group (Ctrl-click or Shift-click to pick several)"));
        add.setOnAction(e -> {
            boolean any = false;
            for (String id : memberPicker.get()) any |= org.addMember(id, "");
            if (any) {
                rebuildMembers();
                changed();
            }
        });
        Label hint = new Label("Double-click a member to open its card. Hover it for a preview.");
        hint.setStyle("-fx-font-size: 0.917em; -fx-text-fill: -t-text3;");
        rebuildMembers();

        HBox typeAndBase = new HBox(12, labelled("Type", type), labelled("Based in", base));
        VBox form = new VBox(12,
                labelled("Name", name),
                typeAndBase,
                labelled("Summary", summary),
                labelled("Wants", goals),
                labelled("Members", new VBox(6, hint, memberRows, add)),
                labelled("Notes", notes));
        form.setPadding(new Insets(14));
        ScrollPane formScroll = new ScrollPane(form);
        formScroll.setFitToWidth(true);

        ScrollPane previewScroll = new ScrollPane(preview);
        previewScroll.setPadding(new Insets(10));
        previewScroll.setFitToWidth(true);
        previewScroll.setStyle("-fx-background: -t-paperbg;");

        SplitPane split = new SplitPane(formScroll, previewScroll);
        Panels.remember(split, "editor.organisation", 0.62);
        setCenter(split);
        preview.refresh(org);
    }

    private void rebuildMembers() {
        memberRows.getChildren().clear();
        for (Organisation.Member m : new ArrayList<>(org.members)) memberRows.getChildren().add(memberRow(m));
    }

    private Node memberRow(Organisation.Member m) {
        TreeNode node = store.project.root.find(m.id);
        String found = node == null ? null : node.name;

        Node icon = node == null ? new Region() : NodeIcons.icon(node, false);
        Label who = new Label(found == null ? "(deleted item)" : found);
        who.setMinWidth(150);
        who.setPrefWidth(190);
        if (found != null) {
            Runnable colour = () -> who.setStyle("-fx-text-fill: " + (Theme.isDark() ? "#6aa5ff" : "#2563eb") + "; -fx-underline: true; -fx-cursor: hand;");
            colour.run();
            Theme.darkProperty().addListener((o, was, now) -> colour.run());
            Tooltip tip = IconButtons.tip("");
            tip.setShowDelay(javafx.util.Duration.millis(350));
            tip.setShowDuration(javafx.util.Duration.minutes(5));
            tip.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
            tip.setOnShowing(e -> tip.setGraphic(ItemPreview.build(store, m.id)));
            who.setTooltip(tip);
        } else {
            who.setStyle("-fx-text-fill: -t-text3;");
        }
        for (Node n : new Node[]{icon, who}) {
            n.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && store.itemExists(m.id)) {
                    openItem.accept(m.id);
                    e.consume();
                }
            });
        }

        Label kind = new Label(OrganisationCardView.memberKind(store, m.id));
        kind.setMinWidth(70);
        kind.setStyle("-fx-font-size: 0.833em; -fx-text-fill: -t-text3;");

        TextField role = new TextField(m.role);
        role.setPromptText("Role, e.g. Leader");
        role.textProperty().addListener((o, a, n) -> {
            m.role = n;
            changed();
        });
        HBox.setHgrow(role, Priority.ALWAYS);

        Button open = new Button("Open");
        open.setTooltip(IconButtons.tip("Open this member"));
        open.setDisable(found == null);
        open.setOnAction(e -> openItem.accept(m.id));
        Button remove = new Button("✕");
        remove.setTooltip(IconButtons.tip("Take this member out of the group (the card itself stays)"));
        remove.setOnAction(e -> {
            org.removeMember(m.id);
            rebuildMembers();
            changed();
        });
        HBox row = new HBox(8, icon, who, kind, role, open, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void changed() {
        store.markOrganisationDirty(org);
        preview.refresh(org);
    }

    // ================================================================ ItemView

    @Override public String itemId() { return org.id; }

    @Override public void undo() { }

    @Override public void redo() { }

    @Override public void activated() { }

    @Override public boolean selectTool(int index) { return false; }
}
