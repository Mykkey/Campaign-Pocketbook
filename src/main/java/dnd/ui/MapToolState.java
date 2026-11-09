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
    /** Area tool: shape and size (feet) of the template, and whether the players see it. */
    public final ObjectProperty<dnd.model.AreaTemplate.Shape> templateShape = new SimpleObjectProperty<>(dnd.model.AreaTemplate.Shape.CONE);
    public final IntegerProperty templateFeet = new SimpleIntegerProperty(30);
    public final BooleanProperty templateShared = new SimpleBooleanProperty(false);
    /** Light tool: which kind of light to place. */
    public final ObjectProperty<dnd.map.LightPresets.Preset> lightPreset = new SimpleObjectProperty<>(dnd.map.LightPresets.find("Torch"));
    /** Ruler tool: whether the players see the measuring line. */
    public final BooleanProperty rulerShared = new SimpleBooleanProperty(false);
    /** Dark map on for this map (mirrors {@code BattleMap.darkMap}). */
    public final BooleanProperty darkMap = new SimpleBooleanProperty(false);
    public final ObjectProperty<Color> tokenColor = new SimpleObjectProperty<>(Color.web("#d33f49"));
}
