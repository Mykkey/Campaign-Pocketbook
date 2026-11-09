package dnd.ui;

import dnd.model.MagicItem;
import javafx.geometry.Insets;
import javafx.scene.layout.VBox;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/** A read-only, parchment-style rendering of a magic item, with its rarity as the accent colour. */
public class MagicItemCardView extends VBox {
    public MagicItemCardView() {
        setSpacing(4);
        setPadding(new Insets(14));
        setMaxWidth(420);
    }

    public MagicItemCardView(MagicItem m) {
        this();
        refresh(m);
    }

    /** The accent colour for a rarity ("#RRGGBB"). */
    public static String rarityColor(String rarity) {
        String r = rarity == null ? "" : rarity.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (r) {
            case "common" -> "#6b7280";
            case "uncommon" -> "#15803d";
            case "rare" -> "#1d5fc4";
            case "very rare" -> "#7e3fc4";
            case "legendary" -> "#c26a0a";
            case "artifact" -> "#9a6b1c";
            default -> "#6b7280";
        };
    }

    public void refresh(MagicItem m) {
        getChildren().clear();
        String accent = rarityColor(m.rarity);
        setStyle("-fx-background-color: #fbf4e6; -fx-border-color: " + accent + "; -fx-border-width: 2;");
        getChildren().add(new TextFlow(StatBlockView.styled(m.name == null || m.name.isBlank() ? "(unnamed item)" : m.name,
                22, FontWeight.BOLD, FontPosture.REGULAR, accent)));
        getChildren().add(new TextFlow(StatBlockView.styled(m.subtitle(), 12, FontWeight.NORMAL, FontPosture.ITALIC, "#222")));
        getChildren().add(StatBlockView.rule());
        if (m.description != null) {
            for (String para : m.description.trim().split("\\n\\s*\\n")) {
                if (!para.isBlank()) getChildren().add(StatBlockView.flow(StatBlockView.styled(para.trim(), 12, FontWeight.NORMAL, FontPosture.REGULAR, "#222")));
            }
        }
        if (m.charges > 0) {
            String text = m.charges + (m.recharge == null || m.recharge.isBlank() ? "" : ", regained " + m.recharge.toLowerCase(java.util.Locale.ROOT));
            getChildren().add(StatBlockView.prop("Charges", text));
        }
        if (m.curse != null && !m.curse.isBlank()) {
            getChildren().add(StatBlockView.flow(StatBlockView.styled("Curse. ", 12, FontWeight.BOLD, FontPosture.ITALIC, "#8b1a1a"),
                    StatBlockView.styled(m.curse.trim(), 12, FontWeight.NORMAL, FontPosture.REGULAR, "#222")));
        }
        if (m.value != null && !m.value.isBlank()) {
            getChildren().add(StatBlockView.prop("Value", m.value.trim() + " gp"));
        }
        if (m.notes != null && !m.notes.isBlank()) {
            getChildren().add(StatBlockView.rule());
            getChildren().add(StatBlockView.flow(StatBlockView.styled("Notes. ", 12, FontWeight.BOLD, FontPosture.ITALIC, "#222"),
                    StatBlockView.styled(m.notes.trim(), 12, FontWeight.NORMAL, FontPosture.REGULAR, "#222")));
        }
    }
}
