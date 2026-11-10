package dnd.ui;

import dnd.model.WorldBiome;
import dnd.model.WorldMap;
import dnd.model.WorldStyle;
import dnd.model.WorldTerrain;
import dnd.storage.ProjectStore;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** A tab for editing one world map: a toolbar of painting tools above the map itself. */
public class WorldMapEditor extends BorderPane implements ItemView {
    private static final String ERASE_BIOME = "Erase biome";
    private static final String SAME_STYLE = "Same style as map";

    private final WorldMap map;
    private final WorldToolState ts = new WorldToolState();
    private final WorldMapView view;
    private final Label statusLabel = new Label();
    private final WorldAssetPanel panel;

    public WorldMapEditor(WorldMap map, ProjectStore store) {
        this(map, store, id -> { });
    }

    /** @param openItem opens another project item in a tab (used by links on the map) */
    public WorldMapEditor(WorldMap map, ProjectStore store, java.util.function.Consumer<String> openItem) {
        this.map = map;
        this.view = new WorldMapView(map, store, ts, statusLabel::setText);
        this.view.openItem = openItem;
        this.panel = new WorldAssetPanel(store, map, ts);

        FlowPane bar = new FlowPane(4, 4);
        bar.setPadding(new Insets(6));
        bar.setAlignment(Pos.CENTER_LEFT);

        // ---- tools
        ToggleGroup tools = new ToggleGroup();
        Map<WorldTool, ToggleButton> toolButtons = new EnumMap<>(WorldTool.class);
        for (WorldTool t : WorldTool.values()) {
            ToggleButton b = IconButtons.decorate(new ToggleButton(), t.icon, t.label + ": " + t.tip + (t.ordinal() < 10 ? " [" + (t.ordinal() + 1) % 10 + "]" : ""));
            b.setToggleGroup(tools);
            b.setOnAction(e -> {
                ts.tool.set(t);
                b.setSelected(true);
            });
            toolButtons.put(t, b);
            bar.getChildren().add(b);
        }
        toolButtons.get(ts.tool.get()).setSelected(true);
        ts.tool.addListener((o, a, n) -> toolButtons.get(n).setSelected(true));
        bar.getChildren().add(new Separator(Orientation.VERTICAL));

        // ---- what the land-and-sea tool paints
        ToggleGroup terrainGroup = new ToggleGroup();
        Map<WorldTerrain, ToggleButton> terrainButtons = new EnumMap<>(WorldTerrain.class);
        List<javafx.scene.Node> terrainNodes = new ArrayList<>();
        String[] icons = {"waves", "drop", "terrain", "flame", "snowflake"};
        for (WorldTerrain t : WorldTerrain.values()) {
            ToggleButton b = IconButtons.decorate(new ToggleButton(), icons[t.ordinal()], t.label + ": " + t.tip);
            b.setToggleGroup(terrainGroup);
            b.setOnAction(e -> {
                ts.terrain.set(t);
                ts.fillBiome.set(false);
                if (ts.tool.get() != WorldTool.FILL) ts.tool.set(WorldTool.BASE);
                b.setSelected(true);
            });
            terrainButtons.put(t, b);
            terrainNodes.add(b);
        }
        ToggleButton eraseTerrain = IconButtons.decorate(new ToggleButton(), "eraser", "Eraser: take away what was painted (back to bare parchment)");
        eraseTerrain.setToggleGroup(terrainGroup);
        eraseTerrain.setOnAction(e -> {
            ts.terrain.set(null);
            ts.fillBiome.set(false);
            if (ts.tool.get() != WorldTool.FILL) ts.tool.set(WorldTool.BASE);
            eraseTerrain.setSelected(true);
        });
        terrainNodes.add(eraseTerrain);
        Runnable syncTerrain = () -> {
            WorldTerrain t = ts.terrain.get();
            (t == null ? eraseTerrain : terrainButtons.get(t)).setSelected(true);
        };
        syncTerrain.run();
        ts.terrain.addListener((o, a, n) -> syncTerrain.run());
        HBox terrainGroupBox = tool(new HBox(4, terrainNodes.toArray(new javafx.scene.Node[0])), WorldTool.BASE);
        terrainGroupBox.visibleProperty().unbind();
        terrainGroupBox.visibleProperty().bind(ts.tool.isEqualTo(WorldTool.BASE).or(ts.tool.isEqualTo(WorldTool.FILL)));
        bar.getChildren().add(terrainGroupBox);

        // ---- what the biome tool paints
        ComboBox<Object> biomes = new ComboBox<>();
        List<Object> biomeItems = new ArrayList<>(List.of(WorldBiome.values()));
        biomeItems.add(ERASE_BIOME);
        biomes.getItems().setAll(biomeItems);
        biomes.setValue(ts.biome.get());
        biomes.setFocusTraversable(false);
        biomes.setTooltip(IconButtons.tip("Biome: what covers the land. Biomes only go on land and ice"));
        biomes.setCellFactory(lv -> biomeCell());
        biomes.setButtonCell(biomeCell());
        biomes.valueProperty().addListener((o, a, n) -> {
            ts.biome.set(n instanceof WorldBiome b ? b : null);
            if (ts.tool.get() == WorldTool.FILL) ts.fillBiome.set(true);
            else ts.tool.set(WorldTool.BIOME);
        });
        HBox biomeBox = tool(new HBox(4, biomes), WorldTool.BIOME);
        biomeBox.visibleProperty().unbind();
        biomeBox.visibleProperty().bind(ts.tool.isEqualTo(WorldTool.BIOME).or(ts.tool.isEqualTo(WorldTool.FILL)));
        bar.getChildren().add(biomeBox);

        // ---- brush size
        Spinner<Integer> brush = new Spinner<>(1, 200, ts.brush.get());
        brush.setPrefWidth(76);
        brush.setEditable(true);
        brush.setTooltip(IconButtons.tip("Brush width in map cells"));
        brush.valueProperty().addListener((o, a, n) -> {
            if (n != null) ts.brush.set(n);
        });
        ts.brush.addListener((o, a, n) -> {
            if (brush.getValue() == null || brush.getValue().intValue() != n.intValue()) brush.getValueFactory().setValue(n.intValue());
        });
        HBox brushBox = new HBox(4, new Label("Brush"), brush);
        brushBox.setAlignment(Pos.CENTER_LEFT);
        brushBox.visibleProperty().bind(ts.tool.isEqualTo(WorldTool.BASE).or(ts.tool.isEqualTo(WorldTool.BIOME)).or(ts.tool.isEqualTo(WorldTool.SCATTER)));
        brushBox.managedProperty().bind(brushBox.visibleProperty());
        bar.getChildren().add(brushBox);

        // ---- symbols: how big, how thick, and in which art style
        Spinner<Integer> stampSize = new Spinner<>(0, 200, ts.stampSize.get());
        stampSize.setPrefWidth(76);
        stampSize.setEditable(true);
        stampSize.setTooltip(IconButtons.tip("Symbol width in map cells. 0 uses each symbol's own size"));
        stampSize.valueProperty().addListener((o, a, n) -> {
            if (n != null) ts.stampSize.set(n);
        });
        ts.stampSize.addListener((o, a, n) -> {
            if (stampSize.getValue() == null || stampSize.getValue().intValue() != n.intValue()) stampSize.getValueFactory().setValue(n.intValue());
        });
        Spinner<Integer> density = new Spinner<>(1, 10, ts.density.get());
        density.setPrefWidth(66);
        density.setTooltip(IconButtons.tip("Scatter density: 1 is sparse, 10 is a dense forest"));
        density.valueProperty().addListener((o, a, n) -> {
            if (n != null) ts.density.set(n);
        });
        ComboBox<Object> stampStyle = new ComboBox<>();
        stampStyle.getItems().add(SAME_STYLE);
        stampStyle.getItems().addAll(WorldStyle.values());
        stampStyle.setValue(SAME_STYLE);
        stampStyle.setFocusTraversable(false);
        stampStyle.setTooltip(IconButtons.tip("Symbol style: new symbols, and the selected ones, can use a different art style from the map"));
        stampStyle.valueProperty().addListener((o, a, n) -> {
            WorldStyle chosen = n instanceof WorldStyle w ? w : null;
            ts.stampStyle.set(chosen);
            if (view.hasSelection()) view.styleSelection(chosen);
        });
        HBox symbolBox = new HBox(4, new Label("Size"), stampSize, stampStyle);
        symbolBox.setAlignment(Pos.CENTER_LEFT);
        symbolBox.visibleProperty().bind(ts.tool.isEqualTo(WorldTool.STAMP).or(ts.tool.isEqualTo(WorldTool.SCATTER)).or(ts.tool.isEqualTo(WorldTool.SELECT)));
        symbolBox.managedProperty().bind(symbolBox.visibleProperty());
        HBox densityBox = new HBox(4, new Label("Density"), density);
        densityBox.setAlignment(Pos.CENTER_LEFT);
        densityBox.visibleProperty().bind(ts.tool.isEqualTo(WorldTool.SCATTER));
        densityBox.managedProperty().bind(densityBox.visibleProperty());
        // ---- lines: rivers, roads and so on
        ToggleGroup pathGroup = new ToggleGroup();
        Map<dnd.model.WorldPath.Type, ToggleButton> pathButtons = new EnumMap<>(dnd.model.WorldPath.Type.class);
        List<javafx.scene.Node> pathNodes = new ArrayList<>();
        String[] pathIcons = {"river", "road", "trail", "route", "border", "railway"};
        String[] pathTips = {"A river, thinnest at its source and wider towards its mouth", "A road", "A trail: a dashed path",
                "A sea route: a dashed line over the water", "A border between lands", "A railway"};
        for (dnd.model.WorldPath.Type t : dnd.model.WorldPath.Type.values()) {
            ToggleButton b = IconButtons.decorate(new ToggleButton(), pathIcons[t.ordinal()], t.label + ": " + pathTips[t.ordinal()]);
            b.setToggleGroup(pathGroup);
            b.setOnAction(e -> {
                ts.pathType.set(t);
                if (ts.tool.get() != WorldTool.SELECT) ts.tool.set(WorldTool.PATH);
                b.setSelected(true);
            });
            pathButtons.put(t, b);
            pathNodes.add(b);
        }
        pathButtons.get(ts.pathType.get()).setSelected(true);
        Spinner<Double> pathWidth = new Spinner<>(0.0, 12.0, ts.pathWidth.get(), 0.2);
        pathWidth.setPrefWidth(74);
        pathWidth.setEditable(true);
        pathWidth.setTooltip(IconButtons.tip("Line width in map cells. 0 uses each kind's own width"));
        pathWidth.valueProperty().addListener((o, a, n) -> {
            if (n == null) return;
            ts.pathWidth.set(n);
            if (ts.tool.get() == WorldTool.SELECT && view.pathSelected()) view.updateSelectedPath(ts.pathType.get(), n);
        });
        ts.pathType.addListener((o, a, n) -> {
            pathButtons.get(n).setSelected(true);
            if (ts.tool.get() == WorldTool.SELECT && view.pathSelected()) view.updateSelectedPath(n, ts.pathWidth.get());
        });
        pathNodes.add(pathWidth);
        HBox pathBox = new HBox(4, pathNodes.toArray(new javafx.scene.Node[0]));
        pathBox.setAlignment(Pos.CENTER_LEFT);
        pathBox.visibleProperty().bind(ts.tool.isEqualTo(WorldTool.PATH).or(ts.tool.isEqualTo(WorldTool.SELECT)));
        pathBox.managedProperty().bind(pathBox.visibleProperty());
        // ---- labels: what kind, and how much the text curves
        ComboBox<dnd.map.world.WorldLabels.Kind> labelKind = new ComboBox<>(FXCollections.observableArrayList(dnd.map.world.WorldLabels.Kind.values()));
        labelKind.setValue(ts.labelKind.get());
        labelKind.setFocusTraversable(false);
        labelKind.setTooltip(IconButtons.tip("Label kind: sets the size, spacing and look of the text"));
        Spinner<Double> bend = new Spinner<>(-0.6, 0.6, ts.labelBend.get(), 0.05);
        bend.setPrefWidth(76);
        bend.setEditable(true);
        bend.setTooltip(IconButtons.tip("Curve: 0 is straight; positive curves the text down in the middle, negative curves it up"));
        Runnable labelChanged = () -> {
            if (ts.tool.get() == WorldTool.SELECT && view.labelSelected()) view.updateSelectedLabels(ts.labelKind.get(), ts.labelBend.get());
        };
        labelKind.valueProperty().addListener((o, a, n) -> {
            if (n == null) return;
            ts.labelKind.set(n);
            labelChanged.run();
        });
        bend.valueProperty().addListener((o, a, n) -> {
            if (n == null) return;
            ts.labelBend.set(Math.round(n * 100) / 100.0);
            labelChanged.run();
        });
        HBox labelBox = new HBox(4, labelKind, new Label("Curve"), bend);
        labelBox.setAlignment(Pos.CENTER_LEFT);
        labelBox.visibleProperty().bind(ts.tool.isEqualTo(WorldTool.LABEL).or(ts.tool.isEqualTo(WorldTool.SELECT)));
        labelBox.managedProperty().bind(labelBox.visibleProperty());
        bar.getChildren().addAll(symbolBox, densityBox, pathBox, labelBox, new Separator(Orientation.VERTICAL));

        // ---- grid, scale bar, legend and travel speeds
        ComboBox<dnd.model.WorldGridKind> gridKind = new ComboBox<>(FXCollections.observableArrayList(dnd.model.WorldGridKind.values()));
        gridKind.setValue(map.gridKind);
        gridKind.setFocusTraversable(false);
        gridKind.setTooltip(IconButtons.tip("Grid: lay a square or hex grid over the map for measuring"));
        Spinner<Double> gridMiles = new Spinner<>(0.5, 1000.0, map.gridMiles, 1.0);
        gridMiles.setPrefWidth(84);
        gridMiles.setEditable(true);
        gridMiles.setTooltip(IconButtons.tip("Grid size in miles: the width of a square, or of a hex across its flat sides"));
        CheckBox gridNames = new CheckBox("Names");
        gridNames.setSelected(map.gridCoords);
        gridNames.setTooltip(IconButtons.tip("Write the name of each grid cell (A1, B1...) in it"));
        Runnable gridChanged = () -> {
            gridMiles.setDisable(map.gridKind == dnd.model.WorldGridKind.NONE);
            gridNames.setDisable(map.gridKind == dnd.model.WorldGridKind.NONE);
            view.overlaysChanged();
        };
        gridKind.valueProperty().addListener((o, a, n) -> {
            if (n == null) return;
            map.gridKind = n;
            gridChanged.run();
        });
        gridMiles.valueProperty().addListener((o, a, n) -> {
            if (n == null || n <= 0) return;
            map.gridMiles = n;
            gridChanged.run();
        });
        gridNames.setOnAction(e -> {
            map.gridCoords = gridNames.isSelected();
            gridChanged.run();
        });
        gridMiles.setDisable(map.gridKind == dnd.model.WorldGridKind.NONE);
        gridNames.setDisable(map.gridKind == dnd.model.WorldGridKind.NONE);
        ToggleButton scaleBtn = IconButtons.decorate(new ToggleButton(), "scalebar", "Scale bar: show how far a distance on the map is");
        scaleBtn.setSelected(map.showScale);
        scaleBtn.setOnAction(e -> {
            map.showScale = scaleBtn.isSelected();
            view.overlaysChanged();
        });
        ToggleButton legendBtn = IconButtons.decorate(new ToggleButton(), "legend", "Legend: a key to the symbols and lines used on the map. Drag it with the Select tool");
        legendBtn.setSelected(map.showLegend);
        legendBtn.setOnAction(e -> {
            map.showLegend = legendBtn.isSelected();
            view.overlaysChanged();
        });
        Button pacesBtn = iconButton("clock", "Travel speeds: how many miles a day each way of travelling covers (used by the Ruler)", this::editPaces);
        bar.getChildren().addAll(gridKind, gridMiles, gridNames, scaleBtn, legendBtn, pacesBtn, new Separator(Orientation.VERTICAL));
        Button linkBtn = iconButton("link", "Link and note: give the selected symbol, line or region a note and a link to a card, whiteboard, map, quest... (Ctrl+L, or right-click it)", view::editSelectedLinkNote);
        linkBtn.visibleProperty().bind(ts.tool.isEqualTo(WorldTool.SELECT));
        linkBtn.managedProperty().bind(linkBtn.visibleProperty());
        javafx.scene.control.ToggleButton previewBtn = IconButtons.switchToggle("previews", "Link previews: show the note and a preview of the linked item when you hover over something on the map", ts.linkPreviews);
        bar.getChildren().addAll(linkBtn, previewBtn, new Separator(Orientation.VERTICAL));

        // ---- the art style of the whole map
        ComboBox<WorldStyle> style = new ComboBox<>(FXCollections.observableArrayList(WorldStyle.values()));
        style.setValue(map.style);
        style.setFocusTraversable(false);
        style.setTooltip(IconButtons.tip("Art style: how the land and sea are drawn. You can change it at any time"));
        style.valueProperty().addListener((o, a, n) -> {
            if (n == null) return;
            map.style = n;
            view.styleChanged();
            panel.restyle();
            biomes.setButtonCell(biomeCell()); // swatches follow the style
            biomes.setCellFactory(lv -> biomeCell());
        });
        bar.getChildren().add(style);

        bar.getChildren().addAll(
                iconButton("fit", "Fit: zoom to show the whole map", this::fit),
                iconButton("undo", "Undo (Ctrl+Z)", view::undo),
                iconButton("redo", "Redo (Ctrl+Y)", view::redo));

        statusLabel.setStyle("-fx-text-fill: -t-text3; -fx-padding: 2 8 2 8;");
        setTop(bar);
        setLeft(panel);
        setCenter(view);
        setBottom(statusLabel);
    }

