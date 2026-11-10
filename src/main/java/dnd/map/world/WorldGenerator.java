package dnd.map.world;

import dnd.model.PaintLayer;
import dnd.model.WorldBiome;
import dnd.model.WorldMap;
import dnd.model.WorldPath;
import dnd.model.WorldStamp;
import dnd.model.WorldTerrain;
import dnd.names.PlaceNameGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Makes a whole random world: continents and seas, mountains (as elevation), lakes, biomes by temperature and rainfall,
 * rivers that run downhill, and optionally symbols and place names. The same seed and options always give the same
 * world. Pure logic (no JavaFX), so it is unit tested.
 */
public final class WorldGenerator {
    private WorldGenerator() { }

    public enum Climate {
        COLD("Cold", 0.45), TEMPERATE("Temperate", 0.75), WARM("Warm", 0.95);

        public final String label;
        final double baseTemperature;

        Climate(String label, double baseTemperature) {
            this.label = label;
            this.baseTemperature = baseTemperature;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * @param landFraction roughly how much of the map is land (0.15 to 0.8)
     * @param mountains    how mountainous it is, 0 (rolling) to 1 (very rugged)
     */
    public record Options(long seed, double landFraction, double mountains, Climate climate, boolean rivers, boolean symbols, boolean names) {
        public static Options defaults(long seed) {
            return new Options(seed, 0.38, 0.5, Climate.TEMPERATE, true, true, true);
        }
    }

    /** Replaces everything painted and placed on the map with a new random world. The map keeps its size, scale and art style. */
    public static void generate(WorldMap m, Options o) {
        new Run(m, o).go();
    }

    private static final class Run {
        final WorldMap m;
        final Options o;
        final int cols, rows;
        final Random rnd;
        final int sElev, sDetail, sMoist, sRidge, sMask, sLake, sBiome;
        final float[] height;       // 0..1 before the sea level is applied
        final float[] elev;         // 0..1 on land, 0 at sea
        final boolean[] land;
        final float[] temp, moist;
        final byte[] base, biome;
        float[] oceanNear, oceanWide; // how much ocean is close by (0..1): tight and wide blurs
        final List<WorldPath> rivers = new ArrayList<>();
        final List<WorldStamp> stamps = new ArrayList<>();

        Run(WorldMap m, Options o) {
            this.m = m;
            this.o = o;
            cols = m.cols;
            rows = m.rows;
            rnd = new Random(o.seed);
            int s = (int) (o.seed ^ (o.seed >>> 32));
            sElev = s * 31 + 11;
            sDetail = s * 31 + 23;
            sMoist = s * 31 + 37;
            sRidge = s * 31 + 41;
            sMask = s * 31 + 53;
            sLake = s * 31 + 67;
            sBiome = s * 31 + 79;
            height = new float[cols * rows];
            elev = new float[cols * rows];
            land = new boolean[cols * rows];
            temp = new float[cols * rows];
            moist = new float[cols * rows];
            base = new byte[cols * rows];
            biome = new byte[cols * rows];
        }

        void go() {
            continents();
            coastDistances();
            mountains();
            climate();
            lakesAndBiomes();
            PaintLayer b = new PaintLayer(cols, rows), bi = new PaintLayer(cols, rows), el = new PaintLayer(cols, rows);
            System.arraycopy(base, 0, b.cells, 0, base.length);
            System.arraycopy(biome, 0, bi.cells, 0, biome.length);
            for (int i = 0; i < elev.length; i++) el.cells[i] = (byte) Math.round(Math.max(0, Math.min(1, elev[i])) * 255);
            m.replaceLayers(b, bi, new PaintLayer(m.revealCols(), m.revealRows()), el);
            m.stamps.clear();
            m.paths.clear();
            m.regions.clear();
            PlaceNameGenerator names = new PlaceNameGenerator(o.seed * 7 + 3);
            if (o.rivers) makeRivers(names);
            if (o.symbols) makeSymbols();
            List<WorldStamp> settlements = o.symbols ? makeSettlements() : List.of();
            if (o.names) makeLabels(names, settlements);
            m.paths.addAll(rivers);
            m.stamps.addAll(stamps);
            m.revision++;
        }

        // ---------------------------------------------------------------- land and sea

        void continents() {
            double ox = rnd.nextDouble() * 100, oy = rnd.nextDouble() * 100;
            double aspect = (double) rows / cols;
            for (int y = 0; y < rows; y++) {
                double ny = (y + 0.5) / rows;
                for (int x = 0; x < cols; x++) {
                    double nx = (x + 0.5) / cols;
                    double cont = WorldNoise.fbm(nx * 2.6 + ox, ny * 2.6 * aspect + oy, sElev, 5);
                    double detail = WorldNoise.fbm(nx * 9 + ox, ny * 9 * aspect + oy, sDetail, 4);
                    double e = Math.hypot((nx - 0.5) / 0.5, (ny - 0.5) / 0.5);
                    double edge = 1 - smooth(0.55, 1.1, e);
                    height[y * cols + x] = (float) (cont * 0.62 + detail * 0.14 + edge * 0.34);
                }
            }
            // sea level: the height below which the wanted share of the map lies
            float[] sample = new float[Math.max(1, height.length / 7)];
            for (int i = 0; i < sample.length; i++) sample[i] = height[i * 7];
            Arrays.sort(sample);
            double frac = Math.max(0.1, Math.min(0.85, o.landFraction));
            float sea = sample[(int) Math.min(sample.length - 1, Math.round((1 - frac) * (sample.length - 1)))];
            for (int i = 0; i < height.length; i++) {
                land[i] = height[i] > sea;
                elev[i] = land[i] ? (height[i] - sea) / Math.max(1e-6f, 1 - sea) : 0;
                base[i] = land[i] ? WorldTerrain.LAND.code : WorldTerrain.OCEAN.code;
            }
        }

        void coastDistances() {
            float[] ocean = new float[cols * rows];
            for (int i = 0; i < ocean.length; i++) ocean[i] = land[i] ? 0f : 1f;
            oceanNear = blur(ocean, cols, rows, 6);
            oceanWide = blur(ocean, cols, rows, Math.max(12, cols / 40));
        }

        void mountains() {
            double mt = Math.max(0, Math.min(1, o.mountains));
            double ox = rnd.nextDouble() * 100, oy = rnd.nextDouble() * 100;
            double aspect = (double) rows / cols;
            for (int y = 0; y < rows; y++) {
                double ny = (y + 0.5) / rows;
                for (int x = 0; x < cols; x++) {
                    int i = y * cols + x;
                    if (!land[i]) continue;
                    double nx = (x + 0.5) / cols;
                    double ridge = 1 - Math.abs(2 * WorldNoise.fbm(nx * 5.5 + ox, ny * 5.5 * aspect + oy, sRidge, 4) - 1); // sharp crests
                    double where = smooth(0.36, 0.54, WorldNoise.fbm(nx * 2.4 + oy, ny * 2.4 * aspect + ox, sMask, 3));   // ranges, not everywhere
                    double rough = WorldNoise.fbm(nx * 14 + ox, ny * 14 * aspect, sDetail + 5, 3);
                    double tt = Math.min(1, elev[i] / 0.6); // how far inland and high the broad land is, 0 to 1
                    double v = tt * 0.35 + 1.6 * mt * Math.pow(ridge, 1.2) * where * (0.35 + 0.65 * tt) + (rough - 0.5) * 0.06 * (0.3 + mt);
                    elev[i] = (float) Math.max(0, Math.min(1, v));
                }
            }
        }

        // ---------------------------------------------------------------- climate and ground

        void climate() {
            double ox = rnd.nextDouble() * 100, oy = rnd.nextDouble() * 100;
            double aspect = (double) rows / cols;
            for (int y = 0; y < rows; y++) {
                double ny = (y + 0.5) / rows;
                double lat = Math.abs(ny - 0.5) * 2;
                for (int x = 0; x < cols; x++) {
                    int i = y * cols + x;
                    double nx = (x + 0.5) / cols;
                    double sea = o.climate.baseTemperature - lat * 0.55;
                    temp[i] = (float) (sea - elev[i] * 0.35);
                    double rain = WorldNoise.fbm(nx * 3.2 + ox, ny * 3.2 * aspect + oy, sMoist, 4) * 0.72 + oceanWide[i] * 0.55;
                    moist[i] = (float) Math.max(0, Math.min(1, rain));
                }
            }
        }

        void lakesAndBiomes() {
            double aspect = (double) rows / cols;
            // lakes are blobs of noise in low ground far from the sea; the small ones are dropped, so only proper lakes remain
            boolean[] lake = new boolean[cols * rows];
            for (int y = 0; y < rows; y++) {
                double ny = (y + 0.5) / rows;
                for (int x = 0; x < cols; x++) {
                    int i = y * cols + x;
                    if (!land[i] || temp[i] < 0.05 || oceanNear[i] > 0.0001f || elev[i] >= 0.3) continue;
                    lake[i] = WorldNoise.fbm((x + 0.5) / cols * 7, ny * 7 * aspect, sLake, 3) > 0.77;
                }
            }
            dropSmall(lake, Math.max(60, cols * rows / 8000));
            for (int y = 0; y < rows; y++) {
                double ny = (y + 0.5) / rows;
                for (int x = 0; x < cols; x++) {
                    int i = y * cols + x;
                    double nx = (x + 0.5) / cols;
                    double polar = o.climate.baseTemperature - Math.abs(ny - 0.5) * 2 * 0.55; // temperature at sea level
                    if (!land[i]) {
                        if (polar < -0.02) base[i] = WorldTerrain.ICE.code; // pack ice
                        continue;
                    }
                    if (temp[i] < 0.05) { // ice cap
                        base[i] = WorldTerrain.ICE.code;
                        biome[i] = WorldBiome.SNOW.code;
                        continue;
                    }
                    if (lake[i]) {
                        base[i] = WorldTerrain.LAKE.code;
                        continue;
                    }
                    biome[i] = chooseBiome(i, nx, ny).code;
                }
            }
        }

        /** Removes every connected patch of {@code cells} smaller than {@code minSize}. */
        void dropSmall(boolean[] cells, int minSize) {
            boolean[] seen = new boolean[cells.length];
            int[] stack = new int[cells.length];
            for (int start = 0; start < cells.length; start++) {
                if (!cells[start] || seen[start]) continue;
                int top = 0, size = 0;
                List<Integer> members = new ArrayList<>();
                stack[top++] = start;
                seen[start] = true;
                while (top > 0) {
                    int i = stack[--top];
                    members.add(i);
                    size++;
                    int x = i % cols, y = i / cols;
                    int[] near = {x > 0 ? i - 1 : -1, x < cols - 1 ? i + 1 : -1, y > 0 ? i - cols : -1, y < rows - 1 ? i + cols : -1};
                    for (int j : near) {
                        if (j < 0 || !cells[j] || seen[j]) continue;
                        seen[j] = true;
                        stack[top++] = j;
                    }
                }
                if (size < minSize) for (int i : members) cells[i] = false;
            }
        }

        WorldBiome chooseBiome(int i, double nx, double ny) {
            double e = elev[i], t = temp[i], w = moist[i];
            double n = WorldNoise.fbm(nx * 20, ny * 20 * ((double) rows / cols), sBiome, 3) - 0.5; // breaks up the borders
            t += n * 0.06;
            w += n * 0.12;
            if (e > 0.74 && t < 0.9) return WorldBiome.SNOW;
            if (e > 0.58) return WorldBiome.ROCK;
            if (t < 0.12) return WorldBiome.SNOW;
            if (oceanNear[i] > 0.04 && t > 0.2 && e < 0.08) return WorldBiome.SAND; // beaches
            if (t < 0.28) return w > 0.55 ? WorldBiome.FOREST : WorldBiome.TUNDRA;
            if (t > 0.62) {
                if (w < 0.3) return n > 0 ? WorldBiome.DUNES : WorldBiome.SAND;
                if (w < 0.4) return WorldBiome.BADLANDS;
                if (w < 0.56) return WorldBiome.SAVANNA;
                return WorldBiome.JUNGLE;
            }
            if (e > 0.42) return WorldBiome.MOOR;
            if (w < 0.3) return WorldBiome.PLAINS;
            if (w < 0.5) return n > 0.18 ? WorldBiome.FARMLAND : WorldBiome.GRASS;
            if (w > 0.74 && e < 0.16) return WorldBiome.SWAMP;
            return WorldBiome.FOREST;
        }

        // ---------------------------------------------------------------- rivers

        void makeRivers(PlaceNameGenerator names) {
            int wanted = Math.max(3, (int) Math.round(cols * (double) rows / 90000.0 + 2));
            List<int[]> sources = new ArrayList<>();
            int step = Math.max(8, cols / 70);
            for (int y = step; y < rows - step; y += step) {
                for (int x = step; x < cols - step; x += step) {
                    int cx = x + rnd.nextInt(step) - step / 2, cy = y + rnd.nextInt(step) - step / 2;
                    if (cx < 1 || cy < 1 || cx >= cols - 1 || cy >= rows - 1) continue;
                    int i = cy * cols + cx;
                    if (land[i] && base[i] == WorldTerrain.LAND.code && elev[i] > 0.42 && elev[i] < 0.9) sources.add(new int[]{cx, cy});
                }
            }
            Collections.shuffle(sources, rnd);
            List<int[]> chosen = new ArrayList<>();
            int minGap = Math.max(30, cols / 18);
            for (int[] s : sources) {
                if (chosen.size() >= wanted) break;
                boolean far = true;
                for (int[] c : chosen) if (Math.hypot(c[0] - s[0], c[1] - s[1]) < minGap) far = false;
                if (far) chosen.add(s);
            }
            for (int[] s : chosen) {
                WorldPath p = trace(s[0], s[1]);
                if (p != null) rivers.add(p);
            }
            // name the longest rivers
            List<WorldPath> byLength = new ArrayList<>(rivers);
            byLength.sort((a, b) -> Integer.compare(b.count(), a.count()));
            if (o.names) for (int i = 0; i < Math.min(4, byLength.size()); i++) byLength.get(i).name = names.river();
        }

        /** Follows the steepest way downhill from a spring until it reaches the sea or a lake; null if it is too short to be a river. */
        WorldPath trace(int sx, int sy) {
            Set<Integer> seen = new HashSet<>();
            List<int[]> cells = new ArrayList<>();
            int x = sx, y = sy, stuck = 0;
            boolean reached = false;
            for (int guard = 0; guard < 6000; guard++) {
                cells.add(new int[]{x, y});
                seen.add(y * cols + x);
                int bx = -1, by = -1;
                double best = Double.MAX_VALUE;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= cols || ny >= rows || seen.contains(ny * cols + nx)) continue;
                        int j = ny * cols + nx;
                        double h = land[j] ? elev[j] + 0.0008 * WorldNoise.fbm(nx * 0.3, ny * 0.3, sDetail, 2) : -1 - oceanWide[j];
                        if (dx != 0 && dy != 0) h += 0.0004; // a straight step is slightly better than a diagonal one
                        if (h < best) {
                            best = h;
                            bx = nx;
                            by = ny;
                        }
                    }
                }
                if (bx < 0) break;
                double here = land[y * cols + x] ? elev[y * cols + x] : -1;
                stuck = best >= here ? stuck + 1 : 0;
                if (stuck > 25) break; // lost in a basin
                x = bx;
                y = by;
                int j = y * cols + x;
                if (!land[j] || base[j] == WorldTerrain.LAKE.code) {
                    cells.add(new int[]{x, y});
                    reached = true;
                    break;
                }
                if (x <= 0 || y <= 0 || x >= cols - 1 || y >= rows - 1) {
                    cells.add(new int[]{x, y});
                    reached = true;
                    break;
                }
            }
            if (cells.size() < (reached ? 22 : 40)) return null;
            WorldPath p = new WorldPath(WorldPath.Type.RIVER);
            for (int i = 0; i < cells.size(); i += 4) p.add(cells.get(i)[0] + 0.5, cells.get(i)[1] + 0.5);
            int[] last = cells.get(cells.size() - 1);
            if ((cells.size() - 1) % 4 != 0) p.add(last[0] + 0.5, last[1] + 0.5);
            return p;
        }

