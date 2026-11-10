package dnd.ui;

import dnd.export.MarkdownExport;
import dnd.export.PrintMath;
import dnd.library.CommonEnemies;
import dnd.model.Card;
import dnd.model.TreeNode;
import dnd.storage.ProjectStore;
import javafx.geometry.Insets;
import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Exporting and printing the text items of a campaign: stat blocks, spells, magic items, quests, sessions and encounters. */
final class ItemExport {

    private ItemExport() { }

    /** True for the kinds of item that can be exported as Markdown and printed. */
    static boolean isTextItem(TreeNode n) {
        return n != null && !n.folder && (n.isCard() || n.isSpell() || n.isMagicItem() || n.isQuest() || n.isSession() || n.isEncounter());
    }

    /** The item as Markdown, or null if it cannot be loaded. */
    static String markdownOf(ProjectStore store, TreeNode n) {
        if (n.isCard()) {
            Card c = store.card(n.id);
            return c == null ? null : MarkdownExport.card(c);
        }
        if (n.isSpell()) {
            dnd.model.Spell s = store.spell(n.id);
            return s == null ? null : MarkdownExport.spell(s);
        }
        if (n.isMagicItem()) {
            dnd.model.MagicItem m = store.magicItem(n.id);
            return m == null ? null : MarkdownExport.magicItem(m);
        }
        if (n.isQuest()) {
            dnd.model.Quest q = store.quest(n.id);
            return q == null ? null : MarkdownExport.quest(q, store::itemName);
        }
        if (n.isSession()) {
            dnd.model.Session s = store.session(n.id);
            return s == null ? null : MarkdownExport.session(s, store::itemName);
        }
        if (n.isEncounter()) {
            dnd.model.Encounter e = store.encounter(n.id);
            if (e == null) return null;
            return MarkdownExport.encounter(e, en -> {
                Card c = en.libraryId != null ? CommonEnemies.shared().get(en.libraryId) : en.cardId == null ? null : store.card(en.cardId);
                return c == null ? null : c.name;
            });
        }
        return null;
    }

    /** Asks where to save and writes the item as a Markdown file. */
    static void saveMarkdown(Window owner, ProjectStore store, TreeNode n) {
        String md = markdownOf(store, n);
        if (md == null) {
            new Alert(Alert.AlertType.ERROR, "Could not load \"" + n.name + "\".").show();
            return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle("Export as Markdown");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Markdown", "*.md"));
        fc.setInitialFileName(MarkdownExport.fileName(n.name, ".md"));
        String dir = AppSettings.getString("export.item.dir", null);
        if (dir != null && new File(dir).isDirectory()) fc.setInitialDirectory(new File(dir));
        File f = fc.showSaveDialog(owner);
        if (f == null) return;
        if (!f.getName().toLowerCase().endsWith(".md")) f = new File(f.getParentFile(), f.getName() + ".md");
        try {
            Files.writeString(f.toPath(), md, StandardCharsets.UTF_8);
            if (f.getParentFile() != null) AppSettings.setString("export.item.dir", f.getParentFile().getAbsolutePath());
        } catch (IOException | RuntimeException ex) {
            new Alert(Alert.AlertType.ERROR, "Could not save the file: " + ex.getMessage()).show();
        }
    }

    // ================================================================ printing

    /** The picture of an item as it is printed: always on white paper in the light colours, whatever mode the app is in. */
    static Node printable(ProjectStore store, TreeNode n, double width) {
        Node content = UiScale.withFactor(1.0, () -> ItemPreview.content(store, n.id));
        StackPane page = new StackPane(content);
        StackPane.setAlignment(content, javafx.geometry.Pos.TOP_LEFT);
        page.setPadding(new Insets(0));
        page.setStyle("-fx-background-color: white;");
        page.setPrefWidth(width);
        page.setMinWidth(width);
        page.setMaxWidth(width);
        Scene scene = new Scene(new Group(page));
        scene.getStylesheets().add(Theme.class.getResource("/theme-light.css").toExternalForm());
        page.applyCss();
        page.autosize();
        page.layout();
        return page;
    }

    /**
     * Cuts printable content into page-sized nodes (each already scaled to the printable width), ready to be given to a printer
     * or taken as pictures.
     */
    static List<Node> pages(Node page, double printableWidth, double printableHeight) {
        double w = Math.max(1, page.getLayoutBounds().getWidth()), h = Math.max(1, page.getLayoutBounds().getHeight());
        double scale = PrintMath.scaleFor(w, printableWidth, 1.6);
        double span = PrintMath.pageSpan(printableHeight, scale);
        int count = PrintMath.pageCount(h, span);
        List<Node> out = new ArrayList<>();
        // the content can only be in one page's window at a time: the caller calls show() on a slice just before printing it
        for (int i = 0; i < count; i++) out.add(new PageSlice(page, i, w, span, scale));
        return out;
    }

    /** One page of a long item: a window onto the content, which is moved when the page is asked for. */
    static final class PageSlice extends Pane {
        private final Node content;
        private final int index;
        private final double span;

        PageSlice(Node content, int index, double width, double span, double scale) {
            this.content = content;
            this.index = index;
            this.span = span;
            setPrefSize(width, span);
            setMinSize(width, span);
            setMaxSize(width, span);
            setClip(new Rectangle(width, span));
            getTransforms().add(new Scale(scale, scale));
        }

        /** Puts the content into this page's window (a node can only be in one place at a time). */
        PageSlice show() {
            getChildren().setAll(content);
            content.setLayoutY(-index * span);
            return this;
        }
    }

    /** Opens the print dialog and prints the item, as many pages as it needs. */
    static void print(Window owner, ProjectStore store, TreeNode n) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            new Alert(Alert.AlertType.ERROR, "No printer was found. Install a printer (or a PDF printer such as \"Microsoft Print to PDF\") and try again.").show();
            return;
        }
        try {
            if (!job.showPrintDialog(owner)) return;
            PageLayout layout = job.getJobSettings().getPageLayout();
            double pw = layout.getPrintableWidth(), ph = layout.getPrintableHeight();
            Node page = printable(store, n, 460);
            boolean ok = true;
            for (Node slice : pages(page, pw, ph)) {
                ok = job.printPage(layout, ((PageSlice) slice).show());
                if (!ok) break;
            }
            if (ok) job.endJob();
            else {
                job.cancelJob();
                new Alert(Alert.AlertType.ERROR, "Printing did not finish.").show();
            }
        } catch (RuntimeException ex) {
            job.cancelJob();
            new Alert(Alert.AlertType.ERROR, "Could not print: " + ex.getMessage()).show();
        }
    }
}
