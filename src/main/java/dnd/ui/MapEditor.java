package dnd.ui;

import dnd.model.BattleMap;
import dnd.model.Terrain;
import dnd.storage.ProjectStore;
import javafx.collections.FXCollections;
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
    private final Runnable treeChanged;

    public MapEditor(BattleMap map, ProjectStore store) {
        this(map, store, id -> { });
    }

    /** @param openItem opens another project item in a tab (used to jump from a battle to a creature's card) */
    public MapEditor(BattleMap map, ProjectStore store, java.util.function.Consumer<String> openItem) {
        this(map, store, openItem, () -> { });
    }

    /** @param treeChanged refreshes the project tree (the battle can add cards to the project) */
    public MapEditor(BattleMap map, ProjectStore store, java.util.function.Consumer<String> openItem, Runnable treeChanged) {
        this.treeChanged = treeChanged;
        this.map = map;
        this.store = store;
        this.openItem = openItem;
        this.view = new MapView(map, store, ts);
        this.view.openCard = openItem;
        this.view.openItem = openItem;
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
            String key = String.valueOf((t.ordinal() + 1) % 10); // keys 1-9, then 0 for the tenth tool
            ToggleButton b = IconButtons.decorate(new ToggleButton(), t.name().toLowerCase(), dnd.keys.Shortcuts.tip(t.label + ": " + t.tip, "tool.battle." + (t.ordinal() + 1)));
            b.setToggleGroup(group);
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
        terrain.setTooltip(IconButtons.tip("Ground to paint with the Terrain tool"));
        terrain.setCellFactory(lv -> terrainCell());
        terrain.setButtonCell(terrainCell());
        terrain.valueProperty().addListener((o, a, n) -> {
            ts.terrain.set(n instanceof Terrain t ? t : null);
            ts.tool.set(MapTool.TERRAIN);
        });
        Spinner<Integer> brush = new Spinner<>(1, 6, 1);
        brush.setPrefWidth(70);
        brush.setTooltip(IconButtons.tip("Terrain brush size (cells)"));
        brush.valueProperty().addListener((o, a, n) -> {
            if (n != null) ts.brush.set(n);
        });
        bar.getChildren().add(toolGroup(MapTool.TERRAIN, new Label("Terrain"), terrain, new Label("Brush"), brush));

        ColorPicker tokenColor = new ColorPicker(ts.tokenColor.get());
        tokenColor.setTooltip(IconButtons.tip("Token colour (also recolours selected tokens)"));
        tokenColor.setStyle("-fx-color-label-visible: false;");
        tokenColor.setOnAction(e -> {
            ts.tokenColor.set(tokenColor.getValue());
            view.applyTokenColor();
        });
        ToggleButton grid = IconButtons.toggle("grid", "Grid: show the grid lines");
        grid.setSelected(map.showGrid);
        grid.setOnAction(e -> {
            map.showGrid = grid.isSelected();
            store.markMapDirty(map);
            view.redraw();
        });
        ToggleButton snap = IconButtons.switchToggle("snap", "Snap: snap pieces to the grid", ts.snap);
        bar.getChildren().add(toolGroup(MapTool.TOKEN, new Label("Token colour"), tokenColor));
        bar.getChildren().addAll(grid, snap, new Separator(Orientation.VERTICAL));

        // ---- fog of war
        ToggleButton fog = IconButtons.switchToggle("fog", "Fog: hide the map from the players until you reveal it", ts.fogEnabled);
        ComboBox<String> fogMode = new ComboBox<>();
        fogMode.getItems().setAll("Reveal brush", "Hide brush", "Reveal rectangle", "Hide rectangle");
        fogMode.setValue("Reveal brush");
        fogMode.setFocusTraversable(false);
        fogMode.setTooltip(IconButtons.tip("What the Fog tool (key 7) does when you click or drag on the map"));
        fogMode.setOnAction(e -> {
            int i = fogMode.getItems().indexOf(fogMode.getValue());
            ts.fogReveal.set(i == 0 || i == 2);
            ts.fogRect.set(i >= 2);
            ts.tool.set(MapTool.FOG);
        });
        Button revealAll = iconAction("eye", "Reveal all: remove all fog", () -> view.revealAllFog());
        Button hideAll = iconAction("eye-off", "Hide all: cover the whole map in fog (tokens that reveal fog keep their surroundings clear)",
                () -> view.hideAllFog());
        bar.getChildren().add(toolGroup(MapTool.FOG, fog, fogMode, revealAll, hideAll));

        // ---- ruler: the players can be shown the measurement too
        ToggleButton rulerShare = IconButtons.switchToggle("monitor", "Show players: also draw the ruler in the players' window", ts.rulerShared);
        bar.getChildren().add(toolGroup(MapTool.RULER, new Label("Ruler"), rulerShare,
                iconAction("clear", "Clear: take the ruler off the map (Esc)", view::clearMeasures)));

        bar.getChildren().add(toolGroup(MapTool.PIN, new Label("Click the map to add a note (only you see pins). Double-click a pin to edit it.")));

        // ---- area templates (cone, circle, line, cube)
        ComboBox<dnd.model.AreaTemplate.Shape> shape = new ComboBox<>(FXCollections.observableArrayList(dnd.model.AreaTemplate.Shape.values()));
        shape.setValue(ts.templateShape.get());
        shape.setFocusTraversable(false);
        shape.setTooltip(IconButtons.tip("Shape of the area the Area tool (key 8) lays over the map"));
        shape.valueProperty().addListener((o, a, n) -> {
            if (n == null) return;
            ts.templateShape.set(n);
            ts.tool.set(MapTool.TEMPLATE);
        });
        Spinner<Integer> feet = new Spinner<>(5, 300, ts.templateFeet.get(), 5);
        feet.setPrefWidth(78);
        feet.setTooltip(IconButtons.tip("Size in feet: radius of a circle, length of a cone or line, side of a cube"));
        feet.valueProperty().addListener((o, a, n) -> {
            if (n != null) ts.templateFeet.set(n);
        });
        ToggleButton share = IconButtons.switchToggle("monitor", "Show players: also draw the area in the players' window", ts.templateShared);
        Button clearArea = iconAction("clear", "Clear area: take the area off the map (Esc)", view::clearTemplate);
        bar.getChildren().add(toolGroup(MapTool.TEMPLATE, new Label("Area"), shape, feet, new Label("ft"), share, clearArea));

        // ---- lighting
        ComboBox<dnd.map.LightPresets.Preset> lightKind = new ComboBox<>(FXCollections.observableArrayList(dnd.map.LightPresets.ALL));
        lightKind.setValue(ts.lightPreset.get());
        lightKind.setFocusTraversable(false);
        lightKind.setTooltip(IconButtons.tip("Kind of light the Light tool (key 0) places: bright light, then dim light, in feet"));
        lightKind.valueProperty().addListener((o, a, n) -> {
            if (n == null) return;
            ts.lightPreset.set(n);
            ts.tool.set(MapTool.LIGHT);
        });
        ToggleButton dark = IconButtons.switchToggle("moon", "Dark map: only places lit by a light, or within a creature's darkvision, are clear. "
                + "Tokens with darkvision or a carried light clear the fog around them", ts.darkMap);
        bar.getChildren().add(toolGroup(MapTool.LIGHT, new Label("Light"), lightKind, dark));

        Button popOut = iconAction("popout", "Pop out: open this map in its own window for the players. Drag it to your "
                + "playtable monitor and press F11 for fullscreen. It updates live as you edit",
                () -> PlayerWindow.open(getScene() == null ? null : getScene().getWindow(), map, store));
        battleBtn = IconButtons.toggle("battle", "Battle: set up a fight, then run it with the initiative strip and creature cards");
        battleBtn.setSelected(map.battle != null && map.battle.active);
        battleBtn.setOnAction(e -> setBattleVisible(battleBtn.isSelected()));
        bar.getChildren().addAll(
                battleBtn,
                popOut,
                iconAction("fit", dnd.keys.Shortcuts.tip("Fit: zoom to show the whole map", "item.fit"), view::fitToWindow),
                iconAction("undo", dnd.keys.Shortcuts.tip("Undo", "item.undo"), view::undo),
                iconAction("redo", dnd.keys.Shortcuts.tip("Redo", "item.redo"), view::redo),
                iconAction("delete", "Delete the selection", view::deleteSelected));
        return bar;
    }

    /** Controls that only matter for one tool: shown while that tool is selected, hidden (and taking no space) otherwise. */
    private javafx.scene.layout.HBox toolGroup(MapTool tool, javafx.scene.Node... nodes) {
        javafx.scene.layout.HBox g = new javafx.scene.layout.HBox(6, nodes);
        g.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        g.getChildren().add(new Separator(Orientation.VERTICAL));
        javafx.beans.binding.BooleanBinding show = ts.tool.isEqualTo(tool);
        g.visibleProperty().bind(show);
        g.managedProperty().bind(show);
        return g;
    }

    /** Opens or closes the battle screen: a banner on top, the creature cards on the right, the turn order below. */
    private void setBattleVisible(boolean on) {
        battleOpen = on;
        if (battleBtn != null) battleBtn.setSelected(on);
        if (on) {
            if (battleScreen == null) {
                battleScreen = new BattleScreen(map, store, view, ts, new BattleScreen.Host() {
                    @Override public void exit() { setBattleVisible(false); }

                    @Override public void showMapTools(boolean show) {
                        toolbar.setVisible(show);
                        toolbar.setManaged(show);
                    }

                    @Override public void popOutPlayers() {
                        PlayerWindow.open(getScene() == null ? null : getScene().getWindow(), map, store);
                    }

                    @Override public void openItem(String itemId) { openItem.accept(itemId); }

                    @Override public void treeChanged() { treeChanged.run(); }
                });
            } else {
                battleScreen.refresh();
            }
            setLeft(null);
            setTop(null);
            battleTop.getChildren().setAll(battleScreen.banner(), battleScreen.quickTools(), toolbar);
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

    private Button iconAction(String icon, String tip, Runnable run) {
        Button b = IconButtons.decorate(new Button(), icon, tip);
        b.setOnAction(e -> {
            run.run();
            view.requestFocus();
        });
        return b;
    }

    private Button action(String text, String tip, Runnable run) {
        Button b = new Button(text);
        b.setFocusTraversable(false);
        b.setTooltip(IconButtons.tip(tip));
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

    @Override
    public boolean perform(String id) {
        if (id.equals("battle.next") || id.equals("battle.previous")) {
            if (!battleOpen || battleScreen == null || !battleScreen.battle().active) return false;
            if (id.equals("battle.next")) battleScreen.next();
            else battleScreen.previous();
            return true;
        }
        return view.perform(id);
    }

    @Override public dnd.keys.Shortcuts.Context keyContext() { return dnd.keys.Shortcuts.Context.BATTLEMAP; }

    @Override public boolean hasCanvasFocus() { return view.hasCanvasFocus(); }

    @Override public boolean escape() { return view.escape(); }

    @Override public boolean deleteSelection() { return view.deleteSelection(); }
}
