package dnd.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A group of characters: a guild, a faction, a family, a party, a villain's gang. Its members are other cards (player
 * characters, NPCs, monsters) and can also be other organisations, each with a role such as "Leader" or "Spy".
 * Stored as organisations/&lt;id&gt;.organisation (JSON).
 */
public class Organisation extends ProjectEntry {
    /** One member: a card or another organisation in the project, and what it is within this group. */
    public static class Member {
        /** The id of the card (or organisation) in the project tree. */
        public String id = "";
        /** What the member is here ("Leader", "Treasurer", "Spy"), as typed. */
        public String role = "";

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
        for (Member m : members) if (m.id.equals(itemId)) return true;
        return false;
    }

    /** Adds a member unless it is already one (or is this group itself). Returns true if it was added. */
    public boolean addMember(String itemId, String role) {
        if (itemId == null || itemId.isBlank() || itemId.equals(id) || hasMember(itemId)) return false;
        members.add(new Member(itemId, role));
        return true;
    }

    public boolean removeMember(String itemId) {
        return members.removeIf(m -> m.id.equals(itemId));
    }

    /** "3 members", "1 member" or "No members yet". */
    public String sizeText() {
        return members.isEmpty() ? "No members yet" : members.size() + (members.size() == 1 ? " member" : " members");
    }

    public Organisation copy() {
        Organisation o = new Organisation();
        o.id = id; o.name = name; o.type = type; o.base = base; o.summary = summary; o.goals = goals; o.notes = notes;
        for (Member m : members) o.members.add(new Member(m.id, m.role));
        return o;
    }
}