        // ---------------------------------------------------------------- symbols

        final Map<Long, List<WorldStamp>> hash = new HashMap<>();

        boolean free(double x, double y, double radius) {
            int bx = (int) Math.floor(x / 16), by = (int) Math.floor(y / 16);
            int reach = (int) Math.ceil(radius / 16) + 1;
            for (int j = by - reach; j <= by + reach; j++) {
                for (int i = bx - reach; i <= bx + reach; i++) {
                    List<WorldStamp> list = hash.get(key(i, j));
                    if (list == null) continue;
                    for (WorldStamp s : list) {
                        double r = (radius + width(s)) * 0.45;
                        if (Math.hypot(s.x - x, s.y - y) < r) return false;
                    }
                }
            }
            return true;
        }

        long key(int i, int j) {
            return ((long) i << 32) ^ (j & 0xffffffffL);
        }

        double width(WorldStamp s) {
            return s.size > 0 ? s.size : 4;
        }

        WorldStamp put(String feature, double x, double y, double sizeFactor) {
            WorldAssets.Feature f = WorldAssets.get(feature);
            if (f == null || stamps.size() >= 3500) return null;
            double w = f.w() * sizeFactor;
            if (!free(x, y, w)) return null;
            WorldStamp s = new WorldStamp(feature, x, y);
            s.size = w;
            s.flipX = rnd.nextBoolean();
            stamps.add(s);
            hash.computeIfAbsent(key((int) Math.floor(x / 16), (int) Math.floor(y / 16)), k -> new ArrayList<>()).add(s);
            return s;
        }

