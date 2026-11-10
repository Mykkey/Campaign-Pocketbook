package dnd.ui;

import dnd.map.world.WorldAssets;
import dnd.model.WorldMap;
import dnd.model.WorldStyle;
import dnd.storage.ProjectStore;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The side panel of a world map: the library of symbols, by group, with a search box. Picking one arms it for the stamp tool. */
final class WorldAssetPanel extends VBox {
    private static final String ALL = "All symbols";

    private final ProjectStore store;
    private final WorldMap map;
    private final WorldToolState ts;
    private final ListView<WorldAssets.Feature> list = new ListView<>();
    private final TextField search = new TextField();
    private final ComboBox<String> category = new ComboBox<>();
    private boolean syncing;

    WorldAssetPanel(ProjectStore store, WorldMap map, WorldToolState ts) {
        this.store = store;
        this.map = map;
        this.ts = ts;
        setSpacing(6);
        setPadding(new Insets(8));
        setPrefWidth(230);
        setMinWidth(180);

        Label title = new Label("Symbols");
        title.setStyle("-fx-font-weight: bold;");
        search.setPromptText("Search symbols...");
        List<String> cats = new ArrayList<>();
        cats.add(ALL);
        cats.addAll(WorldAssets.categories());
        category.setItems(FXCollections.observableArrayList(cats));
        category.setValue(ALL);
        category.setMaxWidth(Double.MAX_VALUE);
        category.setFocusTraversable(false);
        Label hint = new Label("Pick a symbol, then Stamp it one at a time or Scatter a whole forest or range.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 0.917em;");

        list.setCellFactory(v -> new ListCell<>() {
            private final ImageView iv = new ImageView();

            @Override
            protected void updateItem(WorldAssets.Feature f, boolean empty) {
                super.updateItem(f, empty);
                if (empty || f == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                WorldStyle style = ts.stampStyle.get() == null ? map.style : ts.stampStyle.get();
                Image img = WorldAssets.image(store.assets(), style, f.id(), 64);
                iv.setImage(img);
                iv.setFitWidth(48);
                iv.setFitHeight(40);
                iv.setPreserveRatio(true);
                iv.setSmooth(style != WorldStyle.RETRO_PIXEL);
                HBox box = new HBox(8, iv, new Label(f.name()));
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
                setText(null);
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);
        list.getSelectionModel().selectedItemProperty().addListener((o, a, n) -> {
            if (syncing || n == null) return;
            ts.armed.set(n.id());
            if (ts.tool.get() != WorldTool.STAMP && ts.tool.get() != WorldTool.SCATTER) ts.tool.set(WorldTool.STAMP);
        });
        ts.armed.addListener((o, a, n) -> {
            syncing = true;
            if (n == null) list.getSelectionModel().clearSelection();
            else {
                WorldAssets.Feature f = WorldAssets.get(n);
                if (f != null && list.getItems().contains(f)) list.getSelectionModel().select(f);
            }
            syncing = false;
        });
        search.textProperty().addListener((o, a, n) -> refill());
        category.valueProperty().addListener((o, a, n) -> refill());
        ts.stampStyle.addListener((o, a, n) -> list.refresh());

        getChildren().addAll(title, search, category, list, hint);
        refill();
    }

    /** Redraws the thumbnails (after the map's art style changed). */
    void restyle() {
        list.refresh();
    }

    private void refill() {
        String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        String cat = category.getValue();
        List<WorldAssets.Feature> items = new ArrayList<>();
        for (WorldAssets.Feature f : WorldAssets.features()) {
            if (!ALL.equals(cat) && !f.category().equals(cat)) continue;
            if (!q.isEmpty() && !f.name().toLowerCase(Locale.ROOT).contains(q) && !f.id().contains(q)) continue;
            items.add(f);
        }
        syncing = true;
        list.setItems(FXCollections.observableArrayList(items));
        String armed = ts.armed.get();
        if (armed != null) {
            WorldAssets.Feature f = WorldAssets.get(armed);
            if (f != null && items.contains(f)) list.getSelectionModel().select(f);
        }
        syncing = false;
    }
}
