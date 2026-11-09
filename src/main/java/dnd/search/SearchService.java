package dnd.search;

import dnd.model.Board;
import dnd.model.BoardObject;
import dnd.model.TreeNode;
import dnd.storage.ProjectStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Project-wide text search and backlink lookup. */
public final class SearchService {
    private SearchService() { }

    public static final class Result {
        public final String boardId;
        public final String boardName;
        /** Null when the hit is the board's name itself. */
        public final String objectId;
        public final String snippet;

        public Result(String boardId, String boardName, String objectId, String snippet) {
            this.boardId = boardId;
            this.boardName = boardName;
            this.objectId = objectId;
            this.snippet = snippet;
        }
    }

    public static List<Result> search(ProjectStore store, String query, int limit) {
        List<Result> out = new ArrayList<>();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return out;

        List<TreeNode> nodes = new ArrayList<>();
        store.project.root.collectBoards(nodes);
        for (TreeNode n : nodes) {
            if (n.name.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(new Result(n.id, n.name, null, "Whiteboard"));
            }
            Board b = store.board(n.id);
            if (b == null) continue;
            for (BoardObject o : b.objects) {
                if (o.text != null && dnd.text.RichText.plain(o.text).toLowerCase(Locale.ROOT).contains(q)) {
                    out.add(new Result(n.id, n.name, o.id, snippet(o)));
                    if (out.size() >= limit) return out;
                    continue;
                }
                String note = noteMatch(o, q);
                if (note != null) {
                    out.add(new Result(n.id, n.name, o.id, "note: " + excerpt(note, q)));
                    if (out.size() >= limit) return out;
                }
            }
        }

        // cards: by name, then by what is written on them (traits, actions, attacks, notes...)
        List<TreeNode> cards = new ArrayList<>();
        store.project.root.collectCards(cards);
        for (TreeNode n : cards) {
            if (n.name.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(new Result(n.id, n.name, null, "Card"));
                if (out.size() >= limit) return out;
                continue;
            }
            dnd.model.Card c = store.card(n.id);
            String hit = c == null ? null : cardMatch(c, q);
            if (hit != null) {
                out.add(new Result(n.id, n.name, null, "Card - " + hit));
                if (out.size() >= limit) return out;
            }
        }

        // spell cards: by name, then by what is written on them
        List<TreeNode> spells = new ArrayList<>();
        store.project.root.collectSpells(spells);
        for (TreeNode n : spells) {
            if (n.name.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(new Result(n.id, n.name, null, "Spell"));
                if (out.size() >= limit) return out;
                continue;
            }
            dnd.model.Spell sp = store.spell(n.id);
            String hit = sp == null ? null : spellMatch(sp, q);
            if (hit != null) {
                out.add(new Result(n.id, n.name, null, "Spell - " + hit));
                if (out.size() >= limit) return out;
            }
        }

        // magic items: by name, then by what is written on them
        List<TreeNode> magicItems = new ArrayList<>();
        store.project.root.collectKind(TreeNode.KIND_ITEM, magicItems);
        for (TreeNode n : magicItems) {
            if (n.name.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(new Result(n.id, n.name, null, "Magic item"));
                if (out.size() >= limit) return out;
                continue;
            }
            dnd.model.MagicItem mi = store.magicItem(n.id);
            String hit = mi == null ? null : itemMatch(mi, q);
            if (hit != null) {
                out.add(new Result(n.id, n.name, null, "Magic item - " + hit));
                if (out.size() >= limit) return out;
            }
        }

        // quests: by name, then by who gave them, the steps and notes
        List<TreeNode> quests = new ArrayList<>();
        store.project.root.collectKind(TreeNode.KIND_QUEST, quests);
        for (TreeNode n : quests) {
            if (n.name.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(new Result(n.id, n.name, null, "Quest"));
                if (out.size() >= limit) return out;
                continue;
            }
            dnd.model.Quest qu = store.quest(n.id);
            String hit = qu == null ? null : questMatch(qu, q, store::itemNameOrNull);
            if (hit != null) {
                out.add(new Result(n.id, n.name, null, "Quest - " + hit));
                if (out.size() >= limit) return out;
            }
        }

        // sessions: by name, then by recap, what happened and next steps
        List<TreeNode> sessionNodes = new ArrayList<>();
        store.project.root.collectKind(TreeNode.KIND_SESSION, sessionNodes);
        for (TreeNode n : sessionNodes) {
            if (n.name.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(new Result(n.id, n.name, null, "Session"));
                if (out.size() >= limit) return out;
                continue;
            }
            dnd.model.Session se = store.session(n.id);
            String hit = se == null ? null : sessionMatch(se, q);
            if (hit != null) {
                out.add(new Result(n.id, n.name, null, "Session - " + hit));
                if (out.size() >= limit) return out;
            }
        }

        // encounters: by name and notes
        List<TreeNode> encounters = new ArrayList<>();
        store.project.root.collectEncounters(encounters);
        for (TreeNode n : encounters) {
            if (n.name.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(new Result(n.id, n.name, null, "Encounter"));
                if (out.size() >= limit) return out;
                continue;
            }
            dnd.model.Encounter e = store.encounter(n.id);
            if (e != null && e.notes != null && e.notes.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(new Result(n.id, n.name, null, "Encounter - notes: " + excerpt(e.notes, q)));
                if (out.size() >= limit) return out;
            }
        }

        // battlemaps: by name, and by token label
        List<TreeNode> maps = new ArrayList<>();
        store.project.root.collectMaps(maps);
        for (TreeNode n : maps) {
            if (n.name.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(new Result(n.id, n.name, null, "Battlemap"));
            }
            dnd.model.BattleMap m = store.battleMap(n.id);
            if (m == null) continue;
            int pinNo = 0;
            for (dnd.model.MapPin pin : m.pins) {
                pinNo++;
                if (has(pin.text, q)) {
                    out.add(new Result(n.id, n.name, null, "Pin " + pinNo + ": " + excerpt(pin.text, q)));
                    if (out.size() >= limit) return out;
                }
            }
            for (dnd.model.MapToken t : m.tokens) {
                if (t.label != null && t.label.toLowerCase(Locale.ROOT).contains(q)) {
                    out.add(new Result(n.id, n.name, null, "Token \"" + t.label + "\""));
                    if (out.size() >= limit) return out;
                }
            }
        }
        return out;
    }

