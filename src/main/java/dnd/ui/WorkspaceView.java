package dnd.ui;

import dnd.model.Board;
import dnd.model.TreeNode;
import dnd.nav.NavigationHistory;
import dnd.search.SearchService;
import dnd.storage.ProjectStore;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.*;

/**
 * The main window for an open project: project tree on the left, toolbar + tabbed whiteboards on the
 * right, back/forward navigation, backlinks and search along the top.
 */
public class WorkspaceView extends BorderPane implements BoardView.Host {
    private final ProjectStore store;
    private final Runnable onExit;
    private final ToolState ts = new ToolState();
    private final TabPane tabs = new TabPane();
    private final Map<String, Tab> open = new LinkedHashMap<>();
    private final TreeView<TreeNode> tree = new TreeView<>();
    private final NavigationHistory history = new NavigationHistory();
    private final HBox trail = new HBox(2);
    private final Button backBtn = new Button("<");
    private final Button fwdBtn = new Button(">");
    private final javafx.scene.control.Menu backlinks = new javafx.scene.control.Menu("Backlinks: whiteboards that link here");
    private final javafx.scene.control.Menu recentMenu = new javafx.scene.control.Menu("Recent items");
    private final TextField search = new TextField();
    private final javafx.stage.Popup searchPopup = new javafx.stage.Popup();
    private final VBox searchList = new VBox();
    private final PauseTransition saver = new PauseTransition(Duration.seconds(1));
    private final ToolbarPanel toolbar;
    private boolean suppressHistory;
    private boolean restoring;

    private final javafx.application.HostServices hostServices;