        /** Walks a jittered grid over the map and calls {@code visit} for every spot on land. */
        void sweep(double stepCells, java.util.function.BiConsumer<Integer, double[]> visit) {
            double step = Math.max(2.5, stepCells);
            for (double y = step / 2; y < rows; y += step) {
                for (double x = step / 2; x < cols; x += step) {
                    double jx = x + (rnd.nextDouble() - 0.5) * step * 0.8, jy = y + (rnd.nextDouble() - 0.5) * step * 0.8;
                    int ix = (int) jx, iy = (int) jy;
                    if (ix < 0 || iy < 0 || ix >= cols || iy >= rows) continue;
                    int i = iy * cols + ix;
                    if (!land[i] || base[i] != WorldTerrain.LAND.code) continue;
                    visit.accept(i, new double[]{jx, jy});
                }
            }
        }

        double defaultWidth(String id) {
            WorldAssets.Feature f = WorldAssets.get(id);
            return f == null ? 6 : f.w();
        }

        String any(String... ids) {
            return ids[rnd.nextInt(ids.length)];
        }

        double jitter() {
            return 0.85 + rnd.nextDouble() * 0.35;
        }

        void makeSymbols() {
            double mt = 0.35 + 0.65 * o.mountains;
            // mountains and hills first, so trees go around them
            sweep(defaultWidth("peak-single") * 0.9, (i, p) -> {
                double e = elev[i];
                if (e > 0.5 && rnd.nextDouble() < 0.65 * mt) {
                    String id = e > 0.72 || temp[i] < 0.25 ? any("peak-snowy", "range-snowy", "peak-snowy") : any("peak-single", "range-3", "peak-twin");
                    put(id, p[0], p[1], jitter() * 1.05);
                }
            });
            sweep(defaultWidth("hill") * 0.9, (i, p) -> {
                double e = elev[i];
                if (e > 0.27 && e <= 0.5 && rnd.nextDouble() < 0.4 * mt) put(any("hill", "hills-pair"), p[0], p[1], jitter());
            });
            sweep(defaultWidth("tree-oak") * 1.1, (i, p) -> {
                WorldBiome b = WorldBiome.of(biome[i]);
                if (b == null) return;
                double r = rnd.nextDouble();
                switch (b) {
                    case FOREST -> {
                        if (r < 0.7) put(temp[i] < 0.42 ? any("tree-pine", "pine-cluster", "tree-pine") : any("tree-oak", "forest-cluster", "tree-birch", "tree-oak"), p[0], p[1], jitter());
                    }
                    case JUNGLE -> {
                        if (r < 0.7) put(any("tree-jungle", "jungle-cluster", "tree-palm"), p[0], p[1], jitter());
                    }
                    case SAVANNA -> {
                        if (r < 0.14) put(any("tree-oak", "grass-tuft", "tree-palm"), p[0], p[1], jitter());
                    }
                    case DUNES, SAND, BADLANDS -> {
                        if (r < 0.28) put(any("dunes", "dunes-pair", "cactus", "cactus-group"), p[0], p[1], jitter());
                    }
                    case SWAMP -> {
                        if (r < 0.55) put(any("bog", "reeds", "tree-mangrove"), p[0], p[1], jitter());
                    }
                    case SNOW, TUNDRA -> {
                        if (r < 0.3) put(any("tree-pine-snowy", "snow-mound", "tree-pine-snowy"), p[0], p[1], jitter());
                    }
                    case GRASS, PLAINS -> {
                        if (r < 0.06) put(any("tree-oak", "tree-birch"), p[0], p[1], jitter());
                    }
                    default -> { }
                }
            });
        }

