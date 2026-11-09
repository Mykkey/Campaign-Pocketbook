package dnd.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One whiteboard page: its objects plus where the user last left the camera. */
public class Board {
    public String id = UUID.randomUUID().toString();
    public String name = "Untitled";
    public double offsetX = 0, offsetY = 0, zoom = 1;
    public List<BoardObject> objects = new ArrayList<>();

    public Board() { }

    public BoardObject find(String objectId) {
        for (BoardObject o : objects) {
            if (o.id.equals(objectId)) return o;
        }
        return null;
    }
}
