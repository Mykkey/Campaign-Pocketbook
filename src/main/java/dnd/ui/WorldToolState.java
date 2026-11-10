package dnd.ui;

import dnd.model.WorldBiome;
import dnd.model.WorldTerrain;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

/** What the world map editor is set to paint with right now. */
final class WorldToolState {
    final ObjectProperty<WorldTool> tool = new SimpleObjectProperty<>(WorldTool.BASE);
    /** What the land-and-sea tool paints; null means "erase". */
    final ObjectProperty<WorldTerrain> terrain = new SimpleObjectProperty<>(WorldTerrain.LAND);
    /** What the biome tool paints; null means "erase". */
    final ObjectProperty<WorldBiome> biome = new SimpleObjectProperty<>(WorldBiome.GRASS);
    /** Brush width in map cells. */
    final IntegerProperty brush = new SimpleIntegerProperty(14);
    /** The kind of line the line tool draws. */
    final ObjectProperty<dnd.model.WorldPath.Type> pathType = new SimpleObjectProperty<>(dnd.model.WorldPath.Type.ROAD);
    /** The width of new lines in map cells; 0 means the kind's own width. */
    final javafx.beans.property.DoubleProperty pathWidth = new javafx.beans.property.SimpleDoubleProperty(0);
    /** The symbol (library id) the stamp and scatter tools place, or null. */
    final ObjectProperty<String> armed = new SimpleObjectProperty<>();
    /** Width of placed symbols in map cells; 0 means each symbol's own default width. */
    final IntegerProperty stampSize = new SimpleIntegerProperty(0);
    /** How thickly the scatter tool plants, 1 (sparse) to 10 (dense). */
    final IntegerProperty density = new SimpleIntegerProperty(5);
    /** The art style given to new symbols, or null to follow the map's style. */
    final ObjectProperty<dnd.model.WorldStyle> stampStyle = new SimpleObjectProperty<>();
}