    /** The plain text of the first note on this object (whole-object or on some words) that contains {@code q}, or null. */
    static String noteMatch(BoardObject o, String q) {
        if (o.hasNote()) {
            String plain = dnd.text.RichText.plain(o.note);
            if (has(plain, q)) return plain;
        }
        for (dnd.model.SpanRef r : o.refs.values()) {
            if (!r.hasNote()) continue;
            String plain = dnd.text.RichText.plain(r.note);
            if (has(plain, q)) return plain;
        }
        return null;
    }

    /** Where on a card the text was found, e.g. "trait Pack Tactics: ...", or null if it is not on the card. */
    static String cardMatch(dnd.model.Card c, String q) {
        String hit;
        if ((hit = featureMatch("trait", c.traits, q)) != null) return hit;
        if ((hit = featureMatch("action", c.actions, q)) != null) return hit;
        if ((hit = featureMatch("reaction", c.reactions, q)) != null) return hit;
        if ((hit = featureMatch("legendary action", c.legendary, q)) != null) return hit;
        for (dnd.model.Attack a : c.attacks) {
            if (has(a.name, q) || has(a.text, q)) return "attack " + a.name;
        }
        for (String alias : c.aliases) if (has(alias, q)) return "also known as: " + alias;
        for (dnd.model.Relation r : c.relations) if (has(r.note, q)) return "relationship: " + excerpt(r.note, q);
        String[][] fields = {{"notes", c.notes}, {"type", c.creatureType}, {"alignment", c.alignment}, {"languages", c.languages},
                {"senses", c.senses}, {"skills", c.skills}, {"resistances", c.resistances}, {"immunities", c.immunities},
                {"vulnerabilities", c.vulnerabilities}, {"saves", c.saves}};
        for (String[] f : fields) if (has(f[1], q)) return f[0] + ": " + excerpt(f[1], q);
        return null;
    }