    public WorkspaceView(ProjectStore store, Runnable onExit, javafx.application.HostServices hostServices) {
        this.hostServices = hostServices;
        this.store = store;
        this.onExit = onExit;

        store.onDirty = () -> {
            saver.setDuration(Duration.millis(GeneralSettings.autosaveMs()));
            saver.playFromStart();
        };
        saver.setOnFinished(e -> save());

        // ---- left: tree
        Label treeTitle = new Label("Project");
        treeTitle.setStyle("-fx-font-weight: bold;");
        Button newBoard = new Button("+ Board");
        newBoard.setOnAction(e -> newBoard(selectedFolder()));
        Button newFolder = new Button("+ Folder");
        newFolder.setOnAction(e -> newFolder(selectedFolder()));
        Button newMap = new Button("+ Battlemap");
        newMap.setTooltip(IconButtons.tip("Create a square or hex battlemap"));
        newMap.setOnAction(e -> newBattleMap(selectedFolder()));
        Button newCard = new Button("+ Card");
        newCard.setTooltip(IconButtons.tip("Create a character or creature card (stat block)"));
        newCard.setOnAction(e -> newCard(selectedFolder()));
        Button newSpell = new Button("+ Spell");
        newSpell.setTooltip(IconButtons.tip("Create a spell card (your own homebrew spell, or edit one imported from the library)"));
        newSpell.setOnAction(e -> newSpell(selectedFolder()));
        Button newItem = new Button("+ Item");
        newItem.setTooltip(IconButtons.tip("Create a magic item card (your own homebrew item, or one from your loot)"));
        newItem.setOnAction(e -> newMagicItem(selectedFolder()));
        Button newQuest = new Button("+ Quest");
        newQuest.setTooltip(IconButtons.tip("Create a quest or plot thread with steps, clues and the people and places it involves"));
        newQuest.setOnAction(e -> newQuest(selectedFolder()));
        Button newOrg = new Button("+ Organisation");
        newOrg.setTooltip(IconButtons.tip("Create an organisation: a guild, faction, family or party, with its members"));
        newOrg.setOnAction(e -> newOrganisation(selectedFolder()));
        Button newSession = new Button("+ Session");
        newSession.setTooltip(IconButtons.tip("Write up a game session: what happened, a recap for next time, and what came up"));
        newSession.setOnAction(e -> newSession(selectedFolder()));
        Button newWorld = new Button("+ World map");
        newWorld.setTooltip(IconButtons.tip("Create a world map: paint land, sea and biomes, then add mountains, forests, towns and labels"));
        newWorld.setOnAction(e -> newWorldMap(selectedFolder()));
        Button newEncounter = new Button("+ Encounter");
        newEncounter.setTooltip(IconButtons.tip("Plan a fight: pick creatures and see how hard it is for your party"));
        newEncounter.setOnAction(e -> newEncounter(selectedFolder()));
        newButton(newBoard, null, "New whiteboard: a free canvas for notes, text, images and links");
        newButton(newMap, TreeNode.KIND_BATTLEMAP, "New battlemap: a square or hex map for running combat");
        newButton(newCard, TreeNode.KIND_CARD, "New card: a character or creature (stat block)");
        newButton(newSpell, TreeNode.KIND_SPELL, "New spell: your own homebrew spell, or edit one imported from the library");
        newButton(newItem, TreeNode.KIND_ITEM, "New magic item: your own homebrew item, or one from your loot");
        newButton(newQuest, TreeNode.KIND_QUEST, "New quest: a plot thread with steps, clues and the people and places it involves");
        newButton(newOrg, TreeNode.KIND_ORGANISATION, "New organisation: a guild, faction, family or party, with its members (double-click a member to open its card)");
        newButton(newSession, TreeNode.KIND_SESSION, "New session: what happened, a recap for next time, and what came up");
        newButton(newWorld, TreeNode.KIND_WORLDMAP, "New world map: paint land, sea and biomes, then add mountains, forests, towns and labels");
        newButton(newEncounter, TreeNode.KIND_ENCOUNTER, "New encounter: pick creatures and see how hard the fight is for your party");
        newButton(newFolder, "folder", "New folder: to keep related items together");
        // one row per group, with a faint line between the groups: notes, maps and fights, then reference cards and the folder
        VBox treeButtons = new VBox(3,
                new HBox(4, newBoard, newSession, newQuest),
                treeDivider(),
                new HBox(4, newWorld, newMap, newEncounter),
                treeDivider(),
                new HBox(4, newCard, newOrg, newSpell, newItem, new Separator(javafx.geometry.Orientation.VERTICAL), newFolder));
        tree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE); // Ctrl-click or Shift-click to pick several and move them together
        tree.setCellFactory(v -> createTreeCell(true));
        tree.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                TreeItem<TreeNode> it = tree.getSelectionModel().getSelectedItem();
                if (it != null && !it.getValue().folder) openItem(it.getValue().id);
            }
        });
        tree.setContextMenu(buildTreeMenu());
        tree.setOnKeyPressed(e -> {
            if (e.isAltDown() && (e.getCode() == KeyCode.UP || e.getCode() == KeyCode.DOWN)) {
                moveSelected(e.getCode() == KeyCode.UP ? -1 : 1);
                e.consume();
                return;
            }
            if (e.getCode() == KeyCode.F2) {
                renameSelected();
                e.consume();
            } else if (e.getCode() == KeyCode.DELETE) {
                deleteSelected();
                e.consume();
            }
        });
        Button trashBtn = IconButtons.decorate(new Button(), "delete", "Deleted items: bring back something you deleted (kept for 30 days)");
        trashBtn.setOnAction(e -> openTrash());
        Region titleGap = new Region();
        HBox.setHgrow(titleGap, Priority.ALWAYS);
        HBox titleRow = new HBox(4, treeTitle, titleGap, trashBtn);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        VBox left = new VBox(6, titleRow, treeButtons, tree);
        left.setPadding(new Insets(8));
        VBox.setVgrow(tree, Priority.ALWAYS);
        left.setMinWidth(180);
        left.setPrefWidth(240);

        // ---- top bar
        Button projects = new Button("Projects");
        projects.setOnAction(e -> exit());
        backBtn.setOnAction(e -> goBack());
        fwdBtn.setOnAction(e -> goForward());
        backBtn.setTooltip(IconButtons.tip(dnd.keys.Shortcuts.tip("Back", "global.back")));
        fwdBtn.setTooltip(IconButtons.tip(dnd.keys.Shortcuts.tip("Forward", "global.forward")));
        trail.setAlignment(Pos.CENTER_LEFT);
        backlinks.setGraphic(Icons.of("link"));
        search.setPromptText("Search project...");
        search.setPrefWidth(220);
        searchList.setStyle("-fx-background-color: -t-surface; -fx-border-color: -t-border; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 8, 0.2, 0, 2);");
        searchPopup.getContent().add(searchList);
        searchPopup.setAutoHide(true); // a click anywhere else closes it
        search.setOnKeyPressed(ev -> {
            if (ev.getCode() == javafx.scene.input.KeyCode.ESCAPE) searchPopup.hide();
        });
        search.setOnAction(e -> runSearch());
        // keep the results in step with what is typed while they are showing
        search.textProperty().addListener((o, was, now) -> {
            if (searchPopup.isShowing()) runSearch();
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button names = new Button("Names...");
        names.setTooltip(IconButtons.tip(dnd.keys.Shortcuts.tip("Random character name generator", "global.names")));
        names.setOnAction(e -> openNameGenerator());
        Button npc = new Button("NPC...");
        npc.setTooltip(IconButtons.tip(dnd.keys.Shortcuts.tip("Random NPC generator", "global.npc")));
        npc.setOnAction(e -> openNpcGenerator());
        Button loot = new Button("Loot...");
        loot.setTooltip(IconButtons.tip(dnd.keys.Shortcuts.tip("Random treasure generator", "global.loot")));
        loot.setOnAction(e -> openLootGenerator());
        Button rules = new Button("Rules...");
        rules.setTooltip(IconButtons.tip(dnd.keys.Shortcuts.tip("Quick reference: conditions, cover, grappling, travel, resting and more", "global.rules")));
        rules.setOnAction(e -> RulesReferenceDialog.show(getScene().getWindow()));
        Button settings = new Button("Settings...");
        settings.setTooltip(IconButtons.tip(dnd.keys.Shortcuts.tip("Settings for the whole app: text size, colours, accessibility and keyboard shortcuts", "global.settings")));
        settings.setOnAction(e -> SettingsDialog.show(getScene().getWindow()));
        Button helpBtn = IconButtons.decorate(new Button(), "help", dnd.keys.Shortcuts.tip("Help: the guide as a searchable book, with tutorials", "global.help"));
        helpBtn.setOnAction(e -> HelpDialog.show(getScene().getWindow(), hostServices));
        Button relations = new Button("Relations...");
        relations.setTooltip(IconButtons.tip("Family tree and relationship web of your cards"));
        relations.setOnAction(e -> RelationshipsDialog.show(getScene().getWindow(), store, this::openItem));
        Button quests = new Button("Quests...");
        quests.setTooltip(IconButtons.tip("All your quests by status"));
        quests.setOnAction(e -> QuestBoardDialog.show(getScene().getWindow(), store, this::openItem));
        Button timeline = new Button("Timeline...");
        timeline.setTooltip(IconButtons.tip("Your sessions in order, with the recap of each"));
        timeline.setOnAction(e -> TimelineDialog.show(getScene().getWindow(), store, this::openItem));
        Button library = new Button("Library...");
        library.setTooltip(IconButtons.tip("Browse imported SRD monsters and spells"));
        library.setOnAction(e -> openLibrary());
        recentMenu.setGraphic(Icons.of("clock"));
        IconButtons.decorate(projects, "home", "Projects: back to the list of campaigns");
        IconButtons.decorate(backBtn, "back");
        IconButtons.decorate(fwdBtn, "forward");
        IconButtons.decorate(settings, "gear");
        // the generators, reference dialogs and history live in three icon dropdowns instead of twelve buttons
        MenuButton generators = IconMenu.create("dice", "Generators: names, NPCs and treasure");
        generators.getItems().addAll(
                menuRow("names", "Character names", "global.names", names),
                menuRow("person", "NPC generator", "global.npc", npc),
                menuRow("loot", "Treasure and loot", "global.loot", loot));
        MenuButton reference = IconMenu.create("book", "Reference: rules, library, relationships, quests and timeline");
        reference.getItems().addAll(
                menuRow("rules", "Rules quick reference", "global.rules", rules),
                menuRow("book", "Monster and spell library", null, library),
                IconMenu.separator(),
                menuRow("relations", "Family tree and relationship web", null, relations),
                menuRow("quest", "Quest board", null, quests),
                menuRow("calendar", "Session timeline", null, timeline));
        MenuButton historyMenu = IconMenu.create("clock", "History: recent items and what links here");
        historyMenu.getItems().addAll(recentMenu, backlinks);
        // a submenu with no rows cannot be opened, so both are filled as soon as the History button opens
        historyMenu.setOnShowing(e -> {
            rebuildRecent();
            rebuildBacklinks();
        });
        rebuildRecent();
        rebuildBacklinks();
        HBox nav = new HBox(4, projects, backBtn, fwdBtn, trail, spacer, generators, reference, historyMenu, Theme.toggleButton(), helpBtn, settings, search);
        nav.setAlignment(Pos.CENTER_LEFT);
        nav.setPadding(new Insets(6, 8, 0, 8));

        toolbar = new ToolbarPanel(ts, this::currentView);
        VBox top = new VBox(new UpdateBanner(hostServices), nav, toolbar);
        setTop(top);

        // ---- centre: tabs (with a hint behind them while no board is open)
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        Label hint = new Label("Double-click a whiteboard or battlemap in the project tree to open it.");
        hint.setStyle("-fx-text-fill: -t-text3;");
        tabs.visibleProperty().bind(Bindings.isNotEmpty(tabs.getTabs()));
        StackPane center = new StackPane(hint, tabs);

        SplitPane split = new SplitPane(left, center);
        Panels.remember(split, "workspace.tree", 0.17);
        SplitPane.setResizableWithParent(left, false);
        setCenter(split);

        tabs.getSelectionModel().selectedItemProperty().addListener((o, a, t) -> onTabChanged(t));
        tabs.getTabs().addListener((javafx.collections.ListChangeListener<Tab>) c -> persistTabs());

        // tool / style changes
        ts.tool.addListener((o, a, n) -> {
            BoardView v = currentView();
            if (v != null) v.refreshCursor();
        });
        ts.color.addListener((o, a, n) -> style("color"));
        ts.fill.addListener((o, a, n) -> style("fill"));
        ts.useFill.addListener((o, a, n) -> style("fill"));
        ts.width.addListener((o, a, n) -> style("width"));
        ts.fontSize.addListener((o, a, n) -> style("font"));
        ts.bold.addListener((o, a, n) -> style("font"));
        ts.italic.addListener((o, a, n) -> style("font"));

        // keyboard shortcuts: one place decides what a key does (see Shortcuts); the settings can rebind every one of them
        sceneProperty().addListener((o, old, scene) -> {
            if (scene == null) return;
            scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> dispatchKey(scene, ev));
        });

        refreshTree();
        restoreTabs();
        updateNavButtons();
    }

    // ================================================================ keys

    /**
     * Works out which action a key press asks for and does it. Shortcuts with Ctrl or Alt that are global work even in a text
     * box; everything else is left alone while a text box, spinner or list of choices has the focus, so typing is never stolen.
     */
    private void dispatchKey(javafx.scene.Scene scene, javafx.scene.input.KeyEvent ev) {
        javafx.scene.Node focus = scene.getFocusOwner();
        boolean typing = focus instanceof TextInputControl || focus instanceof Spinner<?> || focus instanceof ComboBoxBase<?>;
        ItemView v = currentItem();
        dnd.keys.Shortcuts.Context editor = v == null ? null : v.keyContext();
        dnd.keys.Shortcuts.Action a = dnd.keys.Shortcuts.find(ev, act -> appliesHere(act, typing, editor, v != null));
        if (a == null) return;
        if (runAction(a, v, focus)) ev.consume();
    }

    private static boolean appliesHere(dnd.keys.Shortcuts.Action a, boolean typing, dnd.keys.Shortcuts.Context editor, boolean hasItem) {
        if (typing && !a.typing()) return false;
        return switch (a.context()) {
            case GLOBAL -> true;
            case ITEM -> hasItem;
            case BOARD, BATTLEMAP, WORLDMAP -> editor == a.context();
            case BATTLE -> editor == dnd.keys.Shortcuts.Context.BATTLEMAP;
            default -> false; // player windows and pop-up windows have their own scenes
        };
    }

    private boolean runAction(dnd.keys.Shortcuts.Action a, ItemView v, javafx.scene.Node focus) {
        switch (a.id()) {
            case "global.back" -> goBack();
            case "global.forward" -> goForward();
            case "global.closeTab" -> closeCurrentTab();
            case "global.search" -> search.requestFocus();
            case "global.names" -> openNameGenerator();
            case "global.loot" -> openLootGenerator();
            case "global.rules" -> RulesReferenceDialog.show(getScene().getWindow());
            case "global.npc" -> openNpcGenerator();
            case "global.settings" -> SettingsDialog.show(getScene().getWindow());
            case "global.help" -> HelpDialog.show(getScene().getWindow(), hostServices);
            default -> {
                if (v == null) return false;
                switch (a.id()) {
                    case "item.undo" -> {
                        v.undo();
                        return true;
                    }
                    case "item.redo" -> {
                        v.redo();
                        return true;
                    }
                    default -> { }
                }
                if (a.canvasOnly() && !v.hasCanvasFocus()) return false;
                if (focus == tree && a.id().equals("item.delete")) return false; // the tree deletes its own selected item
                return v.perform(a.id());
            }
        }
        return true;
    }

    // ================================================================ lifecycle

    private boolean saveFailureShown;

    private void save() {
        try {
            store.flush();
            saveFailureShown = false;
        } catch (RuntimeException ex) {
            dnd.util.Log.error("Problem in WorkspaceView", ex);
            if (!saveFailureShown) { // say it once, not on every autosave attempt
                saveFailureShown = true;
                Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Could not save: " + ex.getMessage()
                        + "\nThe app will keep trying; your last good copy is on disk.").show());
            }
        }
    }

    /** The message for an item that would not open: "damaged file" if the file was unreadable, else "not found". */
    private void couldNotOpen(String id, String notFound) {
        String err = store.loadError(id);
        if (err == null) {
            new Alert(Alert.AlertType.WARNING, notFound).show();
            return;
        }
        new Alert(Alert.AlertType.WARNING, "\"" + store.itemName(id) + "\" could not be opened: its file looks damaged.\n\n" + err
                + "\n\nYour other items are not affected, and the file has been left as it is. A backup (Import backup... on the project list) may have a good copy.").show();
    }

    /** The campaign this workspace shows. */
    public ProjectStore store() {
        return store;
    }

    /** Saves everything immediately (called when the window closes or the project is left). */
    public void shutdown() {
        saver.stop();
        PlayerWindow.closeAll();
        WorldPlayerWindow.closeAll();
        BoardView v = currentView();
        if (v != null) v.requestFocus(); // forces any in-place text edit to commit
        store.onDirty = () -> { };
        save();
        if (GeneralSettings.backupOnClose()) dnd.storage.ProjectBackups.backup(store, false); // a copy of the campaign as it is now (skipped if nothing changed)
    }

    private void exit() {
        shutdown();
        onExit.run();
    }

    private void restoreTabs() {
        // opening each tab overwrites activeBoard, so remember which one was selected before we start
        String active = store.project.activeBoard;
        restoring = true;
        try {
            for (String id : new ArrayList<>(store.project.openBoards)) {
                if (store.itemExists(id)) openItem(id);
            }
        } finally {
            restoring = false;
        }
        if (active != null && open.containsKey(active)) {
            tabs.getSelectionModel().select(open.get(active));
        }
        onTabChanged(tabs.getSelectionModel().getSelectedItem());
        persistTabs();
    }

    private void persistTabs() {
        if (restoring) return;
        List<String> ids = new ArrayList<>();
        for (Tab t : tabs.getTabs()) ids.add((String) t.getUserData());
        store.project.openBoards = ids;
        store.markProjectDirty();
    }

    // ================================================================ tabs & navigation

    /** The whiteboard in the selected tab, or null if the tab is a battlemap (or nothing is open). */
    private BoardView currentView() {
        Tab t = tabs.getSelectionModel().getSelectedItem();
        return t != null && t.getContent() instanceof BoardView v ? v : null;
    }

    /** Whatever is in the selected tab, whiteboard or battlemap. */
    private ItemView currentItem() {
        Tab t = tabs.getSelectionModel().getSelectedItem();
        return t != null && t.getContent() instanceof ItemView v ? v : null;
    }

    private BoardView viewFor(String boardId) {
        Tab t = open.get(boardId);
        return t != null && t.getContent() instanceof BoardView v ? v : null;
    }

    @Override
    public void openItem(String id) {
        if (!restoring && store.itemExists(id)) {
            dnd.model.RecentItems.add(store.project.recent, id); // also when the tab was already open
            store.markProjectDirty();
        }
        Tab t = open.get(id);
        if (t == null) {
            Node view;
            if (store.isBattleMap(id)) {
                dnd.model.BattleMap m = store.battleMap(id);
                if (m == null) {
                    couldNotOpen(id, "That battlemap could not be found.");
                    return;
                }
                view = new MapEditor(m, store, this::openItem, this::refreshTree);
            } else if (store.isCard(id)) {
                dnd.model.Card c = store.card(id);
                if (c == null) {
                    couldNotOpen(id, "That card could not be found.");
                    return;
                }
                view = new CardEditor(c, store, newName -> renameFromEditor(id, newName));
            } else if (store.isSpell(id)) {
                dnd.model.Spell sp = store.spell(id);
                if (sp == null) {
                    couldNotOpen(id, "That spell could not be found.");
                    return;
                }
                view = new SpellEditor(sp, store, newName -> renameFromEditor(id, newName));
            } else if (store.isMagicItem(id)) {
                dnd.model.MagicItem mi = store.magicItem(id);
                if (mi == null) {
                    couldNotOpen(id, "That magic item could not be found.");
                    return;
                }
                view = new MagicItemEditor(mi, store, newName -> renameFromEditor(id, newName));
            } else if (store.isQuest(id)) {
                dnd.model.Quest qu = store.quest(id);
                if (qu == null) {
                    couldNotOpen(id, "That quest could not be found.");
                    return;
                }
                view = new QuestEditor(qu, store, newName -> renameFromEditor(id, newName), this::openItem, () -> pickItems("Add to the quest...", "Choose what this quest involves (Ctrl-click or Shift-click to pick several)", n -> !n.folder), () -> pickCard("Who gave this quest?"));
            } else if (store.isOrganisation(id)) {
                dnd.model.Organisation og = store.organisation(id);
                if (og == null) {
                    couldNotOpen(id, "That organisation could not be found.");
                    return;
                }
                view = new OrganisationEditor(og, store, newName -> renameFromEditor(id, newName), this::openItem, () -> pickMember(id));
            } else if (store.isSession(id)) {
                dnd.model.Session se = store.session(id);
                if (se == null) {
                    couldNotOpen(id, "That session could not be found.");
                    return;
                }
                view = new SessionEditor(se, store, newName -> renameFromEditor(id, newName), this::openItem, () -> pickItems("Add to the session...", "Choose what came up in this session (Ctrl-click or Shift-click to pick several)", n -> !n.folder));
            } else if (store.isWorldMap(id)) {
                dnd.model.WorldMap wm = store.worldMap(id);
                if (wm == null) {
                    couldNotOpen(id, "That world map could not be found.");
                    return;
                }
                view = new WorldMapEditor(wm, store, this::openItem);
            } else if (store.isEncounter(id)) {
                dnd.model.Encounter en = store.encounter(id);
                if (en == null) {
                    couldNotOpen(id, "That encounter could not be found.");
                    return;
                }
                view = new EncounterEditor(en, store, newName -> renameFromEditor(id, newName), this::openItem);
            } else {
                Board b = store.board(id);
                if (b == null) {
                    couldNotOpen(id, "That whiteboard could not be found.");
                    return;
                }
                view = new BoardView(b, store, ts, this);
            }
            t = new Tab(store.boardName(id), view);
            t.setUserData(id);
            decorateTab(t, id);
            t.setOnClosed(e -> {
                open.remove(id);
                persistTabs();
            });
            open.put(id, t);
            tabs.getTabs().add(t);
        }
        tabs.getSelectionModel().select(t);
    }

    /** Icon (in the item's colour) and right-click menu for a tab. */
    private void decorateTab(Tab t, String id) {
        TreeNode node = store.project.root.find(id);
        if (node != null) t.setGraphic(NodeIcons.icon(node, false));
        MenuItem close = new MenuItem("Close");
        close.setOnAction(e -> closeTab(id));
        MenuItem others = new MenuItem("Close others");
        others.setOnAction(e -> {
            for (String other : new ArrayList<>(open.keySet())) if (!other.equals(id)) closeTab(other);
        });
        MenuItem all = new MenuItem("Close all");
        all.setOnAction(e -> {
            for (String other : new ArrayList<>(open.keySet())) closeTab(other);
        });
        MenuItem reveal = new MenuItem("Reveal in tree");
        reveal.setOnAction(e -> selectInTree(id));
        t.setContextMenu(new ContextMenu(close, others, all, new SeparatorMenuItem(), reveal));
    }

    private void refreshTabIcons() {
        for (Tab t : tabs.getTabs()) {
            TreeNode node = store.project.root.find((String) t.getUserData());
            if (node != null) t.setGraphic(NodeIcons.icon(node, false));
        }
    }

    private void onTabChanged(Tab t) {
        if (t == null) {
            updateTrail();
            updateNavButtons();
            return;
        }
        String id = (String) t.getUserData();
        if (!suppressHistory && !restoring) history.visit(id);
        store.project.activeBoard = id;
        store.markProjectDirty();
        updateTrail();
        updateNavButtons();
        selectInTree(id);
        boolean whiteboard = t.getContent() instanceof BoardView;
        toolbar.setVisible(whiteboard);
        toolbar.setManaged(whiteboard);
        ItemView v = (ItemView) t.getContent();
        Platform.runLater(v::activated);
    }

    private void goBack() {
        step(history.canBack() ? history.back() : null);
    }

    private void goForward() {
        step(history.canForward() ? history.forward() : null);
    }

    private void step(String id) {
        if (id == null) return;
        if (!store.itemExists(id)) {
            history.forget(id);
            updateNavButtons();
            return;
        }
        suppressHistory = true;
        try {
            openItem(id);
        } finally {
            suppressHistory = false;
        }
        updateTrail();
        updateNavButtons();
    }

    private void closeCurrentTab() {
        Tab t = tabs.getSelectionModel().getSelectedItem();
        if (t != null) closeTab((String) t.getUserData());
    }

    private void closeTab(String boardId) {
        Tab t = open.remove(boardId);
        if (t != null) tabs.getTabs().remove(t);
    }

    private void updateNavButtons() {
        backBtn.setDisable(!history.canBack());
        fwdBtn.setDisable(!history.canForward());
    }

    /** Shows the last few boards visited as a clickable trail, e.g. "Overview > Tavern > Barkeep". */
    private void updateTrail() {
        trail.getChildren().clear();
        List<String> ids = history.trail(5);
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            if (i > 0) trail.getChildren().add(new Label(">"));
            if (i == ids.size() - 1) {
                Label l = new Label(store.boardName(id));
                l.setStyle("-fx-font-weight: bold;");
                trail.getChildren().add(l);
            } else {
                Hyperlink h = new Hyperlink(store.boardName(id));
                h.setOnAction(e -> openItem(id));
                h.setFocusTraversable(false);
                trail.getChildren().add(h);
            }
        }
    }

    /** A dropdown row that does what the matching (hidden) button does, with the button's own icon-free wording kept as its tooltip. */
    private static javafx.scene.control.MenuItem menuRow(String icon, String label, String actionId, Button source) {
        return IconMenu.action(icon, label, actionId, source::fire);
    }

    private void rebuildBacklinks() {
        backlinks.getItems().clear();
        ItemView v = currentItem();
        if (v == null) {
            backlinks.getItems().add(disabled("Nothing open"));
            return;
        }
        List<SearchService.Result> res = SearchService.backlinks(store, v.itemId());
        if (res.isEmpty()) backlinks.getItems().add(disabled("Nothing links here yet"));
        for (SearchService.Result r : res) {
            MenuItem m = new MenuItem(r.boardName + "  -  " + r.snippet);
            m.setOnAction(e -> showResult(r));
            backlinks.getItems().add(m);
        }
    }

    private void rebuildRecent() {
        recentMenu.getItems().clear();
        List<String> ids = dnd.model.RecentItems.existing(store.project.recent, store::itemExists);
        if (ids.isEmpty()) recentMenu.getItems().add(disabled("Nothing opened yet"));
        for (String id : ids) {
            TreeNode n = store.project.root.find(id);
            if (n == null) continue;
            MenuItem mi = new MenuItem(n.name);
            mi.setGraphic(NodeIcons.icon(n, false));
            mi.setOnAction(e -> openItem(id));
            recentMenu.getItems().add(mi);
        }
    }

    private static MenuItem disabled(String text) {
        MenuItem m = new MenuItem(text);
        m.setDisable(true);
        return m;
    }

    /**
     * Shows the matches under the search box. This is a plain popup, not a menu: a menu takes the keyboard for
     * itself, which made a second Enter close the results and left the box looking dead. A popup leaves the
     * keyboard in the search box, so Enter searches again and typing updates the results.
     */
    private void runSearch() {
        List<SearchService.Result> res = SearchService.search(store, search.getText(), 40);
        searchList.getChildren().clear();
        if (search.getText().isBlank()) {
            searchPopup.hide();
            return;
        }
        if (res.isEmpty()) {
            Label none = new Label("No results");
            none.setStyle("-fx-text-fill: -t-text3; -fx-padding: 6 12 6 12;");
            searchList.getChildren().add(none);
        }
        for (SearchService.Result r : res) {
            Label row = new Label(r.boardName + "  -  " + r.snippet);
            row.setMaxWidth(Double.MAX_VALUE);
            row.setStyle(SEARCH_ROW);
            row.setOnMouseEntered(ev -> row.setStyle(SEARCH_ROW + " -fx-background-color: -t-blue;"));
            row.setOnMouseExited(ev -> row.setStyle(SEARCH_ROW));
            row.setOnMouseClicked(ev -> {
                searchPopup.hide();
                showResult(r);
            });
            searchList.getChildren().add(row);
        }
        javafx.geometry.Point2D at = search.localToScreen(0, search.getHeight());
        if (at == null) return;
        searchList.setMinWidth(Math.max(260, search.getWidth()));
        if (searchPopup.isShowing()) {
            searchPopup.setAnchorX(at.getX());
            searchPopup.setAnchorY(at.getY());
        } else {
            searchPopup.show(search, at.getX(), at.getY());
        }
    }

    private static final String SEARCH_ROW = "-fx-padding: 6 12 6 12; -fx-cursor: hand;";

    private void showResult(SearchService.Result r) {
        openItem(r.boardId);
        if (r.objectId != null) {
            BoardView v = viewFor(r.boardId);
            if (v != null) Platform.runLater(() -> v.focusObject(r.objectId));
            else if (currentItem() instanceof WorldMapEditor wme && r.boardId.equals(wme.itemId())) Platform.runLater(() -> wme.focusObject(r.objectId));
        }
    }

    private void openNameGenerator() {
        NameGeneratorDialog.show(getScene().getWindow(), name -> {
            BoardView v = currentView();
            if (v == null) {
                new Alert(Alert.AlertType.INFORMATION, "Open a whiteboard first, then add the name to it.").show();
            } else {
                v.addTextAtCenter(name);
            }
        });
    }

    private void openLibrary() {
        LibraryDialog.show(getScene().getWindow(), store, hostServices, this::openItem, this::refreshTree, text -> {
            BoardView v = currentView();
            if (v == null) {
                new Alert(Alert.AlertType.INFORMATION, "Open a whiteboard first, then add the spell to it.").show();
            } else {
                v.addTextAtCenter(text);
            }
        });
    }

    private void openNpcGenerator() {
        NpcDialog.show(getScene().getWindow(), text -> {
            BoardView v = currentView();
            if (v == null) {
                new Alert(Alert.AlertType.INFORMATION, "Open a whiteboard first, then add the NPC to it.").show();
            } else {
                v.addTextAtCenter(text);
            }
        }, npc -> {
            TreeNode folder = null;
            for (TreeNode ch : store.project.root.children) if (ch.folder && "NPCs".equals(ch.name)) folder = ch;
            if (folder == null) folder = store.createFolder(store.project.root, "NPCs");
            dnd.model.Card card = store.createCard(folder, dnd.npc.NpcCards.toCard(npc));
            refreshTree();
            openItem(card.id);
        });
    }

    private void openLootGenerator() {
        LootDialog.show(getScene().getWindow(), text -> {
            BoardView v = currentView();
            if (v == null) {
                new Alert(Alert.AlertType.INFORMATION, "Open a whiteboard first, then add the loot to it.").show();
            } else {
                v.addTextAtCenter(text);
            }
        });
    }

    private void style(String what) {
        BoardView v = currentView();
        if (v != null && v.hasSelection()) v.applyStyle(what);
    }

    // ================================================================ project tree

    private TreeItem<TreeNode> buildItem(TreeNode n, Set<String> expanded) {
        TreeItem<TreeNode> it = new TreeItem<>(n);
        if (n.folder) {
            it.setExpanded(expanded == null || expanded.contains(n.id));
            for (TreeNode c : n.children) it.getChildren().add(buildItem(c, expanded));
        }
        return it;
    }

    private void collectExpanded(TreeItem<TreeNode> it, Set<String> out) {
        if (it == null) return;
        if (it.isExpanded()) out.add(it.getValue().id);
        for (TreeItem<TreeNode> c : it.getChildren()) collectExpanded(c, out);
    }

    private void refreshTree() {
        Set<String> expanded = null;
        if (tree.getRoot() != null) {
            expanded = new HashSet<>();
            collectExpanded(tree.getRoot(), expanded);
            expanded.add(store.project.root.id);
        }
        tree.setRoot(buildItem(store.project.root, expanded));
        refreshTabIcons();
        Tab t = tabs.getSelectionModel().getSelectedItem();
        if (t != null) selectInTree((String) t.getUserData());
    }

    private void selectInTree(String boardId) {
        TreeItem<TreeNode> it = findItem(tree.getRoot(), boardId);
        if (it != null) {
            tree.getSelectionModel().clearSelection();
            tree.getSelectionModel().select(it);
        }
    }

    /** Selects these items (and only these) in the tree. */
    private void selectInTree(List<String> ids) {
        tree.getSelectionModel().clearSelection();
        for (String id : ids) {
            TreeItem<TreeNode> it = findItem(tree.getRoot(), id);
            if (it != null) tree.getSelectionModel().select(it);
        }
    }

    /** The ids of the selected rows (the root excluded), top to bottom as shown. */
    private List<String> selectedIds() {
        List<String> ids = new ArrayList<>();
        for (TreeItem<TreeNode> it : tree.getSelectionModel().getSelectedItems()) {
            if (it != null && it.getValue() != store.project.root && !ids.contains(it.getValue().id)) ids.add(it.getValue().id);
        }
        return ids;
    }

    private TreeItem<TreeNode> findItem(TreeItem<TreeNode> from, String id) {
        if (from == null) return null;
        if (from.getValue().id.equals(id)) return from;
        for (TreeItem<TreeNode> c : from.getChildren()) {
            TreeItem<TreeNode> f = findItem(c, id);
            if (f != null) return f;
        }
        return null;
    }

    private TreeCell<TreeNode> createTreeCell(boolean draggable) {
        TreeCell<TreeNode> cell = new TreeCell<>() {
            private final javafx.beans.value.ChangeListener<Boolean> expandedListener = (o, was, now) -> show(getItem());

            {
                // the folder icon opens and closes with the folder
                treeItemProperty().addListener((o, old, now) -> {
                    if (old != null) old.expandedProperty().removeListener(expandedListener);
                    if (now != null) now.expandedProperty().addListener(expandedListener);
                });
            }

            private void show(TreeNode n) {
                if (n == null || isEmpty()) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                int count = n.folder ? n.countItems() : 0;
                setText(count > 0 ? n.name + "  (" + count + ")" : n.name);
                setGraphic(NodeIcons.icon(n, n.folder && getTreeItem() != null && getTreeItem().isExpanded()));
            }

            @Override
            protected void updateItem(TreeNode n, boolean empty) {
                super.updateItem(n, empty);
                show(empty ? null : n);
            }
        };
        if (draggable) {
            cell.setOnDragDetected(e -> {
                TreeNode n = cell.getItem();
                if (n == null || n == store.project.root) return;
                // dragging one of several selected rows takes all of them; otherwise just this one
                List<String> ids = selectedIds();
                if (!ids.contains(n.id)) ids = List.of(n.id);
                Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent c = new ClipboardContent();
                c.putString(String.join("\n", ids));
                db.setContent(c);
                e.consume();
            });
            cell.setOnDragOver(e -> {
                if (e.getDragboard().hasString()) {
                    e.acceptTransferModes(TransferMode.MOVE);
                    cell.setStyle(dropStyle(dropZone(cell, e.getY())));
                }
                e.consume();
            });
            cell.setOnDragExited(e -> cell.setStyle(""));
            cell.setOnDragDropped(e -> {
                boolean done = false;
                cell.setStyle("");
                if (e.getDragboard().hasString()) {
                    List<String> dragged = store.movable(List.of(e.getDragboard().getString().split("\n")));
                    TreeNode over = cell.getItem() == null ? store.project.root : cell.getItem();
                    int zone = dropZone(cell, e.getY());
                    TreeNode parent = over == store.project.root ? null : store.project.root.parentOf(over.id);
                    if (!dragged.contains(over.id)) { // dropping something onto itself does nothing
                        if (zone == 0 || parent == null) {
                            // onto a folder (or the empty part of the tree): put them inside, at the end
                            TreeNode target = over.folder ? over : parent == null ? store.project.root : parent;
                            done = store.moveNodesTo(dragged, target, target.children.size()) > 0;
                        } else {
                            // between two items: before (-1) or after (1) the one it was dropped on
                            int at = parent.children.indexOf(over) + (zone > 0 ? 1 : 0);
                            done = store.moveNodesTo(dragged, parent, at) > 0;
                        }
                    }
                    if (done) {
                        refreshTree();
                        selectInTree(dragged);
                    }
                }
                e.setDropCompleted(done);
                e.consume();
            });
        }
        return cell;
    }

    private TreeNode selectedNode() {
        TreeItem<TreeNode> it = tree.getSelectionModel().getSelectedItem();
        return it == null ? null : it.getValue();
    }

    /** Saves the selected battlemap, world map or whiteboard as a PNG picture. */
    private void exportSelectedImage() {
        TreeNode n = selectedNode();
        if (n == null || n.folder) return;
        javafx.stage.Window owner = getScene().getWindow();
        if (n.isBattleMap()) {
            dnd.model.BattleMap m = store.battleMap(n.id);
            if (m != null) BattleExportDialog.show(owner, m, store);
        } else if (n.isWorldMap()) {
            dnd.model.WorldMap m = store.worldMap(n.id);
            if (m != null) WorldExportDialog.show(owner, m, store);
        } else if (n.isBoard()) {
            openItem(n.id);
            Platform.runLater(() -> {
                BoardView v = viewFor(n.id);
                if (v != null) v.exportImage();
            });
        }
    }

    private ContextMenu buildTreeMenu() {
        MenuItem nb = new MenuItem("New whiteboard");
        nb.setOnAction(e -> newBoard(selectedFolder()));
        MenuItem nm = new MenuItem("New battlemap...");
        nm.setOnAction(e -> newBattleMap(selectedFolder()));
        MenuItem nc = new MenuItem("New card...");
        nc.setOnAction(e -> newCard(selectedFolder()));
        MenuItem nsp = new MenuItem("New spell...");
        nsp.setOnAction(e -> newSpell(selectedFolder()));
        MenuItem nmi = new MenuItem("New magic item...");
        nmi.setOnAction(e -> newMagicItem(selectedFolder()));
        MenuItem nq = new MenuItem("New quest...");
        nq.setOnAction(e -> newQuest(selectedFolder()));
        MenuItem nse = new MenuItem("New session...");
        nse.setOnAction(e -> newSession(selectedFolder()));
        MenuItem nw = new MenuItem("New world map...");
        nw.setOnAction(e -> newWorldMap(selectedFolder()));
        MenuItem ne = new MenuItem("New encounter...");
        ne.setOnAction(e -> newEncounter(selectedFolder()));
        MenuItem dup = new MenuItem("Duplicate");
        dup.setOnAction(e -> duplicateSelectedCard());
        MenuItem exportMd = new MenuItem("Export as Markdown...");
        exportMd.setOnAction(e -> {
            TreeNode n = selectedNode();
            if (ItemExport.isTextItem(n)) ItemExport.saveMarkdown(getScene().getWindow(), store, n);
        });
        MenuItem printItem = new MenuItem("Print...");
        printItem.setOnAction(e -> {
            TreeNode n = selectedNode();
            if (ItemExport.isTextItem(n)) ItemExport.print(getScene().getWindow(), store, n);
        });
        MenuItem exportImage = new MenuItem("Export image...");
        exportImage.setOnAction(e -> exportSelectedImage());
        MenuItem nf = new MenuItem("New folder");
        nf.setOnAction(e -> newFolder(selectedFolder()));
        MenuItem up = new MenuItem("Move up   (Alt+Up)");
        up.setOnAction(e -> moveSelected(-1));
        MenuItem down = new MenuItem("Move down   (Alt+Down)");
        down.setOnAction(e -> moveSelected(1));
        MenuItem rn = new MenuItem("Rename   (F2)");
        rn.setOnAction(e -> renameSelected());
        Menu colour = new Menu("Set colour");
        for (NodeColors.Swatch s : NodeColors.PALETTE) {
            MenuItem mi = new MenuItem(s.name());
            javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(6, javafx.scene.paint.Color.web(s.hex()));
            mi.setGraphic(dot);
            mi.setOnAction(e -> setSelectedColour(s.hex()));
            colour.getItems().add(mi);
        }
        MenuItem defaultColour = new MenuItem("Default colour");
        defaultColour.setOnAction(e -> setSelectedColour(null));
        colour.getItems().addAll(new SeparatorMenuItem(), defaultColour);
        MenuItem del = new MenuItem("Delete");
        del.setOnAction(e -> deleteSelected());
        MenuItem bin = new MenuItem("Deleted items...");
        bin.setOnAction(e -> openTrash());
        ContextMenu m = new ContextMenu(nb, nse, nq, new SeparatorMenuItem(), nw, nm, ne, new SeparatorMenuItem(), nc, nsp, nmi, new SeparatorMenuItem(), nf, new SeparatorMenuItem(), up, down, dup, new SeparatorMenuItem(), exportMd, printItem, exportImage, new SeparatorMenuItem(), rn, colour, del, bin);
        m.setOnShowing(e -> {
            TreeItem<TreeNode> it = tree.getSelectionModel().getSelectedItem();
            boolean isRoot = it == null || it.getValue() == store.project.root;
            dup.setDisable(it == null || !(it.getValue().isCard() || it.getValue().isEncounter() || it.getValue().isSpell() || it.getValue().isMagicItem() || it.getValue().isWorldMap() || it.getValue().isOrganisation()));
            TreeNode sel = it == null ? null : it.getValue();
            exportMd.setDisable(!ItemExport.isTextItem(sel));
            printItem.setDisable(!ItemExport.isTextItem(sel));
            exportImage.setDisable(sel == null || sel.folder || !(sel.isBattleMap() || sel.isWorldMap() || sel.isBoard()));
            boolean several = selectedIds().size() > 1;
            if (several) { // these work on one item at a time
                dup.setDisable(true);
                exportMd.setDisable(true);
                printItem.setDisable(true);
                exportImage.setDisable(true);
            }
            rn.setDisable(it == null || several);
            up.setDisable(isRoot || (!several && !canMove(it, -1)));
            down.setDisable(isRoot || (!several && !canMove(it, 1)));
            colour.setDisable(it == null);
            del.setDisable(isRoot);
        });
        return m;
    }

    /** Where on a tree row a drop lands: -1 above it (before), 1 below it (after), 0 on it (inside a folder). */
    private int dropZone(TreeCell<TreeNode> cell, double y) {
        TreeNode n = cell.getItem();
        if (n == null || n == store.project.root) return 0;
        double h = Math.max(1, cell.getHeight());
        if (n.folder) return y < h * 0.25 ? -1 : y > h * 0.75 ? 1 : 0;
        return y < h * 0.5 ? -1 : 1;
    }

    private static String dropStyle(int zone) {
        return switch (zone) {
            case -1 -> "-fx-border-color: #2563eb; -fx-border-width: 2 0 0 0;";
            case 1 -> "-fx-border-color: #2563eb; -fx-border-width: 0 0 2 0;";
            default -> "-fx-background-color: rgba(37,99,235,0.22);";
        };
    }

    /** A thin, faint line that breaks the "new" buttons into groups. */
    private static Separator treeDivider() {
        Separator sep = new Separator();
        sep.setOpacity(0.5);
        return sep;
    }

    /** Turns a "+ Something" button into a small icon button (the item's icon with a plus) with a tooltip. */
    private static void newButton(Button b, String kind, String tip) {
        IconButtons.decorate(b, "add", tip);
        b.setGraphic(NodeIcons.newIcon(kind));
    }

    private boolean canMove(TreeItem<TreeNode> it, int delta) {
        TreeItem<TreeNode> parent = it == null ? null : it.getParent();
        if (parent == null) return false;
        int j = parent.getChildren().indexOf(it) + delta;
        return j >= 0 && j < parent.getChildren().size();
    }

    /** Moves the selected item up or down among its neighbours in the same folder, and keeps it selected. */
    private void moveSelected(int delta) {
        List<String> ids = selectedIds();
        if (ids.isEmpty()) return;
        boolean any = false;
        // the ones at the edge go first, so a block of neighbours moves as a block
        List<String> order = new ArrayList<>(ids);
        if (delta > 0) java.util.Collections.reverse(order);
        for (String id : order) {
            TreeNode node = store.project.root.find(id);
            TreeNode parent = node == null ? null : store.project.root.parentOf(id);
            if (parent == null) continue;
            int next = parent.children.indexOf(node) + delta;
            if (next >= 0 && next < parent.children.size() && ids.contains(parent.children.get(next).id)) continue; // blocked by a selected neighbour
            any |= store.moveWithinParent(id, delta);
        }
        if (!any) return;
        refreshTree();
        selectInTree(ids);
    }

    /** The folder new items should go into: the selected folder, the selected board's parent, or the root. */
    private TreeNode selectedFolder() {
        TreeItem<TreeNode> it = tree.getSelectionModel().getSelectedItem();
        if (it == null) return store.project.root;
        TreeNode n = it.getValue();
        if (n.folder) return n;
        TreeNode p = store.project.root.parentOf(n.id);
        return p == null ? store.project.root : p;
    }

    private Optional<String> ask(String title, String header, String initial) {
        TextInputDialog d = new TextInputDialog(initial);
        d.setTitle(title);
        d.setHeaderText(header);
        d.initOwner(getScene().getWindow());
        return d.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
    }

    private void newBoard(TreeNode parent) {
        ask("New whiteboard", "Whiteboard name:", "New Whiteboard").ifPresent(name -> {
            Board b = store.createBoard(parent, name);
            refreshTree();
            openItem(b.id);
        });
    }

    private void newCard(TreeNode parent) {
        NewCardDialog.show(getScene().getWindow()).ifPresent(res -> {
            dnd.model.Card c = store.createCard(parent, dnd.rules.CardTemplates.create(res.templateId(), res.name()));
            refreshTree();
            openItem(c.id);
        });
    }

    private void newSpell(TreeNode parent) {
        ask("New spell", "Spell name:", "New Spell").ifPresent(name -> {
            dnd.model.Spell sp = new dnd.model.Spell();
            sp.name = name;
            sp.school = "Evocation";
            sp.castingTime = "1 action";
            sp.range = "Self";
            sp.duration = "Instantaneous";
            sp.components = "V, S";
            store.createSpell(parent, sp);
            refreshTree();
            openItem(sp.id);
        });
    }

    private void newMagicItem(TreeNode parent) {
        ask("New magic item", "Item name:", "New Magic Item").ifPresent(name -> {
            dnd.model.MagicItem mi = new dnd.model.MagicItem();
            mi.name = name;
            store.createMagicItem(parent, mi);
            refreshTree();
            openItem(mi.id);
        });
    }

    private void newQuest(TreeNode parent) {
        ask("New quest", "Quest name:", "New Quest").ifPresent(name -> {
            dnd.model.Quest qu = new dnd.model.Quest();
            qu.name = name;
            store.createQuest(parent, qu);
            refreshTree();
            openItem(qu.id);
        });
    }

    private void newOrganisation(TreeNode parent) {
        ask("New organisation", "Organisation name:", "New Organisation").ifPresent(name -> {
            dnd.model.Organisation og = new dnd.model.Organisation();
            og.name = name;
            store.createOrganisation(parent, og);
            refreshTree();
            openItem(og.id);
        });
    }

    private void newSession(TreeNode parent) {
        int number = dnd.model.Session.nextNumber(store.allSessions());
        ask("New session", "Session title:", "Session " + number).ifPresent(name -> {
            dnd.model.Session se = new dnd.model.Session();
            se.name = name;
            se.number = number;
            se.realDate = java.time.LocalDate.now().toString();
            dnd.model.Session prev = dnd.model.Session.previous(store.allSessions(), se);
            if (prev != null && prev.campaignDay > 0) se.campaignDay = prev.campaignDay;
            store.createSession(parent, se);
            refreshTree();
            openItem(se.id);
        });
    }

    private void newWorldMap(TreeNode parent) {
        NewWorldMapDialog.show(getScene().getWindow()).ifPresent(made -> {
            dnd.model.WorldMap map = made.map();
            store.createWorldMap(parent, map);
            if (made.generate() != null) {
                dnd.map.world.WorldGenerator.generate(map, made.generate());
                store.markWorldMapDirty(map, true);
            }
            refreshTree();
            openItem(map.id);
        });
    }

    private void newEncounter(TreeNode parent) {
        ask("New encounter", "Encounter name:", "New Encounter").ifPresent(name -> {
            dnd.model.Encounter e = store.createEncounter(parent, name);
            refreshTree();
            openItem(e.id);
        });
    }

    private void duplicateSelectedCard() {
        TreeItem<TreeNode> it = tree.getSelectionModel().getSelectedItem();
        if (it == null) return;
        String newId;
        if (it.getValue().isCard()) {
            dnd.model.Card copy = store.duplicateCard(it.getValue());
            newId = copy == null ? null : copy.id;
        } else if (it.getValue().isEncounter()) {
            dnd.model.Encounter copy = store.duplicateEncounter(it.getValue());
            newId = copy == null ? null : copy.id;
        } else if (it.getValue().isSpell()) {
            dnd.model.Spell copy = store.duplicateSpell(it.getValue());
            newId = copy == null ? null : copy.id;
        } else if (it.getValue().isMagicItem()) {
            dnd.model.MagicItem copy = store.duplicateMagicItem(it.getValue());
            newId = copy == null ? null : copy.id;
        } else if (it.getValue().isOrganisation()) {
            dnd.model.Organisation copy = store.duplicateOrganisation(it.getValue());
            newId = copy == null ? null : copy.id;
        } else if (it.getValue().isWorldMap()) {
            dnd.model.WorldMap copy = store.duplicateWorldMap(it.getValue());
            newId = copy == null ? null : copy.id;
        } else {
            return;
        }
        if (newId == null) return;
        refreshTree();
        openItem(newId);
    }

    /** The card editor renamed its card: keep the tree, the tab title and the trail in step. */
    private void renameFromEditor(String id, String newName) {
        TreeNode node = store.project.root.find(id);
        if (node == null) return;
        store.renameNode(node, newName);
        Tab t = open.get(id);
        if (t != null) t.setText(newName);
        refreshTree();
        updateTrail();
    }

    private void newBattleMap(TreeNode parent) {
        NewBattleMapDialog.show(getScene().getWindow()).ifPresent(spec -> {
            dnd.model.BattleMap m = store.createBattleMap(parent, spec.name(), spec.type(),
                    spec.cols(), spec.rows(), spec.cellSize());
            refreshTree();
            openItem(m.id);
        });
    }

    private void newFolder(TreeNode parent) {
        ask("New folder", "Folder name:", "New Folder").ifPresent(name -> {
            store.createFolder(parent, name);
            refreshTree();
        });
    }

    /** Gives the selected folder or item a colour (null = back to its default) and refreshes everything that shows it. */
    private void setSelectedColour(String hex) {
        List<String> ids = selectedIds();
        if (ids.isEmpty()) return;
        for (String id : ids) {
            TreeNode n = store.project.root.find(id);
            if (n != null) n.color = hex;
        }
        store.markProjectDirty();
        refreshTree();
        selectInTree(ids);
    }

    private void renameSelected() {
        TreeItem<TreeNode> it = tree.getSelectionModel().getSelectedItem();
        if (it == null) return;
        TreeNode n = it.getValue();
        ask("Rename", "New name:", n.name).ifPresent(name -> {
            store.renameNode(n, name);
            Tab t = open.get(n.id);
            if (t != null) t.setText(name);
            refreshTree();
            updateTrail();
        });
    }

    /** Opens the bin of deleted items; an item brought back shows up in the tree and is selected. */
    private void openTrash() {
        TrashDialog.show(getScene().getWindow(), store, id -> {
            refreshTree();
            selectInTree(id);
        });
    }

    private void deleteSelected() {
        List<TreeNode> doomed = new ArrayList<>();
        for (String id : store.movable(selectedIds())) { // one inside another selected folder goes with it
            TreeNode found = store.project.root.find(id);
            if (found != null) doomed.add(found);
        }
        if (doomed.isEmpty()) return;
        if (GeneralSettings.confirmDelete()) {
            Alert a;
            if (doomed.size() == 1) {
                TreeNode n = doomed.get(0);
                a = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete \"" + n.name + "\"" + (n.folder ? " and everything inside it" : "") + "? It goes to the bin of deleted items (the button above the tree), where you can bring it back for 30 days.",
                        ButtonType.OK, ButtonType.CANCEL);
                a.setHeaderText("Delete " + (n.folder ? "folder" : n.isBattleMap() ? "battlemap" : n.isCard() ? "card" : n.isEncounter() ? "encounter" : n.isSpell() ? "spell" : n.isMagicItem() ? "magic item" : n.isQuest() ? "quest" : n.isSession() ? "session" : n.isWorldMap() ? "world map" : n.isOrganisation() ? "organisation" : "whiteboard"));
            } else {
                boolean folders = doomed.stream().anyMatch(n -> n.folder);
                StringBuilder names = new StringBuilder();
                for (int k = 0; k < Math.min(8, doomed.size()); k++) names.append("\n  ").append(doomed.get(k).name);
                if (doomed.size() > 8) names.append("\n  ... and ").append(doomed.size() - 8).append(" more");
                a = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete these " + doomed.size() + " items" + (folders ? " (and everything inside the folders)" : "") + "?" + names + "\n\nThey go to the bin of deleted items (the button above the tree), where you can bring them back for 30 days.",
                        ButtonType.OK, ButtonType.CANCEL);
                a.setHeaderText("Delete " + doomed.size() + " items");
            }
            Optional<ButtonType> r = a.showAndWait();
            if (r.isEmpty() || r.get() != ButtonType.OK) return;
        }
        dnd.storage.ProjectBackups.beforeDelete(store); // a way back if it was a mistake

        for (TreeNode n : doomed) {
            List<TreeNode> boards = new ArrayList<>();
            n.collectItems(boards);
            for (TreeNode b : boards) {
                closeTab(b.id);
                PlayerWindow.close(b.id); // a deleted map must not stay up on the table screen
                WorldPlayerWindow.close(b.id);
                history.forget(b.id);
            }
            store.deleteNode(n);
        }
        refreshTree();
        updateTrail();
        updateNavButtons();
        BoardView v = currentView();
        if (v != null) v.redraw();
    }

    // ================================================================ BoardView.Host

    @Override
    public Optional<String> pickBoard(String title) {
        return pickItem(title, "Choose the item to link to", false);
    }

    @Override
    public Optional<String> pickMap(String title) {
        return pickItem(title, "Choose a battlemap", true);
    }

    /** Lets the user pick one of their character cards (for a quest's giver). */
    private Optional<String> pickCard(String title) {
        return pickItem(title, "Choose a character", TreeNode::isCard);
    }

    /** Lets the user pick a character card or another organisation to be a member of the organisation {@code self}. */
    private List<String> pickMember(String self) {
        return pickItems("Add members...", "Choose characters or other organisations (Ctrl-click or Shift-click to pick several)",
                n -> (n.isCard() || n.isOrganisation()) && !n.id.equals(self));
    }

    /** Lets the user pick one or more project items (Ctrl-click or Shift-click for several). Empty if they cancel. */
    private List<String> pickItems(String title, String header, java.util.function.Predicate<TreeNode> pickable) {
        Dialog<List<String>> d = new Dialog<>();
        d.setTitle(title);
        d.setHeaderText(header);
        d.initOwner(getScene().getWindow());

        TreeView<TreeNode> tv = new TreeView<>(buildItem(store.project.root, null));
        tv.setCellFactory(v -> createTreeCell(false));
        tv.setPrefSize(380, 440);
        tv.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        d.getDialogPane().setContent(tv);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Node ok = d.getDialogPane().lookupButton(ButtonType.OK);
        Runnable chosen = () -> ok.setDisable(pickedIds(tv, pickable).isEmpty());
        ok.setDisable(true);
        tv.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<TreeItem<TreeNode>>) c -> chosen.run());
        tv.setOnMouseClicked(e -> {
            TreeItem<TreeNode> s = tv.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY && s != null && pickable.test(s.getValue())
                    && tv.getSelectionModel().getSelectedItems().size() == 1) {
                d.setResult(List.of(s.getValue().id));
                d.close();
            }
        });
        d.setResultConverter(bt -> bt == ButtonType.OK ? pickedIds(tv, pickable) : null);
        return d.showAndWait().orElse(List.of());
    }

    /** The ids of the selected rows that may be picked (folders and the like are skipped). */
    private static List<String> pickedIds(TreeView<TreeNode> tv, java.util.function.Predicate<TreeNode> pickable) {
        List<String> ids = new ArrayList<>();
        for (TreeItem<TreeNode> it : tv.getSelectionModel().getSelectedItems()) {
            if (it != null && pickable.test(it.getValue()) && !ids.contains(it.getValue().id)) ids.add(it.getValue().id);
        }
        return ids;
    }

    private Optional<String> pickItem(String title, String header, boolean mapsOnly) {
        return pickItem(title, header, n -> mapsOnly ? n.isBattleMap() : !n.folder);
    }

    private Optional<String> pickItem(String title, String header, java.util.function.Predicate<TreeNode> pickable) {
        Dialog<String> d = new Dialog<>();
        d.setTitle(title);
        d.setHeaderText(header);
        d.initOwner(getScene().getWindow());

        TreeView<TreeNode> tv = new TreeView<>(buildItem(store.project.root, null));
        tv.setCellFactory(v -> createTreeCell(false));
        tv.setPrefSize(360, 420);
        d.getDialogPane().setContent(tv);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Node ok = d.getDialogPane().lookupButton(ButtonType.OK);
        ok.setDisable(true);
        tv.getSelectionModel().selectedItemProperty().addListener((o, a, n) ->
                ok.setDisable(n == null || !pickable.test(n.getValue())));
        tv.setOnMouseClicked(e -> {
            TreeItem<TreeNode> s = tv.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && s != null && pickable.test(s.getValue())) {
                d.setResult(s.getValue().id);
                d.close();
            }
        });
        d.setResultConverter(bt -> {
            TreeItem<TreeNode> s = tv.getSelectionModel().getSelectedItem();
            return bt == ButtonType.OK && s != null && pickable.test(s.getValue()) ? s.getValue().id : null;
        });
        return d.showAndWait();
    }
}