    private Button iconButton(String icon, String tip, Runnable run) {
        Button b = IconButtons.decorate(new Button(), icon, tip);
        b.setOnAction(e -> {
            run.run();
            view.requestFocus();
        });
        return b;
    }

    /** A small dialog to change the miles per day of each way of travelling. */
    private void editPaces() {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Travel speeds");
        d.setHeaderText("Miles covered in a day (8 hours of travel)");
        if (getScene() != null) d.initOwner(getScene().getWindow());
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        Map<String, Spinner<Double>> spinners = new java.util.LinkedHashMap<>();
        int row = 0;
        for (var e : map.paces.entrySet()) {
            Spinner<Double> sp = new Spinner<>(1.0, 1000.0, e.getValue(), 1.0);
            sp.setEditable(true);
            sp.setPrefWidth(90);
            spinners.put(e.getKey(), sp);
            grid.addRow(row++, new Label(e.getKey()), sp);
        }
        d.getDialogPane().setContent(grid);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button reset = new Button("Defaults");
        reset.setOnAction(e -> dnd.map.world.WorldTravel.defaultPaces().forEach((k, v) -> {
            Spinner<Double> sp = spinners.get(k);
            if (sp != null) sp.getValueFactory().setValue(v);
        }));
        grid.addRow(row, reset);
        if (d.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            spinners.forEach((k, sp) -> {
                if (sp.getValue() != null) map.paces.put(k, sp.getValue());
            });
            view.overlaysChanged();
        }
    }

