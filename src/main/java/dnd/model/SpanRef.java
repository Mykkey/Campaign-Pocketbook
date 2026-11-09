package dnd.model;

import com.fasterxml.jackson.annotation.JsonSetter;

/** What a span of words in a text object refers to: an item to open (link) and/or a note shown on hover. */
public class SpanRef {
    /** Id of the project item the words link to (it is previewed on hover and opened on double-click), or null. */
    public String link;
    /** The DM's own note for these words, with {@code **bold**} and {@code *italic*} markup, or null. */
    public String note;

    public SpanRef() { }

    public SpanRef(String link, String note) {
        this.link = link;
        this.note = note;
    }

    /** Older files had a "tooltip" that previewed an item: that is exactly what a link does now. */
    @JsonSetter("tip")
    private void legacyTip(String itemId) {
        if (itemId != null && link == null) link = itemId;
    }

    public boolean hasNote() {
        return note != null && !note.isBlank();
    }

    public boolean isEmpty() {
        return link == null && !hasNote();
    }

    public SpanRef copy() {
        return new SpanRef(link, note);
    }
}
