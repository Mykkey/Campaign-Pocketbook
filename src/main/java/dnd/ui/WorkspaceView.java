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
    private final MenuButton backlinks = new MenuButton("Backlinks");
    private final MenuButton recentMenu = new MenuButton("Recent");
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

        store.onDirty = saver::playFromStart;
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
        Button newSession = new Button("+ Session");
        newSession.setTooltip(IconButtons.tip("Write up a game session: what happened, a recap for next time, and what came up"));
        newSession.setOnAction(e -> newSession(selectedFolder()));
        Button newEncounter = new Button("+ Encounter");
        newEncounter.setTooltip(IconButtons.tip("Plan a fight: pick creatures and see how hard it is for your party"));
        newEncounter.setOnAction(e -> newEncounter(selectedFolder()));
        FlowPane treeButtons = new FlowPane(6, 6, newBoard, newMap, newCard, newSpell, newItem, newQuest, newSession, newEncounter, newFolder);
        tree.setCellFactory(v -> createTreeCell(true));
        tree.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                TreeItem<TreeNode> it = tree.getSelectionModel().getSelectedItem();
                if (it != null && !it.getValue().folder) openItem(it.getValue().id);
            }
        });
        tree.setContextMenu(buildTreeMenu());
        tree.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.F2) {
                renameSelected();
                e.consume();
            } else if (e.getCode() == KeyCode.DELETE) {
                deleteSelected();
                e.consume();
            }
        });
        VBox left = new VBox(6, treeTitle, treeButtons, tree);
        left.setPadding(new Insets(8));
        VBox.setVgrow(tree, Priority.ALWAYS);
        left.setMinWidth(180);
        left.setPrefWidth(240);

        // ---- top bar
        Button projects = new Button("Projects");
        projects.setOnAction(e -> exit());
        backBtn.setOnAction(e -> goBack());
        fwdBtn.setOnAction(e -> goForward());
        backBtn.setTooltip(IconButtons.tip("Back (Alt+Left)"));
        fwdBtn.setTooltip(IconButtons.tip("Forward (Alt+Right)"));
        trail.setAlignment(Pos.CENTER_LEFT);
        backlinks.setTooltip(IconButtons.tip("Whiteboards that link to this one"));
        backlinks.setOnShowing(e -> rebuildBacklinks());
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
        names.setTooltip(IconButtons.tip("Random character name generator (Ctrl+G)"));
        names.setOnAction(e -> openNameGenerator());
        Button npc = new Button("NPC...");
        npc.setTooltip(IconButtons.tip("Random NPC generator (Ctrl+Shift+N)"));
        npc.setOnAction(e -> openNpcGenerator());
        Button loot = new Button("Loot...");
        loot.setTooltip(IconButtons.tip("Random treasure generator (Ctrl+L)"));
        loot.setOnAction(e -> openLootGenerator());
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
        recentMenu.setTooltip(IconButtons.tip("The last items you opened"));
        recentMenu.setOnShowing(e -> rebuildRecent());
        IconButtons.decorate(projects, "home", "Projects: back to the list of campaigns");
        IconButtons.decorate(backBtn, "back");
        IconButtons.decorate(fwdBtn, "forward");
        IconButtons.decorate(names, "names");
        IconButtons.decorate(npc, "person");
        IconButtons.decorate(loot, "loot");
        IconButtons.decorate(relations, "relations");
        IconButtons.decorate(quests, "quest");
        IconButtons.decorate(timeline, "calendar");
        IconButtons.decorate(library, "book");
        IconButtons.decorate(recentMenu, "clock");
        IconButtons.decorate(backlinks, "link");
        HBox nav = new HBox(4, projects, backBtn, fwdBtn, trail, spacer, names, npc, loot, relations, quests, timeline, library, Theme.toggleButton(), recentMenu, backlinks, search);
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
        split.setDividerPositions(0.17);
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

        // keyboard shortcuts at scene level
        sceneProperty().addListener((o, old, scene) -> {
            if (scene == null) return;
            // Ctrl+Z / Ctrl+Y and number keys 1..9,0 = tools, unless the user is typing in a text field.
            scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
                if (scene.getFocusOwner() instanceof TextInputControl) return;
                ItemView v = currentItem();
                if (v == null) return;
                KeyCode k = ev.getCode();
                if (ev.isShortcutDown() && !ev.isAltDown()) {
                    if (k == KeyCode.Z) {
                        if (ev.isShiftDown()) v.redo(); else v.undo();
                        ev.consume();
                    } else if (k == KeyCode.Y) {
                        v.redo();
                        ev.consume();
                    } else if (v.shortcut(ev)) {
                        ev.consume();
                    }
                    return;
                }
                if (ev.isControlDown() || ev.isAltDown() || ev.isMetaDown() || ev.isShiftDown()) return;
                if (k == KeyCode.ESCAPE) {
                    if (v.escape()) ev.consume();
                    return;
                }
                if (k == KeyCode.DELETE || k == KeyCode.BACK_SPACE) {
                    if (scene.getFocusOwner() == tree) return; // the tree deletes its own selected item
                    if (v.deleteSelection()) ev.consume();
                    return;
                }
                int idx = k.isDigitKey() ? Integer.parseInt(k.getName()) : -1;
                if (idx < 0) return;
                int toolIndex = (idx + 9) % 10; // 1 -> 0 ... 9 -> 8, 0 -> 9
                if (v.selectTool(toolIndex)) ev.consume();
            });
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.LEFT, KeyCombination.ALT_DOWN), this::goBack);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.ALT_DOWN), this::goForward);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN), this::closeCurrentTab);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN), () -> search.requestFocus());
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN), this::openNameGenerator);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN), this::openLootGenerator);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), this::openNpcGenerator);
        });

        refreshTree();
        restoreTabs();
        updateNavButtons();
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

    /** Saves everything immediately (called when the window closes or the project is left). */
    public void shutdown() {
        saver.stop();
        PlayerWindow.closeAll();
        BoardView v = currentView();
        if (v != null) v.requestFocus(); // forces any in-place text edit to commit
        store.onDirty = () -> { };
        save();
        dnd.storage.ProjectBackups.backup(store, false); // a copy of the campaign as it is now (skipped if nothing changed)
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
                view = new QuestEditor(qu, store, newName -> renameFromEditor(id, newName), this::openItem, () -> pickBoard("Add to the quest..."), () -> pickCard("Who gave this quest?"));
            } else if (store.isSession(id)) {
                dnd.model.Session se = store.session(id);
                if (se == null) {
                    couldNotOpen(id, "That session could not be found.");
                    return;
                }
                view = new SessionEditor(se, store, newName -> renameFromEditor(id, newName), this::openItem, () -> pickBoard("Add to the session..."));
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
        if (it != null) tree.getSelectionModel().select(it);
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
                Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
                ClipboardContent c = new ClipboardContent();
                c.putString(n.id);
                db.setContent(c);
                e.consume();
            });
            cell.setOnDragOver(e -> {
                if (e.getDragboard().hasString()) e.acceptTransferModes(TransferMode.MOVE);
                e.consume();
            });
            cell.setOnDragDropped(e -> {
                boolean done = false;
                if (e.getDragboard().hasString()) {
                    TreeNode target = cell.getItem() == null ? store.project.root : cell.getItem();
                    if (!target.folder) {
                        TreeNode p = store.project.root.parentOf(target.id);
                        target = p == null ? store.project.root : p;
                    }
                    if (store.moveNode(e.getDragboard().getString(), target)) {
                        refreshTree();
                        done = true;
                    }
                }
                e.setDropCompleted(done);
                e.consume();
            });
        }
        return cell;
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
        MenuItem ne = new MenuItem("New encounter...");
        ne.setOnAction(e -> newEncounter(selectedFolder()));
        MenuItem dup = new MenuItem("Duplicate");
        dup.setOnAction(e -> duplicateSelectedCard());
        MenuItem nf = new MenuItem("New folder");
        nf.setOnAction(e -> newFolder(selectedFolder()));
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
        ContextMenu m = new ContextMenu(nb, nm, nc, nsp, nmi, nq, nse, ne, nf, new SeparatorMenuItem(), dup, rn, colour, del);
        m.setOnShowing(e -> {
            TreeItem<TreeNode> it = tree.getSelectionModel().getSelectedItem();
            boolean isRoot = it == null || it.getValue() == store.project.root;
            dup.setDisable(it == null || !(it.getValue().isCard() || it.getValue().isEncounter() || it.getValue().isSpell() || it.getValue().isMagicItem()));
            rn.setDisable(it == null);
            colour.setDisable(it == null);
            del.setDisable(isRoot);
        });
        return m;
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
        TreeItem<TreeNode> it = tree.getSelectionModel().getSelectedItem();
        if (it == null) return;
        it.getValue().color = hex;
        store.markProjectDirty();
        refreshTree();
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

    private void deleteSelected() {
        TreeItem<TreeNode> it = tree.getSelectionModel().getSelectedItem();
        if (it == null || it.getValue() == store.project.root) return;
        TreeNode n = it.getValue();
        Alert a = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete \"" + n.name + "\"" + (n.folder ? " and everything inside it" : "") + "? This cannot be undone.",
                ButtonType.OK, ButtonType.CANCEL);
        a.setHeaderText("Delete " + (n.folder ? "folder" : n.isBattleMap() ? "battlemap" : n.isCard() ? "card" : n.isEncounter() ? "encounter" : n.isSpell() ? "spell" : n.isMagicItem() ? "magic item" : n.isQuest() ? "quest" : n.isSession() ? "session" : "whiteboard"));
        Optional<ButtonType> r = a.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) return;
        dnd.storage.ProjectBackups.beforeDelete(store); // a way back if it was a mistake

        List<TreeNode> boards = new ArrayList<>();
        n.collectItems(boards);
        for (TreeNode b : boards) {
            closeTab(b.id);
            PlayerWindow.close(b.id); // a deleted map must not stay up on the table screen
            history.forget(b.id);
        }
        store.deleteNode(n);
        refreshTree();
        updateTrail();
        updateNavButtons();
        BoardView v = currentView();
        if (v != null) v.redraw();
    }

    // ================================================================ BoardView.Host

    @Override
    public Optional<String> pickBoard(String title) {
        return pickItem(title, "Choose the whiteboard or battlemap to link to", false);
    }

    @Override
    public Optional<String> pickMap(String title) {
        return pickItem(title, "Choose a battlemap", true);
    }

    /** Lets the user pick one of their character cards (for a quest's giver). */
    private Optional<String> pickCard(String title) {
        return pickItem(title, "Choose a character", TreeNode::isCard);
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
