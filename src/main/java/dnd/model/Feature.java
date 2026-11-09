package dnd.model;

/** A named trait, action, reaction or legendary action on a card, e.g. "Nimble Escape" + its text. */
public class Feature {
    public String name = "";
    public String text = "";

    public Feature() { }

    public Feature(String name, String text) {
        this.name = name;
        this.text = text;
    }

    public Feature copy() {
        return new Feature(name, text);
    }
}
