package dnd.ui;

import dnd.map.AssetLibrary;
import dnd.storage.ProjectStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Side panel listing the vector pieces (houses, walls, paths, camp items...) that can be placed on a map. */
public class AssetPanel extends VBox {
    private static final String ALL = "All";

    private final ProjectStore store;
    private final MapToolState ts;
    private final AssetLibrary lib;
    private final TextField search = new TextField();
    private final ComboBox<String> category = new ComboBox<>();
    private final TilePane tiles = new TilePane(6, 6);
    private final Label empty = new Label();

    public AssetPanel(ProjectStore store, MapToolState ts) {
        this.store = store;
        this.ts = ts;
        this.lib = store.assets();

        setSpacing(6);
        setPadding(new Insets(8));
        setPrefWidth(248);
        setMinWidth(200);

        Label title = new Label("Pieces");
        title.setStyle("-fx-font-weight: bold;");
        search.setPromptText("Search pieces...");
        category.setMaxWidth(Double.MAX_VALUE);
        rebuildCategories();
        category.setValue(ALL);

        tiles.setPrefColumns(3);
        tiles.setPadding(new Insets(2));
        ScrollPane scroll = new ScrollPane(tiles);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        empty.setWrapText(true);
        empty.setMinHeight(Region.USE_PREF_SIZE);
        empty.setStyle("-fx-text-fill: #888;");

        Button importBtn = new Button("Import SVG / PNG...");
        importBtn.setMaxWidth(Double.MAX_VALUE);
        importBtn.setTooltip(new Tooltip("Add your own pieces. They are copied into this project's assets folder"));
        importBtn.setOnAction(e -> importFiles());

        Label hint = new Label("Click a piece, then click the map to place it. Esc to stop.");
        hint.setWrapText(true);
        hint.setMinHeight(Region.USE_PREF_SIZE);
        hint.setStyle("-fx-text-fill: #888; -fx-font-size: 11px;");

        getChildren().addAll(title, search, category, scroll, empty, importBtn, hint);

        search.textProperty().addListener((o, a, n) -> rebuild());
        category.valueProperty().addListener((o, a, n) -> rebuild());
        ts.armedAsset.addListener((o, a, n) -> rebuild());
        rebuild();
    }

    private void rebuildCategories() {
        String cur = category.getValue();
        List<String> cats = new ArrayList<>();
        cats.add(ALL);
        cats.addAll(lib.categories());
        category.getItems().setAll(cats);
        if (cur != null && cats.contains(cur)) category.setValue(cur);
    }

    private void rebuild() {
        tiles.getChildren().clear();
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        String cat = category.getValue() == null ? ALL : category.getValue();
        int shown = 0;
        for (AssetLibrary.Asset a : lib.assets()) {
            if (!ALL.equals(cat) && !a.category.equals(cat)) continue;
            if (!q.isEmpty() && !a.name.toLowerCase(Locale.ROOT).contains(q)
                    && !a.category.toLowerCase(Locale.ROOT).contains(q)) continue;
            tiles.getChildren().add(tile(a));
            shown++;
        }
        if (shown == 0) {
            empty.setText(AssetLibrary.IMPORTED.equals(cat)
                    ? "No imported pieces yet. Use Import to add your own SVG or PNG files."
                    : "No pieces match.");
            empty.setVisible(true);
            empty.setManaged(true);
        } else {
            empty.setVisible(false);
            empty.setManaged(false);
        }
    }

    private Node tile(AssetLibrary.Asset a) {
        Image img = lib.image(a.id, 64);
        ImageView iv = new ImageView(img);
        iv.setFitWidth(64);
        iv.setFitHeight(56);
        iv.setPreserveRatio(true);
        Label name = new Label(a.name);
        name.setStyle("-fx-font-size: 10px;");
        name.setMaxWidth(70);
        VBox box = new VBox(2, iv, name);
        box.setAlignment(Pos.CENTER);
        box.setPrefSize(72, 78);
        box.setPadding(new Insets(3));
        boolean armed = a.id.equals(ts.armedAsset.get());
        box.setStyle("-fx-background-color: " + (armed ? "#dbeafe" : "#f4f1ea") + ";"
                + "-fx-border-color: " + (armed ? "#2563eb" : "#d6d0c0") + "; -fx-border-radius: 4; -fx-background-radius: 4;");
        Tooltip.install(box, new Tooltip(a.name + "  (" + trim(a.cellsW) + " x " + trim(a.cellsH) + " cells)"));
        box.setOnMouseClicked(e -> {
            ts.armedAsset.set(a.id);
            ts.tool.set(MapTool.ASSET);
        });
        box.setOnDragDetected(e -> {
            Dragboard db = box.startDragAndDrop(TransferMode.COPY);
            ClipboardContent c = new ClipboardContent();
            c.putString("asset:" + a.id);
            db.setContent(c);
            if (img != null) db.setDragView(img, 32, 32);
            e.consume();
        });
        return box;
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private void importFiles() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Import pieces");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("SVG or images", "*.svg", "*.png", "*.jpg", "*.jpeg"));
        List<File> files = fc.showOpenMultipleDialog(getScene().getWindow());
        if (files == null || files.isEmpty()) return;
        String lastId = null;
        for (File f : files) {
            try {
                lastId = "imported/" + store.importAsset(f.toPath());
            } catch (IOException ex) {
                new Alert(Alert.AlertType.ERROR, "Could not import " + f.getName() + ": " + ex.getMessage()).show();
            }
        }
        lib.reload();
        rebuildCategories();
        category.setValue(AssetLibrary.IMPORTED);
        if (lastId != null && lib.get(lastId) != null) ts.armedAsset.set(lastId);
        rebuild();
    }
}
