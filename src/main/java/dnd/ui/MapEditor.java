package dnd.ui;

import dnd.model.BattleMap;
import dnd.model.Terrain;
import dnd.storage.ProjectStore;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** One open battlemap tab: toolbar on top, piece library on the left, the map in the middle. */
public class MapEditor extends BorderPane implements ItemView {
    private static final String ERASE = "Erase";

    private final MapView view;
    private final MapToolState ts = new MapToolState();
    private BattleScreen battleScreen;
    private AssetPanel assetPanel;
    private FlowPane toolbar;
    private ToggleButton battleBtn;
    private final javafx.scene.layout.VBox battleTop = new javafx.scene.layout.VBox();
    private boolean battleOpen;
    private final BattleMap map;
    private final ProjectStore store;
    private final java.util.function.Consumer<String> openItem;

    public MapEditor(BattleMap map, ProjectStore store) {
        this(map, store, id -> { });
    }

    /** @param openItem opens another project item in a tab (used to jump from a battle to a creature's card) */
    public MapEditor(BattleMap map, ProjectStore store, java.util.function.Consumer<String> openItem) {
        this.map = map;
        this.store = store;
        this.openItem = openItem;
        this.view = new MapView(map, store, ts);
        this.view.openCard = openItem;
        // render the pieces this map uses in the background so the first draw doesn't stall
        java.util.List<String> used = new java.util.ArrayList<>();
        for (dnd.model.MapObject o : map.objects) used.add(o.assetId);
        store.assets().warmUp(used, 256);
        setCenter(view);
        assetPanel = new AssetPanel(store, ts);
        setLeft(assetPanel);
        toolbar = buildToolbar(map, store);
        setTop(toolbar);
        // clicking a token during a battle shows that creature's card
        view.onTokenClicked = id -> {
            if (battleOpen && battleScreen != null) battleScreen.inspectToken(id);
        };
        // N = next turn, P = previous turn while the battle screen is up (not while typing in a box)
        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
            if (!battleOpen || battleScreen == null) return;
            if (getScene() != null && getScene().getFocusOwner() instanceof TextInputControl) return;
            if (battleScreen.handleKey(ev)) ev.consume();
        });
        if (map.battle != null && map.battle.active) setBattleVisible(true); // a fight was in progress when it was saved
        // first open: show the whole map
        javafx.application.Platform.runLater(() -> {
            if (map.revision == 0 && map.zoom == 1 && map.offsetX == 40 && map.offsetY == 40) view.fitToWindow();
        });
    }

    private FlowPane buildToolbar(BattleMap map, ProjectStore store) {
        FlowPane bar = new FlowPane(6, 4);
        bar.setPadding(new Insets(6));

        ToggleGroup group = new ToggleGroup();
        Map<MapTool, ToggleButton> buttons = new EnumMap<>(MapTool.class);
        for (MapTool t : MapTool.values()) {
            ToggleButton b = new ToggleButton((t.ordinal() + 1) + " " + t.label);
            b.setToggleGroup(group);
            b.setFocusTraversable(false);
            b.setTooltip(new Tooltip(t.tip + " [" + (t.ordinal() + 1) + "]"));
            b.setOnAction(e -> {
                ts.tool.set(t);
                b.setSelected(true);
            });
            buttons.put(t, b);
            bar.getChildren().add(b);
        }
        buttons.get(ts.tool.get()).setSelected(true);
        ts.tool.addListener((o, a, n) -> buttons.get(n).setSelected(true));
        bar.getChildren().add(new Separator(Orientation.VERTICAL));

        // terrain
        ComboBox<Object> terrain = new ComboBox<>();
        List<Object> items = new ArrayList<>(List.of(Terrain.values()));
        items.add(ERASE);
        terrain.getItems().setAll(items);
        terrain.setValue(Terrain.GRASS);
        terrain.setFocusTraversable(false);
        terrain.setTooltip(new Tooltip("Ground to paint with the Terrain tool"));
        terrain.setCellFactory(lv -> terrainCell());
        terrain.setButtonCell(terrainCell());
        terrain.valueProperty().addListener((o, a, n) -> {
            ts.terrain.set(n instanceof Terrain t ? t : null);
            ts.tool.set(MapTool.TERRAIN);
        });
        Spinner<Integer> brush = new Spinner<>(1, 6, 1);
        brush.setPrefWidth(70);
        brush.setTooltip(new Tooltip("Terrain brush size (cells)"));
        brush.valueProperty().addListener((o, a, n) -> {
            if (n != null) ts.brush.set(n);
        });
        bar.getChildren().addAll(new Label("Terrain"), terrain, new Label("Brush"), brush);
        bar.getChildren().add(new Separator(Orientation.VERTICAL));

        ColorPicker tokenColor = new ColorPicker(ts.tokenColor.get());
        tokenColor.setTooltip(new Tooltip("Token colour (also recolours selected tokens)"));
        tokenColor.setOnAction(e -> {
            ts.tokenColor.set(tokenColor.getValue());
            view.applyTokenColor();
        });
        CheckBox grid = new CheckBox("Grid");
        grid.setSelected(map.showGrid);
        grid.setFocusTraversable(false);
        grid.setOnAction(e -> {
            map.showGrid = grid.isSelected();
            store.markMapDirty(map);
            view.redraw();
        });
        CheckBox snap = new CheckBox("Snap");
        snap.setFocusTraversable(false);
        snap.setTooltip(new Tooltip("Snap pieces to the grid"));
        snap.selectedProperty().bindBidirectional(ts.snap);
        bar.getChildren().addAll(new Label("Token"), tokenColor, grid, snap, new Separator(Orientation.VERTICAL));

        // ---- fog of war
        CheckBox fog = new CheckBox("Fog");
        fog.setFocusTraversable(false);
        fog.setTooltip(new Tooltip("Hide the map from the players until you reveal it"));
        fog.selectedProperty().bindBidirectional(ts.fogEnabled);
        ComboBox<String> fogMode = new ComboBox<>();
        fogMode.getItems().setAll("Reveal brush", "Hide brush", "Reveal rectangle", "Hide rectangle");
        fogMode.setValue("Reveal brush");
        fogMode.setFocusTraversable(false);
        fogMode.setTooltip(new Tooltip("What the Fog tool (key 7) does when you click or drag on the map"));
        fogMode.setOnAction(e -> {
            int i = fogMode.getItems().indexOf(fogMode.getValue());
            ts.fogReveal.set(i == 0 || i == 2);
            ts.fogRect.set(i >= 2);
            ts.tool.set(MapTool.FOG);
        });
        Button revealAll = action("Reveal all", "Remove all fog", () -> view.revealAllFog());
        Button hideAll = action("Hide all", "Cover the whole map in fog (tokens that reveal fog keep their surroundings clear)",
                () -> view.hideAllFog());
        bar.getChildren().addAll(fog, fogMode, revealAll, hideAll, new Separator(Orientation.VERTICAL));

        Button popOut = action("Pop out", "Open this map in its own window for the players. Drag it to your "
                + "playtable monitor and press F11 for fullscreen. It updates live as you edit",
                () -> PlayerWindow.open(getScene() == null ? null : getScene().getWindow(), map, store));
        popOut.setStyle("-fx-font-weight: bold;");
        battleBtn = new ToggleButton("Battle");
        battleBtn.setFocusTraversable(false);
        battleBtn.setStyle("-fx-font-weight: bold;");
        battleBtn.setTooltip(new Tooltip("Battle mode: set up a fight, then run it with the initiative strip and creature cards"));
        battleBtn.setSelected(map.battle != null && map.battle.active);
        battleBtn.setOnAction(e -> setBattleVisible(battleBtn.isSelected()));
        bar.getChildren().addAll(
                battleBtn,
                popOut,
                action("Fit", "Zoom to show the whole map (Ctrl+0)", view::fitToWindow),
                action("Undo", "Ctrl+Z", view::undo),
                action("Redo", "Ctrl+Y", view::redo),
                action("Delete", "Delete the selection", view::deleteSelected));
        return bar;
    }

    /** Opens or closes the battle screen: a banner on top, the creature cards on the right, the turn order below. */
    private void setBattleVisible(boolean on) {
        battleOpen = on;
        if (battleBtn != null) battleBtn.setSelected(on);
        if (on) {
            if (battleScreen == null) {
                battleScreen = new BattleScreen(map, store, view, new BattleScreen.Host() {
                    @Override public void exit() { setBattleVisible(false); }

                    @Override public void showMapTools(boolean show) {
                        toolbar.setVisible(show);
                        toolbar.setManaged(show);
                    }

                    @Override public void popOutPlayers() {
                        PlayerWindow.open(getScene() == null ? null : getScene().getWindow(), map, store);
                    }

                    @Override public void openItem(String itemId) { openItem.accept(itemId); }
                });
            } else {
                battleScreen.refresh();
            }
            setLeft(null);
            setTop(null);
            battleTop.getChildren().setAll(battleScreen.banner(), toolbar);
            toolbar.setVisible(false); // hidden until "Map tools" is pressed
            toolbar.setManaged(false);
            setTop(battleTop);
            setRight(battleScreen.rightPane());
            setBottom(battleScreen.strip());
        } else {
            setTop(null);
            battleTop.getChildren().clear();
            toolbar.setVisible(true);
            toolbar.setManaged(true);
            setTop(toolbar);
            setLeft(assetPanel);
            setRight(null);
            setBottom(null);
        }
    }
    private static ListCell<Object> terrainCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else if (item instanceof Terrain t) {
                    setText(t.label);
                    Rectangle swatch = new Rectangle(20, 20);
                    swatch.setFill(new ImagePattern(dnd.map.TerrainTextures.get(t), 0, 0, 80, 80, false));
                    swatch.setStroke(Color.web("#00000055"));
                    setGraphic(swatch);
                } else {
                    setText("Erase");
                    Rectangle r = new Rectangle(14, 14, Color.WHITE);
                    r.setStroke(Color.GRAY);
                    setGraphic(r);
                }
            }
        };
    }

    private Button action(String text, String tip, Runnable run) {
        Button b = new Button(text);
        b.setFocusTraversable(false);
        b.setTooltip(new Tooltip(tip));
        b.setOnAction(e -> {
            run.run();
            view.requestFocus();
        });
        return b;
    }

    // ================================================================ ItemView

    @Override public String itemId() { return view.itemId(); }

    @Override public void undo() { view.undo(); }

    @Override public void redo() { view.redo(); }

    @Override public void activated() { view.activated(); }

    @Override public boolean selectTool(int index) { return view.selectTool(index); }

    @Override public boolean shortcut(javafx.scene.input.KeyEvent e) { return view.shortcut(e); }

    @Override public boolean escape() { return view.escape(); }

    @Override public boolean deleteSelection() { return view.deleteSelection(); }
}
