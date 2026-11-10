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

    private final WorldMap map;
    private final WorldToolState ts = new WorldToolState();
    private final WorldMapView view;
    private final Label statusLabel = new Label();

    public WorldMapEditor(WorldMap map, ProjectStore store) {
        this.map = map;
        this.view = new WorldMapView(map, store, ts, statusLabel::setText);

        FlowPane bar = new FlowPane(4, 4);
        bar.setPadding(new Insets(6));
        bar.setAlignment(Pos.CENTER_LEFT);

        // ---- tools
        ToggleGroup tools = new ToggleGroup();
        Map<WorldTool, ToggleButton> toolButtons = new EnumMap<>(WorldTool.class);
        for (WorldTool t : WorldTool.values()) {
            ToggleButton b = IconButtons.decorate(new ToggleButton(), t.icon, t.label + ": " + t.tip + " [" + (t.ordinal() + 1) % 10 + "]");
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
                ts.tool.set(WorldTool.BASE);
                b.setSelected(true);
            });
            terrainButtons.put(t, b);
            terrainNodes.add(b);
        }
        ToggleButton eraseTerrain = IconButtons.decorate(new ToggleButton(), "eraser", "Eraser: take away what was painted (back to bare parchment)");
        eraseTerrain.setToggleGroup(terrainGroup);
        eraseTerrain.setOnAction(e -> {
            ts.terrain.set(null);
            ts.tool.set(WorldTool.BASE);
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
            ts.tool.set(WorldTool.BIOME);
        });
        bar.getChildren().add(tool(new HBox(4, biomes), WorldTool.BIOME));

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
        brushBox.visibleProperty().bind(ts.tool.isEqualTo(WorldTool.BASE).or(ts.tool.isEqualTo(WorldTool.BIOME)));
        brushBox.managedProperty().bind(brushBox.visibleProperty());
        bar.getChildren().addAll(brushBox, new Separator(Orientation.VERTICAL));

        // ---- the art style of the whole map
        ComboBox<WorldStyle> style = new ComboBox<>(FXCollections.observableArrayList(WorldStyle.values()));
        style.setValue(map.style);
        style.setFocusTraversable(false);
        style.setTooltip(IconButtons.tip("Art style: how the land and sea are drawn. You can change it at any time"));
        style.valueProperty().addListener((o, a, n) -> {
            if (n == null) return;
            map.style = n;
            view.styleChanged();
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

    @Override
    public boolean selectTool(int index) {
        WorldTool[] all = WorldTool.values();
        if (index < 0 || index >= all.length) return false;
        ts.tool.set(all[index]);
        return true;
    }
}