        List<WorldStamp> makeSettlements() {
            double area = cols * (double) rows / (1024.0 * 768.0);
            int[] counts = {1, Math.max(1, (int) Math.round(1.5 * area)), Math.max(1, (int) Math.round(2.5 * area)), (int) Math.round(5 * area), (int) Math.round(9 * area)};
            String[] kinds = {"settlement-capital", "settlement-city-large", "settlement-city-small", "settlement-town", "settlement-village"};
            // candidate spots: low, mild ground, near the coast or a river
            List<double[]> candidates = new ArrayList<>();
            int step = Math.max(6, cols / 90);
            for (int y = step; y < rows - step; y += step) {
                for (int x = step; x < cols - step; x += step) {
                    int cx = x + rnd.nextInt(step), cy = y + rnd.nextInt(step);
                    if (cx >= cols || cy >= rows) continue;
                    int i = cy * cols + cx;
                    if (!land[i] || base[i] != WorldTerrain.LAND.code || elev[i] > 0.34 || temp[i] < 0.15) continue;
                    WorldBiome b = WorldBiome.of(biome[i]);
                    if (b == WorldBiome.SNOW || b == WorldBiome.ROCK || b == WorldBiome.DUNES || b == WorldBiome.SWAMP) continue;
                    double score = rnd.nextDouble() * 0.4 + (oceanNear[i] > 0.02 && oceanNear[i] < 0.35 ? 0.5 : 0) + nearRiver(cx, cy) + moist[i] * 0.3;
                    candidates.add(new double[]{cx + 0.5, cy + 0.5, score, oceanNear[i] > 0.02 ? 1 : 0});
                }
            }
            candidates.sort((a, b) -> Double.compare(b[2], a[2]));
            List<WorldStamp> placed = new ArrayList<>();
            List<double[]> taken = new ArrayList<>();
            double gap = Math.max(28, cols / 14.0);
            for (int k = 0; k < kinds.length; k++) {
                int made = 0;
                for (double[] c : candidates) {
                    if (made >= counts[k]) break;
                    boolean far = true;
                    for (double[] t : taken) if (Math.hypot(t[0] - c[0], t[1] - c[1]) < gap * (k < 2 ? 1.2 : 0.8)) far = false;
                    if (!far) continue;
                    String id = c[3] > 0 && k >= 1 && k <= 3 && rnd.nextInt(3) == 0 ? "settlement-port" : kinds[k];
                    WorldStamp s = put(id, c[0], c[1], jitter() * 0.9);
                    if (s == null) continue;
                    taken.add(c);
                    placed.add(s);
                    made++;
                }
            }
            return placed;
        }

