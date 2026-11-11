package dnd.ui;

import dnd.model.Organisation;
import dnd.model.TreeNode;
import dnd.storage.ProjectStore;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
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
 * A tab for editing one organisation: a form on the left with the members (double-click a member to open its card, and say
 * who each one reports to) and on the right a summary and a chart of the chain of command.
 */
public class OrganisationEditor extends BorderPane implements ItemView {
    private static final String TOP = "(nobody: top of the group)";

    private final Organisation org;
    private final ProjectStore store;
    private final Consumer<String> openItem;
    private final OrganisationCardView preview;
    private final HierarchyChart chart;
    private final VBox memberRows = new VBox(4);
    private final TabPane views = new TabPane();
    /** Restyles the member links when the look changes; held here so the weak listener below stays alive. */
    private final ChangeListener<Boolean> themeListener = (o, was, now) -> rebuildMembers();

    /**
     * @param openItem     opens a project item in a tab
     * @param memberPicker lets the user pick character cards or other organisations to add as members
     */
    public OrganisationEditor(Organisation org, ProjectStore store, Consumer<String> onRename, Consumer<String> openItem,
                              Supplier<List<String>> memberPicker) {
        this.org = org;
        this.store = store;
        this.openItem = openItem;
        this.preview = new OrganisationCardView(store);
        this.chart = new HierarchyChart(store, openItem);

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
        Label hint = new Label("Double-click a member to open its card. Hover it for a preview. \"Reports to\" builds the chain of command shown on the Hierarchy tab, and this list follows it: the top of the group first, then the people who report to them, indented.");
        hint.setStyle("-fx-font-size: 0.917em; -fx-text-fill: -t-text3;");
        hint.setWrapText(true);
        Theme.darkProperty().addListener(new WeakChangeListener<>(themeListener));
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
        Tab summaryTab = new Tab("Summary", previewScroll);
        Tab hierarchyTab = new Tab("Hierarchy", chart);
        summaryTab.setClosable(false);
        hierarchyTab.setClosable(false);
        views.getTabs().addAll(summaryTab, hierarchyTab);

        SplitPane split = new SplitPane(formScroll, views);
        Panels.remember(split, "editor.organisation", 0.55);
        setCenter(split);
        preview.refresh(org);
        chart.refresh(org);
    }

    private void rebuildMembers() {
        memberRows.getChildren().clear();
        for (Organisation.Ranked r : org.inHierarchyOrder()) memberRows.getChildren().add(memberRow(r.member(), r.depth()));
    }

    private Node memberRow(Organisation.Member m, int depth) {
        TreeNode node = store.project.root.find(m.id);
        String found = node == null ? null : node.name;

        Node icon = node == null ? new Region() : NodeIcons.icon(node, false);
        Label who = new Label(found == null ? "(deleted item)" : found);
        who.setMinWidth(130);
        who.setPrefWidth(170);
        if (found != null) {
            who.setStyle("-fx-text-fill: " + (Theme.isDark() ? "#6aa5ff" : "#2563eb") + "; -fx-underline: true; -fx-cursor: hand;");
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
        kind.setMinWidth(62);
        kind.setStyle("-fx-font-size: 0.833em; -fx-text-fill: -t-text3;");

        TextField role = new TextField(m.role);
        role.setPromptText("Role, e.g. Leader");
        role.setPrefWidth(190);
        role.textProperty().addListener((o, a, n) -> {
            m.role = n;
            changed();
        });

        ComboBox<String> boss = new ComboBox<>();
        boss.getItems().add(TOP);
        for (String id : org.possibleBosses(m.id)) boss.getItems().add(id);
        boss.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(String id) {
                if (id == null || TOP.equals(id)) return TOP;
                String n = store.itemNameOrNull(id);
                return n == null ? "(deleted item)" : n;
            }

            @Override public String fromString(String s) { return s; }
        });
        String current = org.bossOf(m.id);
        boss.setValue(current == null ? TOP : current);
        boss.setPrefWidth(170);
        boss.setTooltip(IconButtons.tip("Who this member reports to. This builds the chain of command on the Hierarchy tab."));
        boss.setOnAction(e -> {
            String chosen = boss.getValue();
            if (chosen == null) return;
            if (org.setBoss(m.id, TOP.equals(chosen) ? null : chosen)) {
                changed();
                javafx.application.Platform.runLater(this::rebuildMembers); // what the others may report to has changed
            }
        });

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
        Label reports = new Label("reports to");
        reports.setStyle("-fx-font-size: 0.833em; -fx-text-fill: -t-text3;");
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox top = new HBox(8, icon, who, kind, gap, open, remove);
        top.setAlignment(Pos.CENTER_LEFT);
        HBox bottom = new HBox(8, role, reports, boss);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(0, 0, 0, 24));
        VBox row = new VBox(3, top, bottom);
        row.setPadding(new Insets(4, 0, 6, Math.min(depth, 6) * 22)); // those who report to someone are indented under them
        row.setStyle("-fx-border-color: transparent transparent -t-border transparent;");
        return row;
    }

    private void changed() {
        store.markOrganisationDirty(org);
        preview.refresh(org);
        chart.refresh(org);
    }

    // ================================================================ ItemView

    @Override public String itemId() { return org.id; }

    @Override public void undo() { }

    @Override public void redo() { }

    @Override public void activated() {
        // members' names may have changed since this tab was last shown
        rebuildMembers();
        preview.refresh(org);
        chart.refresh(org);
    }

    @Override public boolean selectTool(int index) { return false; }
}
