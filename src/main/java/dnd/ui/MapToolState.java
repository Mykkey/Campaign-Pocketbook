package dnd.ui;

import dnd.model.Terrain;
import javafx.beans.property.*;
import javafx.scene.paint.Color;

/** Current tool and brush settings for one open battlemap. */
public class MapToolState {
    public final ObjectProperty<MapTool> tool = new SimpleObjectProperty<>(MapTool.SELECT);
    /** Terrain to paint; null means "erase". */
    public final ObjectProperty<Terrain> terrain = new SimpleObjectProperty<>(Terrain.GRASS);
    public final IntegerProperty brush = new SimpleIntegerProperty(1);
    /** Library id of the piece that the Piece tool stamps, or null. */
    public final StringProperty armedAsset = new SimpleStringProperty();
    public final BooleanProperty snap = new SimpleBooleanProperty(true);
    /** Fog of war on for this map (mirrors {@code BattleMap.fogEnabled}). */
    public final BooleanProperty fogEnabled = new SimpleBooleanProperty(false);
    /** Fog tool: true = reveal cells, false = hide them. */
    public final BooleanProperty fogReveal = new SimpleBooleanProperty(true);
    /** Fog tool: true = drag a rectangle, false = paint with the brush. */
    public final BooleanProperty fogRect = new SimpleBooleanProperty(false);
    public final ObjectProperty<Color> tokenColor = new SimpleObjectProperty<>(Color.web("#d33f49"));
}
