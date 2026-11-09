package dnd.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.geometry.size.FloatSize;
import com.github.weisj.jsvg.parser.DocumentLimits;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.attributes.ViewBox;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * The vector pieces available to place on battlemaps: a built-in set (SVG files on the classpath under
 * /assets) plus anything the user imported into the project's assets folder. SVGs are rasterised on demand
 * at the size needed and cached.
 */
public class AssetLibrary {
    public static final String IMPORTED = "Imported";
    private static final String IMPORT_PREFIX = "imported/";

    public static final class Asset {
        public final String id;
        public final String name;
        public final String category;
        /** Default footprint in grid cells. */
        public final double cellsW, cellsH;
        public final boolean imported;

        Asset(String id, String name, String category, double cellsW, double cellsH, boolean imported) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.cellsW = cellsW;
            this.cellsH = cellsH;
            this.imported = imported;
        }
    }

    private static List<Asset> builtIn;
    private static final SVGLoader LOADER = new SVGLoader();
    /**
     * The built-in pieces are detailed (tiled shingles, stonework, ...), so they need more shapes than
     * JSVG's default safety cap of 2000 allows. Files the user imports keep the strict default.
     */
    private static final LoaderContext BUILT_IN_CONTEXT = LoaderContext.builder()
            .documentLimits(new DocumentLimits(DocumentLimits.DEFAULT_MAX_NESTING_DEPTH,
                    DocumentLimits.DEFAULT_MAX_USE_NESTING_DEPTH, 20000))
            .build();

    private final Path importDir; // may be null (tests)
    private final Map<String, Asset> byId = new LinkedHashMap<>();
    private final Map<String, SVGDocument> svgCache = new HashMap<>();
    private final Map<String, Image> imageCache = new HashMap<>();
    private final Map<String, BufferedImage> rasterSource = new HashMap<>();

    public AssetLibrary(Path importDir) {
        this.importDir = importDir;
        reload();
    }

    /** Rescans built-ins and the import folder (call after importing a file). */
    public final synchronized void reload() {
        byId.clear();
        for (Asset a : builtIn()) byId.put(a.id, a);
        if (importDir != null && Files.isDirectory(importDir)) {
            try (Stream<Path> s = Files.list(importDir)) {
                List<Path> files = new ArrayList<>();
                s.filter(AssetLibrary::isSupported).forEach(files::add);
                files.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
                for (Path f : files) {
                    String file = f.getFileName().toString();
                    String id = IMPORT_PREFIX + file;
                    double[] size = naturalSize(id);
                    double max = Math.max(size[0], size[1]);
                    double scale = max <= 0 ? 1 : 2.0 / max; // longest side = 2 cells by default
                    byId.put(id, new Asset(id, stripExt(file), IMPORTED,
                            Math.max(0.25, size[0] * scale), Math.max(0.25, size[1] * scale), true));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean isSupported(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".svg") || n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
    }

    private static String stripExt(String n) {
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    private static synchronized List<Asset> builtIn() {
        if (builtIn != null) return builtIn;
        List<Asset> out = new ArrayList<>();
        try (InputStream in = AssetLibrary.class.getResourceAsStream("/assets/index.json")) {
            if (in != null) {
                JsonNode root = new ObjectMapper().readTree(in);
                for (JsonNode n : root) {
                    out.add(new Asset(n.get("id").asText(), n.get("name").asText(), n.get("category").asText(),
                            n.get("w").asDouble(1), n.get("h").asDouble(1), false));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        builtIn = Collections.unmodifiableList(out);
        return builtIn;
    }

    public synchronized List<Asset> assets() {
        return new ArrayList<>(byId.values());
    }

    public synchronized Asset get(String id) {
        return byId.get(id);
    }

    /** Category names in display order (built-in categories first, Imported last). */
    public synchronized List<String> categories() {
        LinkedHashSet<String> cats = new LinkedHashSet<>();
        for (Asset a : byId.values()) if (!a.imported) cats.add(a.category);
        cats.add(IMPORTED);
        return new ArrayList<>(cats);
    }

    // ---------------------------------------------------------------- rendering

    private URL builtInUrl(String id) {
        return AssetLibrary.class.getResource("/assets/" + id + ".svg");
    }

    private SVGDocument svg(String id) {
        if (svgCache.containsKey(id)) return svgCache.get(id);
        SVGDocument doc = null;
        try {
            if (id.startsWith(IMPORT_PREFIX)) {
                Path f = importDir.resolve(id.substring(IMPORT_PREFIX.length()));
                if (f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".svg") && Files.isRegularFile(f)) {
                    doc = LOADER.load(f.toUri().toURL());
                }
            } else {
                URL u = builtInUrl(id);
                if (u != null) doc = LOADER.load(u, BUILT_IN_CONTEXT);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        svgCache.put(id, doc);
        return doc;
    }

    private BufferedImage raster(String id) {
        if (rasterSource.containsKey(id)) return rasterSource.get(id);
        BufferedImage img = null;
        try {
            Path f = importDir.resolve(id.substring(IMPORT_PREFIX.length()));
            if (Files.isRegularFile(f)) img = ImageIO.read(f.toFile());
        } catch (Exception e) {
            e.printStackTrace();
        }
        rasterSource.put(id, img);
        return img;
    }

    private boolean isSvg(String id) {
        return id.toLowerCase(Locale.ROOT).endsWith(".svg") || !id.startsWith(IMPORT_PREFIX);
    }

    /** Natural {width, height} of the artwork (arbitrary units, only the ratio matters). */
    private double[] naturalSize(String id) {
        if (isSvg(id)) {
            SVGDocument d = svg(id);
            if (d != null) {
                FloatSize s = d.size();
                if (s.width > 0 && s.height > 0) return new double[]{s.width, s.height};
            }
        } else {
            BufferedImage b = raster(id);
            if (b != null) return new double[]{b.getWidth(), b.getHeight()};
        }
        return new double[]{1, 1};
    }

    /**
     * Renders the asset so its longer side is {@code px} pixels, keeping the artwork's aspect ratio.
     * Returns null if the asset can't be loaded. Does not need the JavaFX toolkit.
     */
    public synchronized BufferedImage render(String id, int px) {
        if (id == null || px < 1) return null;
        double[] nat = naturalSize(id);
        double max = Math.max(nat[0], nat[1]);
        int w = Math.max(1, (int) Math.round(px * nat[0] / max));
        int h = Math.max(1, (int) Math.round(px * nat[1] / max));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            if (isSvg(id)) {
                SVGDocument d = svg(id);
                if (d == null) return null;
                d.render(null, g, new ViewBox(0, 0, w, h));
            } else {
                BufferedImage src = raster(id);
                if (src == null) return null;
                g.drawImage(src, 0, 0, w, h, null);
            }
        } finally {
            g.dispose();
        }
        return out;
    }

    /** The cached image for this asset and size, or null if it hasn't been rendered yet (never renders). */
    public synchronized Image cachedImage(String id, double wantedPx) {
        int bucket = 64;
        while (bucket < wantedPx && bucket < 1024) bucket *= 2;
        return imageCache.get(id + "@" + bucket);
    }

    /** Renders the pieces used on a map in the background, so drawing the map doesn't hitch the first time. */
    public void warmUp(java.util.Collection<String> assetIds, double px) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>(assetIds);
        Thread t = new Thread(() -> {
            for (String id : ids) image(id, px);
        }, "asset-warmup");
        t.setDaemon(true);
        t.start();
    }

    /** A JavaFX image of the asset, cached per size bucket (64, 128, 256, 512, 1024 px). */
    public synchronized Image image(String id, double wantedPx) {
        int bucket = 64;
        while (bucket < wantedPx && bucket < 1024) bucket *= 2;
        String key = id + "@" + bucket;
        Image cached = imageCache.get(key);
        if (cached != null) return cached;
        BufferedImage bi = render(id, bucket);
        Image img = bi == null ? null : SwingFXUtils.toFXImage(bi, null);
        if (img != null) imageCache.put(key, img);
        return img;
    }
}