    /** Where in a session log the text was found, or null. */
    static String sessionMatch(dnd.model.Session se, String q) {
        if (has(se.recap, q)) return "recap: " + excerpt(se.recap, q);
        if (has(se.summary, q)) return "what happened: " + excerpt(se.summary, q);
        if (has(se.nextSteps, q)) return "next: " + excerpt(se.nextSteps, q);
        if (has(se.inWorldDate, q)) return "in-world date: " + se.inWorldDate;
        return null;
    }

    /** Where on a quest the text was found, or null. */
    static String questMatch(dnd.model.Quest qu, String q) {
        return questMatch(qu, q, id -> null);
    }

    static String questMatch(dnd.model.Quest qu, String q, java.util.function.Function<String, String> nameOf) {
        if (has(qu.summary, q)) return "summary: " + excerpt(qu.summary, q);
        for (dnd.model.Quest.Step s : qu.steps) if (has(s.text, q)) return "step: " + excerpt(s.text, q);
        String giverName = qu.giverName(nameOf);
        if (has(giverName, q) || has(qu.giver, q)) return "given by: " + giverName;
        if (has(qu.reward, q)) return "reward: " + qu.reward;
        if (has(qu.notes, q)) return "notes: " + excerpt(qu.notes, q);
        return null;
    }

    /** Where on a magic item card the text was found, or null. */
    static String itemMatch(dnd.model.MagicItem m, String q) {
        if (has(m.description, q)) return "text: " + excerpt(m.description, q);
        if (has(m.curse, q)) return "curse: " + excerpt(m.curse, q);
        if (has(m.type, q)) return "type: " + m.type;
        if (has(m.rarity, q)) return "rarity: " + m.rarity;
        if (has(m.attunementNote, q)) return "attunement: " + m.attunementNote;
        if (has(m.notes, q)) return "notes: " + excerpt(m.notes, q);
        return null;
    }

    /** Where on a spell card the text was found, or null. */
    static String spellMatch(dnd.model.Spell s, String q) {
        if (has(s.description, q)) return "text: " + excerpt(s.description, q);
        if (has(s.higherLevel, q)) return "higher levels: " + excerpt(s.higherLevel, q);
        if (has(s.school, q)) return "school: " + s.school;
        for (String c : s.classes) if (has(c, q)) return "class: " + c;
        if (has(s.material, q)) return "material: " + excerpt(s.material, q);
        if (has(s.notes, q)) return "notes: " + excerpt(s.notes, q);
        return null;
    }

    private static String featureMatch(String label, java.util.List<dnd.model.Feature> list, String q) {
        for (dnd.model.Feature f : list) {
            if (has(f.name, q)) return label + " " + f.name;
            if (has(f.text, q)) return label + " " + f.name + ": " + excerpt(f.text, q);
        }
        return null;
    }

    private static boolean has(String s, String q) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(q);
    }

    /** A short piece of {@code s} around the first match of {@code q}. */
    public static String excerpt(String s, String q) {
        String flat = s.replaceAll("\\s+", " ").trim();
        int i = flat.toLowerCase(Locale.ROOT).indexOf(q);
        if (i < 0) return flat.length() > 60 ? flat.substring(0, 57) + "..." : flat;
        int from = Math.max(0, i - 20), to = Math.min(flat.length(), i + q.length() + 30);
        return (from > 0 ? "..." : "") + flat.substring(from, to) + (to < flat.length() ? "..." : "");
    }

    /** Every object on any other board that links to {@code targetId}. */
    public static List<Result> backlinks(ProjectStore store, String targetId) {
        List<Result> out = new ArrayList<>();
        for (String id : store.allBoardIds()) {
            if (id.equals(targetId)) continue;
            Board b = store.board(id);
            if (b == null) continue;
            for (BoardObject o : b.objects) {
                boolean refers = targetId.equals(o.link);
                for (dnd.model.SpanRef r : o.refs.values()) if (targetId.equals(r.link)) refers = true;
                if (refers) out.add(new Result(id, store.boardName(id), o.id, snippet(o)));
            }
        }
        return out;
    }

    static String snippet(BoardObject o) {
        if (o.type == BoardObject.Type.MAP_PREVIEW) return "battlemap preview";
        if (o.text == null || o.text.isBlank()) return o.type.name().toLowerCase(Locale.ROOT);
        String s = dnd.text.RichText.plain(o.text).replace('\n', ' ').trim();
        return s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }
}