        double nearRiver(int x, int y) {
            double best = Double.MAX_VALUE;
            for (WorldPath p : rivers) {
                for (int i = 0; i < p.count(); i++) best = Math.min(best, Math.hypot(p.x(i) - x, p.y(i) - y));
            }
            return best < 10 ? 0.5 : best < 24 ? 0.2 : 0;
        }

        // ---------------------------------------------------------------- names

        void makeLabels(PlaceNameGenerator names, List<WorldStamp> settlements) {
            // seas: the middles of the biggest stretches of open water
            List<int[]> seaSpots = farthest(i -> !land[i] && base[i] == WorldTerrain.OCEAN.code, i -> oceanWide[i], 2, Math.max(60, cols / 4));
            for (int[] p : seaSpots) label(WorldLabelsKind.SEA, names.sea(), p[0], p[1]);
            // kingdoms: spread over the land
            List<int[]> regionSpots = farthest(i -> land[i] && base[i] == WorldTerrain.LAND.code, i -> 1 - oceanNear[i], 4, Math.max(80, cols / 4));
            for (int[] p : regionSpots) label(WorldLabelsKind.REGION, names.region(), p[0], p[1]);
            // the highest mountains
            int top = -1;
            for (int i = 0; i < elev.length; i++) if (land[i] && (top < 0 || elev[i] > elev[top])) top = i;
            if (top >= 0 && elev[top] > 0.6 && o.mountains > 0.15) label(WorldLabelsKind.RIVER, names.mountains(), top % cols, top / cols);
            for (WorldPath p : rivers) {
                if (p.name == null || p.count() < 3) continue;
                int mid = p.count() / 2;
                label(WorldLabelsKind.RIVER, p.name, p.x(mid) + 4, p.y(mid));
            }
            for (WorldStamp s : settlements) {
                WorldAssets.Feature f = WorldAssets.get(s.feature);
                double drop = f == null ? 3 : 1.5 + f.h() * (s.size / Math.max(1, f.w())) * 0.02;
                label(WorldLabelsKind.TOWN, names.town(), s.x, s.y + 2.4 + drop);
            }
        }

