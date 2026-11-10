package dnd.model;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

/**
 * A world map: a large freehand-painted picture of a continent or a whole world. The land and sea (base layer) and
 * what covers the land (biome layer) are painted cell by cell and drawn smoothly, in one of several art styles.
 * One map cell is one map unit; {@link #milesPerCell} says how far that is in the game world.
 */
public class WorldMap extends ProjectEntry {
    public static final int SMALL_COLS = 512, SMALL_ROWS = 384;
    public static final int MEDIUM_COLS = 1024, MEDIUM_ROWS = 768;
    public static final int LARGE_COLS = 2048, LARGE_ROWS = 1536;

    public int cols = MEDIUM_COLS;
    public int rows = MEDIUM_ROWS;
    /** How many miles one cell is across. */
    public double milesPerCell = 2;
    public WorldStyle style = WorldStyle.FANTASY_COLOUR;
    public double offsetX = 40, offsetY = 40, zoom = 1;

    /** The symbols placed on the map (mountains, trees, towns...). */
    public java.util.List<WorldStamp> stamps = new java.util.ArrayList<>();
    /** The rivers, roads, borders and other lines drawn on the map. */
    public java.util.List<WorldPath> paths = new java.util.ArrayList<>();
    /** The named areas (kingdoms, forests...) outlined on the map. */
    public java.util.List<WorldRegion> regions = new java.util.ArrayList<>();

    /** The grid laid over the map, its size in miles, and whether its cells are named. */
    public WorldGridKind gridKind = WorldGridKind.NONE;
    public double gridMiles = 24;
    public boolean gridCoords;
    /** Whether the scale bar and the legend are drawn, and where the legend's top left corner is (null: bottom left). */
    public boolean showScale = true;
    public boolean showLegend;
    public Double legendX, legendY;
    /** Miles per day for each way of travelling, used by the ruler. */
    public java.util.Map<String, Double> paces = dnd.map.world.WorldTravel.defaultPaces();

    /** The season the map is shown in (colours only), and the year it is shown as (null: every year at once; see WorldEra). */
    public WorldSeason season = WorldSeason.NONE;
    public Integer year;

    /** Whether the height of the land is shown as relief shading (only has an effect once some elevation has been painted), and how strongly. */
    public boolean relief = true;
    public double reliefStrength = 1.0;

    /** True when the players only see the parts of the map the DM has revealed (the rest shows as unexplored parchment). */
    public boolean hideUnexplored;

    /** Bumped whenever the revealed area changes, so the cover picture knows to redraw. Not saved. */
    @JsonIgnore
    public transient int revealVersion;

    /** Bumped whenever the content changes (not the camera), so cached pictures know to redraw. Not saved. */
    @JsonIgnore
    public transient int revision;

    @JsonIgnore
    private PaintLayer base, biome, reveal, elevation;
    private String loadedBase, loadedBiome, loadedReveal, loadedElevation;

    public WorldMap() { }

    public WorldMap(String name, int cols, int rows, double milesAcross, WorldStyle style) {
        this.name = name;
        this.cols = cols;
        this.rows = rows;
        this.milesPerCell = Math.max(0.01, milesAcross / cols);
        this.style = style;
    }

    /** The land-and-sea layer (values are {@link WorldTerrain} codes). */
    public PaintLayer base() {
        if (base == null) base = PaintLayer.decode(cols, rows, loadedBase);
        return base;
    }

    /** The biome layer (values are {@link WorldBiome} codes). */
    public PaintLayer biome() {
        if (biome == null) biome = PaintLayer.decode(cols, rows, loadedBiome);
        return biome;
    }

    /** How many map cells one cell of the reveal layer covers across (the reveal layer is coarse, to stay small). */
    public static final int REVEAL_STEP = 4;

    public int revealCols() {
        return (cols + REVEAL_STEP - 1) / REVEAL_STEP;
    }

    public int revealRows() {
        return (rows + REVEAL_STEP - 1) / REVEAL_STEP;
    }

    /** The area the players may see, at a quarter of the map's resolution: 1 = revealed, 0 = still unexplored. */
    public PaintLayer reveal() {
        if (reveal == null) reveal = PaintLayer.decode(revealCols(), revealRows(), loadedReveal);
        return reveal;
    }

    /** True if the point (in map cells) is in the revealed area, or if nothing is being hidden from the players. */
    public boolean isRevealed(double x, double y) {
        if (!hideUnexplored) return true;
        return reveal().get((int) Math.floor(x / REVEAL_STEP), (int) Math.floor(y / REVEAL_STEP)) != 0;
    }

    /** How high the land is, one value per cell from 0 (flat) to 255 (the highest peak), painted with the elevation tool. */
    public PaintLayer elevation() {
        if (elevation == null) elevation = PaintLayer.decode(cols, rows, loadedElevation);
        return elevation;
    }

    @JsonGetter("elevationData")
    private String elevationDataOut() {
        return elevation().isEmpty() ? null : elevation().encode();
    }

    @JsonSetter("elevationData")
    private void elevationDataIn(String data) {
        loadedElevation = data;
        elevation = null;
    }

    @JsonGetter("revealData")
    private String revealDataOut() {
        return reveal().isEmpty() ? null : reveal().encode();
    }

    @JsonSetter("revealData")
    private void revealDataIn(String data) {
        loadedReveal = data;
        reveal = null;
    }

    @JsonGetter("baseData")
    private String baseDataOut() {
        return base().isEmpty() ? null : base().encode();
    }

    @JsonSetter("baseData")
    private void baseDataIn(String data) {
        loadedBase = data;
        base = null;
    }

    @JsonGetter("biomeData")
    private String biomeDataOut() {
        return biome().isEmpty() ? null : biome().encode();
    }

    @JsonSetter("biomeData")
    private void biomeDataIn(String data) {
        loadedBiome = data;
        biome = null;
    }

    public double milesAcross() {
        return cols * milesPerCell;
    }

    public double milesDown() {
        return rows * milesPerCell;
    }

    /** Puts in new paint layers (after the map was resized); they must already have the map's new size. */
    public void replaceLayers(PaintLayer newBase, PaintLayer newBiome, PaintLayer newReveal, PaintLayer newElevation) {
        base = newBase;
        biome = newBiome;
        reveal = newReveal;
        elevation = newElevation;
    }

    public WorldMap copy() {
        WorldMap m = new WorldMap(name, cols, rows, milesAcross(), style);
        m.id = id;
        m.milesPerCell = milesPerCell;
        m.offsetX = offsetX;
        m.offsetY = offsetY;
        m.zoom = zoom;
        m.gridKind = gridKind;
        m.gridMiles = gridMiles;
        m.gridCoords = gridCoords;
        m.showScale = showScale;
        m.showLegend = showLegend;
        m.legendX = legendX;
        m.legendY = legendY;
        m.hideUnexplored = hideUnexplored;
        m.reveal = reveal().copy();
        m.elevation = elevation().copy();
        m.relief = relief;
        m.season = season;
        m.year = year;
        m.reliefStrength = reliefStrength;
        m.paces = new java.util.LinkedHashMap<>(paces);
        m.base = base().copy();
        m.biome = biome().copy();
        for (WorldStamp s : stamps) m.stamps.add(s.copy());
        for (WorldPath p : paths) m.paths.add(p.copy());
        for (WorldRegion r : regions) m.regions.add(r.copy());
        return m;
    }
}
