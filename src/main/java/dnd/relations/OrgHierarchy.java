package dnd.relations;

import dnd.model.Organisation;

import java.util.ArrayList;
import java.util.List;

/** Lays out the chain of command of an organisation like a family tree: the people at the top in the first row, those who report to them below. */
public final class OrgHierarchy {
    private OrgHierarchy() { }

    /** Where each member goes. The first row ({@code generation} 0) is the people who report to nobody. */
    public static FamilyTreeLayout.Layout layout(Organisation o) {
        List<String> people = new ArrayList<>();
        List<String[]> reports = new ArrayList<>();
        for (Organisation.Member m : o.members) {
            people.add(m.id);
            String boss = o.bossOf(m.id);
            if (boss != null) reports.add(new String[]{boss, m.id});
        }
        return FamilyTreeLayout.layout(people, reports, List.of(), List.of());
    }

    /** {boss, member} pairs for every member who reports to another member, in member-list order. */
    public static List<String[]> reportLines(Organisation o) {
        List<String[]> out = new ArrayList<>();
        for (Organisation.Member m : o.members) {
            String boss = o.bossOf(m.id);
            if (boss != null) out.add(new String[]{boss, m.id});
        }
        return out;
    }
}
