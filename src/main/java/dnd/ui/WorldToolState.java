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
}
