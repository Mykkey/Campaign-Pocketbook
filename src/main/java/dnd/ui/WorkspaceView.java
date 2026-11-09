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
    private final TextField search = new TextField();
    private final ContextMenu searchResults = new ContextMenu();
    private final PauseTransition saver = new PauseTransition(Duration.seconds(1));
    private boolean suppressHistory;
    private boolean restoring;

    public WorkspaceView(ProjectStore store, Runnable onExit) {
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
        HBox treeButtons = new HBox(6, newBoard, newFolder);
        tree.setCellFactory(v -> createTreeCell(true));
        tree.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                TreeItem<TreeNode> it = tree.getSelectionModel().getSelectedItem();
                if (it != null && !it.getValue().folder) openBoard(it.getValue().id);
            }
        });
        tree.setContextMenu(buildTreeMenu());
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
        backBtn.setTooltip(new Tooltip("Back (Alt+Left)"));
        fwdBtn.setTooltip(new Tooltip("Forward (Alt+Right)"));
        trail.setAlignment(Pos.CENTER_LEFT);
        backlinks.setTooltip(new Tooltip("Whiteboards that link to this one"));
        backlinks.setOnShowing(e -> rebuildBacklinks());
        search.setPromptText("Search project...");
        search.setPrefWidth(220);
        search.setOnAction(e -> runSearch());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox nav = new HBox(8, projects, backBtn, fwdBtn, trail, spacer, backlinks, search);
        nav.setAlignment(Pos.CENTER_LEFT);
        nav.setPadding(new Insets(6, 8, 0, 8));

        ToolbarPanel toolbar = new ToolbarPanel(ts, this::currentView);
        VBox top = new VBox(nav, toolbar);
        setTop(top);

        // ---- centre: tabs (with a hint behind them while no board is open)
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        Label hint = new Label("Double-click a whiteboard in the project tree to open it.");
        hint.setStyle("-fx-text-fill: #888;");
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
                BoardView v = currentView();
                if (v == null) return;
                KeyCode k = ev.getCode();
                if (ev.isShortcutDown() && !ev.isAltDown()) {
                    if (k == KeyCode.Z) {
                        if (ev.isShiftDown()) v.redo(); else v.undo();
                        ev.consume();
                    } else if (k == KeyCode.Y) {
                        v.redo();
                        ev.consume();
                    }
                    return;
                }
                if (ev.isControlDown() || ev.isAltDown() || ev.isMetaDown() || ev.isShiftDown()) return;
                int idx = k.isDigitKey() ? Integer.parseInt(k.getName()) : -1;
                if (idx < 0) return;
                int toolIndex = (idx + 9) % 10; // 1 -> 0 ... 9 -> 8, 0 -> 9
                Tool[] tools = Tool.values();
                if (toolIndex < tools.length) {
                    ts.tool.set(tools[toolIndex]);
                    ev.consume();
                }
            });
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.LEFT, KeyCombination.ALT_DOWN), this::goBack);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.ALT_DOWN), this::goForward);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN), this::closeCurrentTab);
            scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN), () -> search.requestFocus());
        });

        refreshTree();
        restoreTabs();
        updateNavButtons();
    }

    // ================================================================ lifecycle

    private void save() {
        try {
            store.flush();
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            Platform.runLater(() -> new Alert(Alert.AlertType.ERROR, "Could not save: " + ex.getMessage()).show());
        }
    }

    /** Saves everything immediately (called when the window closes or the project is left). */
    public void shutdown() {
        saver.stop();
        BoardView v = currentView();
        if (v != null) v.requestFocus(); // forces any in-place text edit to commit
        store.onDirty = () -> { };
        save();
    }

    private void exit() {
        shutdown();
        onExit.run();
    }

    private void restoreTabs() {
        restoring = true;
        try {
            for (String id : new ArrayList<>(store.project.openBoards)) {
                if (store.boardExists(id)) openBoard(id);
            }
        } finally {
            restoring = false;
        }
        String active = store.project.activeBoard;
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

    private BoardView currentView() {
        Tab t = tabs.getSelectionModel().getSelectedItem();
        return t == null ? null : (BoardView) t.getContent();
    }

    private BoardView viewFor(String boardId) {
        Tab t = open.get(boardId);
        return t == null ? null : (BoardView) t.getContent();
    }

    @Override
    public void openBoard(String id) {
        Tab t = open.get(id);
        if (t == null) {
            Board b = store.board(id);
            if (b == null) {
                new Alert(Alert.AlertType.WARNING, "That whiteboard could not be found.").show();
                return;
            }
            BoardView view = new BoardView(b, store, ts, this);
            t = new Tab(store.boardName(id), view);
            t.setUserData(id);
            t.setOnClosed(e -> {
                open.remove(id);
                persistTabs();
            });
            open.put(id, t);
            tabs.getTabs().add(t);
        }
        tabs.getSelectionModel().select(t);
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
        BoardView v = (BoardView) t.getContent();
        v.refreshCursor();
        Platform.runLater(v::requestFocus);
    }

    private void goBack() {
        step(history.canBack() ? history.back() : null);
    }

    private void goForward() {
        step(history.canForward() ? history.forward() : null);
    }

    private void step(String id) {
        if (id == null) return;
        if (!store.boardExists(id)) {
            history.forget(id);
            updateNavButtons();
            return;
        }
        suppressHistory = true;
        try {
            openBoard(id);
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
                h.setOnAction(e -> openBoard(id));
                h.setFocusTraversable(false);
                trail.getChildren().add(h);
            }
        }
    }

    private void rebuildBacklinks() {
        backlinks.getItems().clear();
        BoardView v = currentView();
        if (v == null) {
            backlinks.getItems().add(disabled("No board open"));
            return;
        }
        List<SearchService.Result> res = SearchService.backlinks(store, v.board().id);
        if (res.isEmpty()) backlinks.getItems().add(disabled("Nothing links here yet"));
        for (SearchService.Result r : res) {
            MenuItem m = new MenuItem(r.boardName + "  -  " + r.snippet);
            m.setOnAction(e -> showResult(r));
            backlinks.getItems().add(m);
        }
    }

    private static MenuItem disabled(String text) {
        MenuItem m = new MenuItem(text);
        m.setDisable(true);
        return m;
    }

    private void runSearch() {
        List<SearchService.Result> res = SearchService.search(store, search.getText(), 40);
        searchResults.getItems().clear();
        if (res.isEmpty()) searchResults.getItems().add(disabled("No results"));
        for (SearchService.Result r : res) {
            MenuItem m = new MenuItem(r.boardName + "  -  " + r.snippet);
            m.setOnAction(e -> showResult(r));
            searchResults.getItems().add(m);
        }
        searchResults.show(search, Side.BOTTOM, 0, 0);
    }

    private void showResult(SearchService.Result r) {
        openBoard(r.boardId);
        if (r.objectId != null) {
            BoardView v = viewFor(r.boardId);
            if (v != null) Platform.runLater(() -> v.focusObject(r.objectId));
        }
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
            @Override
            protected void updateItem(TreeNode n, boolean empty) {
                super.updateItem(n, empty);
                setText(empty || n == null ? null : (n.folder ? "[+] " : "") + n.name);
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
        MenuItem nf = new MenuItem("New folder");
        nf.setOnAction(e -> newFolder(selectedFolder()));
        MenuItem rn = new MenuItem("Rename");
        rn.setOnAction(e -> renameSelected());
        MenuItem del = new MenuItem("Delete");
        del.setOnAction(e -> deleteSelected());
        ContextMenu m = new ContextMenu(nb, nf, new SeparatorMenuItem(), rn, del);
        m.setOnShowing(e -> {
            TreeItem<TreeNode> it = tree.getSelectionModel().getSelectedItem();
            boolean isRoot = it == null || it.getValue() == store.project.root;
            rn.setDisable(it == null);
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
            openBoard(b.id);
        });
    }

    private void newFolder(TreeNode parent) {
        ask("New folder", "Folder name:", "New Folder").ifPresent(name -> {
            store.createFolder(parent, name);
            refreshTree();
        });
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
        a.setHeaderText("Delete " + (n.folder ? "folder" : "whiteboard"));
        Optional<ButtonType> r = a.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) return;

        List<TreeNode> boards = new ArrayList<>();
        n.collectBoards(boards);
        for (TreeNode b : boards) {
            closeTab(b.id);
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
        Dialog<String> d = new Dialog<>();
        d.setTitle(title);
        d.setHeaderText("Choose the whiteboard to link to");
        d.initOwner(getScene().getWindow());

        TreeView<TreeNode> tv = new TreeView<>(buildItem(store.project.root, null));
        tv.setCellFactory(v -> createTreeCell(false));
        tv.setPrefSize(360, 420);
        d.getDialogPane().setContent(tv);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Node ok = d.getDialogPane().lookupButton(ButtonType.OK);
        ok.setDisable(true);
        tv.getSelectionModel().selectedItemProperty().addListener((o, a, n) ->
                ok.setDisable(n == null || n.getValue().folder));
        tv.setOnMouseClicked(e -> {
            TreeItem<TreeNode> s = tv.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && s != null && !s.getValue().folder) {
                d.setResult(s.getValue().id);
                d.close();
            }
        });
        d.setResultConverter(bt -> {
            TreeItem<TreeNode> s = tv.getSelectionModel().getSelectedItem();
            return bt == ButtonType.OK && s != null && !s.getValue().folder ? s.getValue().id : null;
        });
        return d.showAndWait();
    }
}
