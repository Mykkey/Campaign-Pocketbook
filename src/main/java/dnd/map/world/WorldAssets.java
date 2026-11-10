package dnd.map.world;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dnd.map.AssetLibrary;
import dnd.model.WorldStyle;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The library of world map symbols (mountains, trees, towns...). Every symbol exists in every art style as an SVG under
 * {@code /assets/world/<style>/<id>.svg}; the list of symbols, their groups and default sizes is {@code world-index.json}.
 * Pictures are made by the shared {@link AssetLibrary}, which caches them.
 */
public final class WorldAssets {
    private WorldAssets() { }

    /**
     * One symbol of the library.
     *
     * @param w      default width in map cells
     * @param h      default height in map cells
     * @param anchor "base" (it stands on its point, like a tree) or "center"
     * @param family symbols of the same family are mixed when scattering (several kinds of tree), or null
     */
    public record Feature(String id, String name, String category, double w, double h, String anchor, String family) {
        public boolean centered() {
            return "center".equals(anchor);
        }
    }

    private static List<Feature> all;
    private static final Map<String, Feature> BY_ID = new HashMap<>();
    private static final Map<String, Image> PIXEL = new HashMap<>();

    private static synchronized void load() {
        if (all != null) return;
        List<Feature> out = new ArrayList<>();
        try (InputStream in = WorldAssets.class.getResourceAsStream("/assets/world-index.json")) {
            if (in != null) {
                for (JsonNode n : new ObjectMapper().readTree(in)) {
                    Feature f = new Feature(n.get("id").asText(), n.get("name").asText(), n.get("category").asText(), n.get("w").asDouble(),
                            n.get("h").asDouble(), n.has("anchor") ? n.get("anchor").asText() : "base", n.has("family") ? n.get("family").asText() : null);
                    out.add(f);
                    BY_ID.put(f.id(), f);
                }
            }
        } catch (IOException e) {
            dnd.util.Log.error("Problem in WorldAssets", e);
        }
        all = out;
    }

    public static List<Feature> features() {
        load();
        return all;
    }

    public static Feature get(String id) {
        load();
        return BY_ID.get(id);
    }

    /** Group names in the library's order. */
    public static List<String> categories() {
        Set<String> cats = new LinkedHashSet<>();
        for (Feature f : features()) cats.add(f.category());
        return new ArrayList<>(cats);
    }

    /** The symbols that can stand in for this one when scattering (its family), or just itself. */
    public static List<Feature> variants(String id) {
        Feature f = get(id);
        List<Feature> out = new ArrayList<>();
        if (f == null) return out;
        if (f.family() == null) {
            out.add(f);
            return out;
        }
        for (Feature o : features()) if (f.family().equals(o.family())) out.add(o);
        return out;
    }

    /** The id of a symbol's SVG in one style, for {@link AssetLibrary}. */
    public static String assetId(WorldStyle style, String id) {
        return "world/" + style.folder() + "/" + id;
    }

    /**
     * The picture of a symbol in a style, sharp enough for {@code screenPx} pixels across. Retro pixel symbols are
     * drawn small and meant to be scaled up without smoothing, which is what gives them their chunky look.
     */
    public static Image image(AssetLibrary lib, WorldStyle style, String id, double screenPx) {
        String asset = assetId(style, id);
        if (style == WorldStyle.RETRO_PIXEL) {
            synchronized (PIXEL) {
                Image img = PIXEL.get(asset);
                if (img == null) {
                    java.awt.image.BufferedImage bi = lib.render(asset, 26);
                    if (bi != null) img = SwingFXUtils.toFXImage(bi, null);
                    if (img != null) PIXEL.put(asset, img);
                }
                return img;
            }
        }
        return lib.image(asset, screenPx);
    }
}
