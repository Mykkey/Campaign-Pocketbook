package dnd;

import dnd.model.BoardObject;
import dnd.storage.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextBoxTest {
    @Test
    void aSetWidthIsSavedAndCopied() {
        BoardObject o = new BoardObject(BoardObject.Type.TEXT);
        o.text = "Hello there";
        o.wrapWidth = 240;
        BoardObject back = Json.read(Json.write(o), BoardObject.class);
        assertEquals(240, back.wrapWidth);
        assertEquals(240, o.copy().wrapWidth);
    }

    @Test
    void textFromOlderBoardsKeepsItsAutomaticWidth() {
        String old = "{\"id\":\"t1\",\"type\":\"TEXT\",\"x\":10,\"y\":20,\"w\":120,\"h\":26,\"text\":\"Old text\",\"fontSize\":20}";
        BoardObject o = Json.read(old, BoardObject.class);
        assertEquals(0, o.wrapWidth, "0 means the box takes the width of its text");
        assertEquals("Old text", o.text);
    }
}
