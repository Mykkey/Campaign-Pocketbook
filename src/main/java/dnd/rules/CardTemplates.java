package dnd.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dnd.model.Card;
import dnd.storage.Json;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Starting points for new cards: blank player/monster plus the bundled example creatures. */
public final class CardTemplates {
    private CardTemplates() { }

    public static final String BLANK_PLAYER = "blank-player";
    public static final String BLANK_MONSTER = "blank-monster";

    public static final class Template {
        public final String id, name, summary;

        Template(String id, String name, String summary) {
            this.id = id;
            this.name = name;
            this.summary = summary;
        }

        @Override
        public String toString() {
            return summary == null || summary.isBlank() ? name : name + "  -  " + summary;
        }
    }

    private static List<Template> cache;

    /** Blank templates first, then the bundled creatures. */
    public static synchronized List<Template> list() {
        if (cache != null) return cache;
        List<Template> out = new ArrayList<>();
        out.add(new Template(BLANK_PLAYER, "Blank player character", "Level 1 style PC"));
        out.add(new Template(BLANK_MONSTER, "Blank creature", "Empty stat block"));
        try (InputStream in = CardTemplates.class.getResourceAsStream("/cards/index.json")) {
            if (in != null) {
                for (JsonNode n : new ObjectMapper().readTree(in)) {
                    out.add(new Template(n.get("id").asText(), n.get("name").asText(), n.path("summary").asText("")));
                }
            }
        } catch (IOException e) {
            dnd.util.Log.error("Problem in CardTemplates", e);
        }
        cache = Collections.unmodifiableList(out);
        return cache;
    }

    /** A fresh card (new id) built from a template, using {@code name} if given. */
    public static Card create(String templateId, String name) {
        Card c;
        if (BLANK_PLAYER.equals(templateId)) {
            c = new Card();
            c.kind = Card.Kind.PLAYER;
            c.ac = 12;
            c.hpMax = 10;
            c.speed = "30 ft.";
            c.alignment = "";
            c.creatureType = "humanoid";
        } else if (BLANK_MONSTER.equals(templateId) || templateId == null) {
            c = new Card();
        } else {
            c = load(templateId);
            if (c == null) c = new Card();
        }
        c.id = java.util.UUID.randomUUID().toString();
        if (name != null && !name.isBlank()) c.name = name.trim();
        return c;
    }

    private static Card load(String id) {
        try (InputStream in = CardTemplates.class.getResourceAsStream("/cards/" + id + ".json")) {
            if (in == null) return null;
            return Json.readStream(in, Card.class);
        } catch (IOException e) {
            dnd.util.Log.error("Problem in CardTemplates", e);
            return null;
        }
    }
}
