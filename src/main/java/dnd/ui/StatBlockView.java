package dnd.ui;

import dnd.model.Attack;
import dnd.model.Card;
import dnd.model.Feature;
import dnd.storage.ProjectStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** A read-only, parchment-style rendering of a card, like a printed 5e stat block. */
public class StatBlockView extends VBox {
    private static final String RED = "#7a200d";
    private static final String FONT = "Georgia";

    private final ProjectStore store;

    public StatBlockView(ProjectStore store) {
        this.store = store;
        setSpacing(4);
        setPadding(new Insets(14));
        setStyle("-fx-background-color: #fdf1dc; -fx-border-color: #d9c8a4; -fx-border-width: 1;");
        setMaxWidth(420);
    }

    public void refresh(Card c) {
        getChildren().clear();

        HBox head = new HBox(8);
        VBox titles = new VBox(1);
        Text name = styled(c.name.isBlank() ? "(unnamed)" : c.name, 22, FontWeight.BOLD, FontPosture.REGULAR, RED);
        titles.getChildren().addAll(new TextFlow(name), new TextFlow(styled(subtitle(c), 12, FontWeight.NORMAL, FontPosture.ITALIC, "#222")));
        HBox.setHgrow(titles, Priority.ALWAYS);
        head.getChildren().add(titles);
        Node portrait = portrait(c);
        if (portrait != null) head.getChildren().add(portrait);
        getChildren().addAll(head, rule());

        getChildren().add(prop("Armor Class", c.ac + (c.acNote.isBlank() ? "" : " (" + c.acNote.trim() + ")")));
        getChildren().add(prop("Hit Points", c.hpMax + (c.hitDice.isBlank() ? "" : " (" + c.hitDice.trim() + ")")));
        getChildren().add(prop("Speed", c.speed));
        getChildren().add(rule());

        GridPane abilities = new GridPane();
        abilities.setHgap(2);
        abilities.setAlignment(Pos.CENTER);
        for (int i = 0; i < 6; i++) {
            Text label = styled(Card.ABILITIES[i], 12, FontWeight.BOLD, FontPosture.REGULAR, RED);
            int score = c.score(i);
            Text value = styled(score + " (" + Card.signed(Card.mod(score)) + ")", 12, FontWeight.NORMAL, FontPosture.REGULAR, "#222");
            VBox cell = new VBox(1, new TextFlow(label), new TextFlow(value));
            cell.setAlignment(Pos.CENTER);
            cell.setMinWidth(62);
            abilities.add(cell, i, 0);
        }
        getChildren().addAll(abilities, rule());

        addProp("Saving Throws", c.saves);
        addProp("Skills", c.skills);
        addProp("Damage Vulnerabilities", c.vulnerabilities);
        addProp("Damage Resistances", c.resistances);
        addProp("Damage Immunities", c.immunities);
        addProp("Condition Immunities", c.conditionImmunities);
        addProp("Senses", c.senses);
        addProp("Languages", c.languages);
        getChildren().add(prop("Challenge", (c.challenge.isBlank() ? "0" : c.challenge) + " (" + c.xp + " XP)"));
        getChildren().add(prop("Initiative", Card.signed(c.initiative())));
        getChildren().add(rule());

        for (Feature f : c.traits) getChildren().add(feature(f));
        if (!c.attacks.isEmpty() || !c.actions.isEmpty()) {
            getChildren().add(sectionTitle("Actions"));
            for (Attack a : c.attacks) {
                getChildren().add(flow(styled(a.name + ". ", 12, FontWeight.BOLD, FontPosture.ITALIC, "#222"),
                        styled(a.summary(), 12, FontWeight.NORMAL, FontPosture.REGULAR, "#222")));
            }
            for (Feature f : c.actions) getChildren().add(feature(f));
        }
        if (!c.reactions.isEmpty()) {
            getChildren().add(sectionTitle("Reactions"));
            for (Feature f : c.reactions) getChildren().add(feature(f));
        }
        if (!c.legendary.isEmpty()) {
            getChildren().add(sectionTitle("Legendary Actions"));
            for (Feature f : c.legendary) getChildren().add(feature(f));
        }
        if (!c.notes.isBlank()) {
            getChildren().add(rule());
            getChildren().add(flow(styled("Notes. ", 12, FontWeight.BOLD, FontPosture.ITALIC, "#222"),
                    styled(c.notes.trim(), 12, FontWeight.NORMAL, FontPosture.REGULAR, "#222")));
        }
    }

    private static String subtitle(Card c) {
        StringBuilder sb = new StringBuilder();
        sb.append(c.size).append(' ').append(c.creatureType);
        if (c.alignment != null && !c.alignment.isBlank()) sb.append(", ").append(c.alignment);
        return sb.toString();
    }

    private Node portrait(Card c) {
        if (c.portrait == null) return null;
        Path p = store.imagePath(c.portrait);
        if (!Files.isRegularFile(p)) return null;
        ImageView iv = new ImageView(new Image(p.toUri().toString(), 72, 72, true, true));
        iv.setFitWidth(72);
        iv.setFitHeight(72);
        iv.setPreserveRatio(true);
        return iv;
    }

    private void addProp(String label, String value) {
        if (value != null && !value.isBlank()) getChildren().add(prop(label, value.trim()));
    }

    private static Text styled(String s, double size, FontWeight w, FontPosture p, String color) {
        Text t = new Text(s);
        t.setFont(Font.font(FONT, w, p, size));
        t.setFill(Color.web(color));
        return t;
    }

    private static TextFlow flow(Text... parts) {
        TextFlow tf = new TextFlow(parts);
        tf.setMaxWidth(390);
        return tf;
    }

    private static Node prop(String label, String value) {
        return flow(styled(label + " ", 12, FontWeight.BOLD, FontPosture.REGULAR, RED),
                styled(value, 12, FontWeight.NORMAL, FontPosture.REGULAR, "#222"));
    }

    private static Node feature(Feature f) {
        return flow(styled(f.name.isBlank() ? "" : f.name + ". ", 12, FontWeight.BOLD, FontPosture.ITALIC, "#222"),
                styled(f.text, 12, FontWeight.NORMAL, FontPosture.REGULAR, "#222"));
    }

    private static Node sectionTitle(String s) {
        VBox box = new VBox(2, new TextFlow(styled(s, 16, FontWeight.NORMAL, FontPosture.REGULAR, RED)), rule());
        box.setPadding(new Insets(6, 0, 2, 0));
        return box;
    }

    private static Region rule() {
        Region r = new Region();
        r.setPrefHeight(2);
        r.setMinHeight(2);
        r.setMaxHeight(2);
        r.setStyle("-fx-background-color: " + RED + ";");
        VBox.setMargin(r, new Insets(2, 0, 2, 0));
        return r;
    }

    /** Plain text of a list of attacks, used by the battle tracker too. */
    public static String attackLines(List<Attack> attacks) {
        StringBuilder sb = new StringBuilder();
        for (Attack a : attacks) sb.append(a.name).append(": ").append(a.summary()).append('\n');
        return sb.toString().trim();
    }
}