        void label(WorldLabelsKind kind, String text, double x, double y) {
            WorldStamp s = new WorldStamp(WorldLabels.FEATURE, x, y);
            s.text = text;
            s.labelKind = kind.kind.name();
            s.size = kind.kind.size * (kind == WorldLabelsKind.SEA ? 2.4 : kind == WorldLabelsKind.REGION ? 1.8 : 1.15); // a whole world is big: names must be readable
            stamps.add(s);
        }

        /** Picks up to {@code count} spots that score high and are far apart from each other. */
        List<int[]> farthest(java.util.function.IntPredicate ok, java.util.function.IntToDoubleFunction score, int count, double gap) {
            List<int[]> out = new ArrayList<>();
            int step = Math.max(8, cols / 64);
            List<double[]> cand = new ArrayList<>();
            for (int y = step; y < rows - step; y += step) {
                for (int x = step; x < cols - step; x += step) {
                    int i = y * cols + x;
                    if (ok.test(i)) cand.add(new double[]{x, y, score.applyAsDouble(i) + rnd.nextDouble() * 0.15});
                }
            }
            cand.sort((a, b) -> Double.compare(b[2], a[2]));
            for (double[] c : cand) {
                if (out.size() >= count) break;
                boolean far = true;
                for (int[] p : out) if (Math.hypot(p[0] - c[0], p[1] - c[1]) < gap) far = false;
                if (far) out.add(new int[]{(int) c[0], (int) c[1]});
            }
            return out;
        }
    }

    /** The label kinds the generator uses (a small indirection so the generator reads clearly). */
    private enum WorldLabelsKind {
        SEA(WorldLabels.Kind.SEA), REGION(WorldLabels.Kind.REGION), TOWN(WorldLabels.Kind.TOWN), RIVER(WorldLabels.Kind.RIVER);

        final WorldLabels.Kind kind;

        WorldLabelsKind(WorldLabels.Kind kind) {
            this.kind = kind;
        }
    }

    // ---------------------------------------------------------------- helpers

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** A box blur of a grid with the given radius (cells beyond the edge count as the nearest cell). */
    static float[] blur(float[] src, int w, int h, int r) {
        float[] tmp = new float[w * h], out = new float[w * h];
        float norm = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            float sum = 0;
            for (int k = -r; k <= r; k++) sum += src[y * w + Math.max(0, Math.min(w - 1, k))];
            for (int x = 0; x < w; x++) {
                tmp[y * w + x] = sum / norm;
                sum += src[y * w + Math.min(w - 1, x + r + 1)] - src[y * w + Math.max(0, x - r)];
            }
        }
        for (int x = 0; x < w; x++) {
            float sum = 0;
            for (int k = -r; k <= r; k++) sum += tmp[Math.max(0, Math.min(h - 1, k)) * w + x];
            for (int y = 0; y < h; y++) {
                out[y * w + x] = sum / norm;
                sum += tmp[Math.min(h - 1, y + r + 1) * w + x] - tmp[Math.max(0, y - r) * w + x];
            }
        }
        return out;
    }
}
