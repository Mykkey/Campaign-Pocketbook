package dnd.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A group of characters: a guild, a faction, a family, a party, a villain's gang. Its members are other cards (player
 * characters, NPCs, monsters) and can also be other organisations, each with a role such as "Leader" or "Spy", and
 * optionally who they report to, which makes the group's hierarchy.
 * Stored as organisations/&lt;id&gt;.organisation (JSON).
 */
public class Organisation extends ProjectEntry {
    /** One member: a card or another organisation in the project, and what it is within this group. */
    public static class Member {
        /** The id of the card (or organisation) in the project tree. */
        public String id = "";
        /** What the member is here ("Leader", "Treasurer", "Spy"), as typed. */
        public String role = "";
        /** The id of another member of this group this one reports to, or null for someone at the top. */
        public String reportsTo;

        public Member() { }

        public Member(String id, String role) {
            this.id = id;
            this.role = role == null ? "" : role;
        }
    }

    /** What sort of group it is, as typed ("Thieves' guild", "Noble house", "Adventuring party"). */
    public String type = "";
    /** Where it is based. */
    public String base = "";
    public String summary = "";
    /** What the group wants. */
    public String goals = "";
    public List<Member> members = new ArrayList<>();
    /** The DM's own notes. */
    public String notes = "";

    public Organisation() { }

    /** True if the item is already a member. */
    public boolean hasMember(String itemId) {
        return member(itemId) != null;
    }

    public Member member(String itemId) {
        if (itemId == null) return null;
        for (Member m : members) if (m.id.equals(itemId)) return m;
        return null;
    }

    /** Adds a member unless it is already one (or is this group itself). Returns true if it was added. */
    public boolean addMember(String itemId, String role) {
        if (itemId == null || itemId.isBlank() || itemId.equals(id) || hasMember(itemId)) return false;
        members.add(new Member(itemId, role));
        return true;
    }

    /** Removes a member; the people who reported to it now report to its own boss (or to nobody if it had none). */
    public boolean removeMember(String itemId) {
        Member gone = member(itemId);
        if (gone == null) return false;
        String boss = bossOf(itemId);
        for (Member m : members) if (itemId.equals(m.reportsTo)) m.reportsTo = boss;
        members.remove(gone);
        return true;
    }

    // ---------------------------------------------------------------- hierarchy

    /** Who this member reports to: another member of this group, or null if it is at the top (or the boss is no longer a member). */
    public String bossOf(String itemId) {
        Member m = member(itemId);
        if (m == null || m.reportsTo == null || m.reportsTo.equals(itemId) || !hasMember(m.reportsTo)) return null;
        return m.reportsTo;
    }

    /** Everyone who reports to this member, in the order of the member list. */
    public List<String> directReports(String itemId) {
        List<String> out = new ArrayList<>();
        for (Member m : members) if (itemId.equals(bossOf(m.id))) out.add(m.id);
        return out;
    }

    /** True if {@code itemId} is below {@code ancestorId} in the hierarchy (reports to it, directly or through others). */
    public boolean isUnder(String itemId, String ancestorId) {
        String at = bossOf(itemId);
        int guard = members.size() + 1;
        while (at != null && guard-- > 0) {
            if (at.equals(ancestorId)) return true;
            at = bossOf(at);
        }
        return false;
    }

    /** The members this one could report to: everyone but itself and the people below it (that would go round in a circle). */
    public List<String> possibleBosses(String itemId) {
        List<String> out = new ArrayList<>();
        for (Member m : members) if (!m.id.equals(itemId) && !isUnder(m.id, itemId)) out.add(m.id);
        return out;
    }

    /**
     * Sets who a member reports to ({@code bossId} null puts it at the top).
     *
     * @return false (and changes nothing) if either is not a member or the change would make a circle
     */
    public boolean setBoss(String itemId, String bossId) {
        Member m = member(itemId);
        if (m == null) return false;
        if (bossId == null || bossId.isBlank()) {
            m.reportsTo = null;
            return true;
        }
        if (!possibleBosses(itemId).contains(bossId)) return false;
        m.reportsTo = bossId;
        return true;
    }

    /** A member and how far down the chain of command it is (0 for the top). */
    public record Ranked(Member member, int depth) { }

    /**
     * The members in the order of the chain of command: those who report to nobody first, each followed by the people who
     * report to them (and theirs), so the list reads like the organisation chart from top to bottom. Within a level the order
     * of the member list is kept.
     */
    public List<Ranked> inHierarchyOrder() {
        List<Ranked> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Member m : members) if (bossOf(m.id) == null) walk(m, 0, out, seen);
        for (Member m : members) if (!seen.contains(m.id)) walk(m, 0, out, seen); // anything left over (a circle in an old file)
        return out;
    }

    private void walk(Member m, int depth, List<Ranked> out, java.util.Set<String> seen) {
        if (!seen.add(m.id)) return;
        out.add(new Ranked(m, depth));
        for (String id : directReports(m.id)) walk(member(id), depth + 1, out, seen);
    }

    /** True if at least one member reports to another, so there is a hierarchy to draw. */
    public boolean hasHierarchy() {
        for (Member m : members) if (bossOf(m.id) != null) return true;
        return false;
    }

    /** "3 members", "1 member" or "No members yet". */
    public String sizeText() {
        return members.isEmpty() ? "No members yet" : members.size() + (members.size() == 1 ? " member" : " members");
    }

    public Organisation copy() {
        Organisation o = new Organisation();
        o.id = id; o.name = name; o.type = type; o.base = base; o.summary = summary; o.goals = goals; o.notes = notes;
        for (Member m : members) {
            Member c = new Member(m.id, m.role);
            c.reportsTo = m.reportsTo;
            o.members.add(c);
        }
        return o;
    }
}
