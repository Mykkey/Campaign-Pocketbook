package dnd.ui;

import dnd.model.Spell;
import javafx.geometry.Insets;
import javafx.scene.layout.VBox;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/** A read-only, parchment-style rendering of a spell, like a printed spell card. */
public class SpellCardView extends VBox {
    public SpellCardView() {
        setSpacing(4);
        setPadding(new Insets(14));
        setStyle("-fx-background-color: #f3ecfb; -fx-border-color: #c9b6e4; -fx-border-width: 1;");
        setMaxWidth(420);
    }

    public SpellCardView(Spell s) {
        this();
        refresh(s);
    }

    public void refresh(Spell s) {
        getChildren().clear();
        String purple = "#4b2a85";
        Text name = StatBlockView.styled(s.name == null || s.name.isBlank() ? "(unnamed spell)" : s.name, 22, FontWeight.BOLD, FontPosture.REGULAR, purple);
        getChildren().add(new TextFlow(name));
        getChildren().add(new TextFlow(StatBlockView.styled(subtitle(s), 12, FontWeight.NORMAL, FontPosture.ITALIC, "#222")));
        getChildren().add(StatBlockView.rule());
        prop("Casting Time", s.castingTime);
        prop("Range", s.range);
        String comps = s.components == null ? "" : s.components.trim();
        if (s.material != null && !s.material.isBlank()) comps = (comps.isBlank() ? "M" : comps) + " (" + s.material.trim().replaceAll("\\.$", "") + ")";
        prop("Components", comps);
        String duration = s.duration == null ? "" : s.duration.trim();
        if (s.concentration && !duration.toLowerCase().contains("concentration")) duration = "Concentration, " + duration;
        prop("Duration", duration);
        getChildren().add(StatBlockView.rule());
        if (s.description != null) {
            for (String para : s.description.trim().split("\\n\\s*\\n")) {
                if (!para.isBlank()) getChildren().add(StatBlockView.flow(StatBlockView.styled(para.trim(), 12, FontWeight.NORMAL, FontPosture.REGULAR, "#222")));
            }
        }
        if (s.higherLevel != null && !s.higherLevel.isBlank()) {
            getChildren().add(StatBlockView.flow(StatBlockView.styled("At Higher Levels. ", 12, FontWeight.BOLD, FontPosture.ITALIC, "#222"),
                    StatBlockView.styled(s.higherLevel.trim(), 12, FontWeight.NORMAL, FontPosture.REGULAR, "#222")));
        }
        if (!s.classes.isEmpty()) {
            getChildren().add(StatBlockView.rule());
            getChildren().add(StatBlockView.flow(StatBlockView.styled("Classes. ", 12, FontWeight.BOLD, FontPosture.ITALIC, "#222"),
                    StatBlockView.styled(String.join(", ", s.classes), 12, FontWeight.NORMAL, FontPosture.REGULAR, "#222")));
        }
        if (s.notes != null && !s.notes.isBlank()) {
            getChildren().add(StatBlockView.flow(StatBlockView.styled("Notes. ", 12, FontWeight.BOLD, FontPosture.ITALIC, "#222"),
                    StatBlockView.styled(s.notes.trim(), 12, FontWeight.NORMAL, FontPosture.REGULAR, "#222")));
        }
    }

    private void prop(String label, String value) {
        if (value == null || value.isBlank()) return;
        getChildren().add(StatBlockView.prop(label, value.trim()));
    }

    /** "3rd-level evocation (ritual)" or "Evocation cantrip". */
    static String subtitle(Spell s) {
        String school = s.school == null ? "" : s.school;
        String text = s.level <= 0 ? (school.isBlank() ? "Cantrip" : school + " cantrip") : s.levelLabel() + (school.isBlank() ? "" : " " + school.toLowerCase());
        if (s.ritual) text += " (ritual)";
        return text;
    }
}