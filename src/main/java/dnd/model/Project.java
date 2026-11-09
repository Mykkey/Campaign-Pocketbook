package dnd.model;

import java.util.ArrayList;
import java.util.List;

/** Contents of project.json. */
public class Project {
    public String name = "Campaign";
    public TreeNode root = new TreeNode("Campaign", true);
    /** Tabs that were open when the project was last closed, in order. */
    public List<String> openBoards = new ArrayList<>();
    public String activeBoard;
    /** The adventuring party the encounter builder rates fights against. */
    public int partySize = 4;
    public int partyLevel = 3;

    public Project() { }
}