    private void fit() {
        view.fitToWindow();
    }

    /** Controls that only matter for one tool: shown while that tool is selected, hidden (and taking no space) otherwise. */
    private HBox tool(HBox box, WorldTool tool) {
        box.setAlignment(Pos.CENTER_LEFT);
        box.visibleProperty().bind(ts.tool.isEqualTo(tool));
        box.managedProperty().bind(box.visibleProperty());
        return box;
    }

    private ListCell<Object> biomeCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else if (item instanceof WorldBiome b) {
                    setText(b.label);
                    Rectangle r = new Rectangle(18, 18, WorldMapView.swatch(map.style, b));
                    r.setStroke(javafx.scene.paint.Color.web("#00000055"));
                    setGraphic(r);
                } else {
                    setText(String.valueOf(item));
                    setGraphic(null);
                }
            }
        };
    }

    // ================================================================ ItemView

    @Override public String itemId() { return map.id; }

    @Override public void undo() { view.undo(); }

    @Override public void redo() { view.redo(); }

    @Override
    public void activated() {
        view.redraw();
        view.requestFocus();
    }

    double rulerMilesForTest() {
        return view.rulerMiles();
    }

    /** Selects an object (a search hit) and shows it. */
    public void focusObject(String id) {
        view.focusObject(id);
    }

    boolean pathSelectedForTest() {
        return view.pathSelected();
    }

    /** True while something on the map is selected. */
    boolean hasSelectionForTest() {
        return view.hasSelection();
    }

    @Override
    public boolean deleteSelection() {
        return view.deleteSelection();
    }

    @Override
    public boolean escape() {
        return view.escape();
    }

    @Override
    public boolean shortcut(javafx.scene.input.KeyEvent e) {
        if (e.isShortcutDown() && e.getCode() == javafx.scene.input.KeyCode.L) {
            if (view.linkable()) view.editSelectedLinkNote();
            return true;
        }
        if (e.isShortcutDown() && e.getCode() == javafx.scene.input.KeyCode.D) {
            view.duplicateSelection();
            return true;
        }
        return false;
    }

    @Override
    public boolean selectTool(int index) {
        WorldTool[] all = WorldTool.values();
        if (index < 0 || index >= all.length) return false;
        ts.tool.set(all[index]);
        return true;
    }
}
