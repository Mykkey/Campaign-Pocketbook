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

    public MapEditor(BattleMap map, ProjectStore store) {
        this.view = new MapView(map, store, ts);
        setCenter(view);
        setLeft(new AssetPanel(store, ts));
        setTop(buildToolbar(map, store));
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

        Button popOut = action("Pop out", "Open this map in its own window for the players. Drag it to your "
                + "playtable monitor and press F11 for fullscreen. It updates live as you edit",
                () -> PlayerWindow.open(getScene() == null ? null : getScene().getWindow(), map, store));
        popOut.setStyle("-fx-font-weight: bold;");
        bar.getChildren().addAll(
                popOut,
                action("Fit", "Zoom to show the whole map (Ctrl+0)", view::fitToWindow),
                action("Undo", "Ctrl+Z", view::undo),
                action("Redo", "Ctrl+Y", view::redo),
                action("Delete", "Delete the selection", view::deleteSelected));
        return bar;
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
}
