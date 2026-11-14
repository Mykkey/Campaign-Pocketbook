package dnd.ui;

import dnd.help.HelpBook;
import dnd.help.MarkdownBlocks;
import javafx.application.HostServices;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The help book: the user guide as a searchable book. A list of chapters and topics on the left (or search results while
 * something is typed in the box), and the page on the right. Press F1 anywhere to open it.
 */
public final class HelpDialog {
    private HelpDialog() { }

    private static Stage open;
    private static TextField openSearch;
    private static HelpBook book;

    /** The guide, read once from the copy packaged with the app; null if it is missing. */
    private static HelpBook book() {
        if (book == null) {
            try (InputStream in = HelpDialog.class.getResourceAsStream("/help/GUIDE.md")) {
                if (in != null) book = HelpBook.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                dnd.util.Log.error("Could not read the help book", e);
            }
        }
        return book;
    }

    public static void show(Window owner, HostServices hostServices) {
        if (open != null && open.isShowing()) {
            open.toFront();
            open.requestFocus();
            if (openSearch != null) {
                openSearch.requestFocus();
                openSearch.selectAll();
            }
            return;
        }
        HelpBook help = book();
        if (help == null || help.topics().isEmpty()) {
            javafx.scene.control.Alert a = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION,
                    "The help book could not be loaded. The same text is in docs/GUIDE.md, and online in the project's repository.");
            a.setHeaderText("Help");
            a.show();
            return;
        }

        TextField search = new TextField();
        search.setPromptText("Search the guide...  (words, a feature, a question)");
        Label count = new Label();
        count.setStyle("-fx-text-fill: -t-text3; -fx-font-size: 0.917em;");
        ListView<Object> list = new ListView<>();
        VBox.setVgrow(list, Priority.ALWAYS);
        VBox left = new VBox(6, search, count, list);
        left.setPadding(new Insets(8));
        left.setMinWidth(200);

        VBox page = new VBox(10);
        page.setPadding(new Insets(16, 20, 24, 20));
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: -t-surface;");

        List<String> highlight = new ArrayList<>();
        Runnable[] showSelected = new Runnable[1];
        showSelected[0] = () -> {
            Object sel = list.getSelectionModel().getSelectedItem();
            HelpBook.Topic t = sel instanceof HelpBook.Hit h ? h.topic() : sel instanceof HelpBook.Topic tt ? tt : null;
            if (t == null) return;
            render(page, help, t, highlight, hostServices);
            scroll.setVvalue(0);
        };
        list.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> showSelected[0].run());

        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(null);
                setText(null);
                if (empty || item == null) return;
                if (item instanceof HelpBook.Topic t) {
                    setText(t.title());
                    setStyle(t.level() == 2 ? "-fx-font-weight: bold; -fx-padding: 5 6 5 6;" : "-fx-padding: 3 6 3 22;");
                } else if (item instanceof HelpBook.Hit h) {
                    Label title = new Label(h.topic().level() == 3 ? h.topic().chapter() + "  >  " + h.topic().title() : h.topic().title());
                    title.setStyle("-fx-font-weight: bold;");
                    Label snip = new Label(h.snippet());
                    snip.setWrapText(true);
                    snip.setStyle("-fx-text-fill: -t-text2; -fx-font-size: 0.917em;");
                    snip.setMaxWidth(260);
                    VBox box = new VBox(2, title, snip);
                    setGraphic(box);
                    setStyle("-fx-padding: 5 6 5 6;");
                }
            }
        });

        Runnable showContents = () -> {
            highlight.clear();
            count.setText(help.topics().size() + " topics");
            list.setItems(FXCollections.observableArrayList(help.topics()));
        };
        search.textProperty().addListener((o, a, query) -> {
            List<String> words = HelpBook.words(query);
            if (words.isEmpty()) {
                showContents.run();
                list.getSelectionModel().selectFirst();
                return;
            }
            List<HelpBook.Hit> hits = help.search(query);
            highlight.clear();
            highlight.addAll(words);
            count.setText(hits.isEmpty() ? "Nothing found. Try fewer or different words." : hits.size() + (hits.size() == 1 ? " result" : " results"));
            list.setItems(FXCollections.observableArrayList(hits.subList(0, Math.min(hits.size(), 80))));
            if (!hits.isEmpty()) list.getSelectionModel().selectFirst();
            else page.getChildren().clear();
        });

        SplitPane split = new SplitPane(left, scroll);
        Panels.remember(split, "dialog.help", 0.3);
        Stage stage = new Stage();
        stage.setTitle("Help: the guide");
        if (owner != null) stage.initOwner(owner);
        Scene scene = new Scene(split, UiScale.px(1040), UiScale.px(720));
        scene.setOnKeyPressed(e -> {
            if (dnd.keys.Shortcuts.matches(e, "dialog.close")) stage.close();
        });
        stage.setScene(scene);
        stage.setOnHidden(e -> {
            open = null;
            openSearch = null;
        });
        open = stage;
        openSearch = search;
        showContents.run();
        list.getSelectionModel().selectFirst();
        stage.show();
        search.requestFocus();
    }

    // ---------------------------------------------------------------- drawing a page

    private static void render(VBox page, HelpBook help, HelpBook.Topic topic, List<String> highlight, HostServices hostServices) {
        page.getChildren().clear();
        for (HelpBook.Topic t : help.pageFor(topic)) {
            Label heading = new Label(t.title());
            heading.setWrapText(true);
            heading.setStyle(t.level() == 2 ? "-fx-font-size: 1.9em; -fx-font-weight: bold; -fx-text-fill: -t-heading;"
                    : "-fx-font-size: 1.35em; -fx-font-weight: bold; -fx-text-fill: -t-heading; -fx-padding: 12 0 0 0;");
            page.getChildren().add(heading);
            for (MarkdownBlocks.Block b : MarkdownBlocks.parse(t.body())) page.getChildren().add(block(b, highlight, hostServices));
        }
    }

    private static Node block(MarkdownBlocks.Block b, List<String> highlight, HostServices hostServices) {
        switch (b.type()) {
            case HEADING: {
                TextFlow f = flow(b.text(), highlight, hostServices, b.level() <= 4 ? 1.15 : 1.05, true);
                f.setPadding(new Insets(6, 0, 0, 0));
                return f;
            }
            case BULLET:
            case NUMBERED: {
                Label mark = new Label(b.type() == MarkdownBlocks.Type.BULLET ? "•" : b.number() + ".");
                mark.setMinWidth(22);
                mark.setAlignment(Pos.TOP_RIGHT);
                mark.setStyle("-fx-text-fill: -t-text2;");
                TextFlow f = flow(b.text(), highlight, hostServices, 1.0, false);
                HBox.setHgrow(f, Priority.ALWAYS);
                HBox row = new HBox(8, mark, f);
                row.setPadding(new Insets(0, 0, 0, 6 + b.level() * 22));
                return row;
            }
            case TABLE:
                return table(b, highlight, hostServices);
            case CODE: {
                Label code = new Label(b.text());
                code.setStyle("-fx-font-family: monospace; -fx-background-color: -t-surface2; -fx-padding: 8; -fx-text-fill: -t-text;");
                code.setMaxWidth(Double.MAX_VALUE);
                return code;
            }
            case RULE: {
                javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
                return sep;
            }
            default:
                return flow(b.text(), highlight, hostServices, 1.0, false);
        }
    }

    private static Node table(MarkdownBlocks.Block b, List<String> highlight, HostServices hostServices) {
        GridPane grid = new GridPane();
        grid.setHgap(0);
        grid.setVgap(0);
        grid.setStyle("-fx-border-color: -t-border; -fx-border-width: 1;");
        int cols = 0;
        for (List<String> row : b.rows()) cols = Math.max(cols, row.size());
        for (int c = 0; c < cols; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(cols == 2 ? (c == 0 ? 32 : 68) : 100.0 / cols);
            grid.getColumnConstraints().add(cc);
        }
        for (int r = 0; r < b.rows().size(); r++) {
            List<String> row = b.rows().get(r);
            for (int c = 0; c < cols; c++) {
                String cell = c < row.size() ? row.get(c) : "";
                TextFlow f = flow(cell, highlight, hostServices, 1.0, r == 0);
                VBox box = new VBox(f);
                box.setPadding(new Insets(5, 8, 5, 8));
                box.setStyle("-fx-border-color: -t-border; -fx-border-width: 0 0 1 0;" + (r == 0 ? " -fx-background-color: -t-surface2;" : ""));
                grid.add(box, c, r);
            }
        }
        return grid;
    }

    private static TextFlow flow(String markdown, List<String> highlight, HostServices hostServices, double sizeEm, boolean allBold) {
        TextFlow flow = new TextFlow();
        flow.setLineSpacing(2);
        Pattern marks = highlight.isEmpty() ? null : Pattern.compile("(?i)(" + String.join("|", highlight.stream().map(Pattern::quote).toList()) + ")");
        for (MarkdownBlocks.Span span : MarkdownBlocks.inline(markdown)) {
            boolean bold = span.bold() || allBold;
            if (span.url() != null && span.url().startsWith("http")) {
                Hyperlink link = new Hyperlink(span.text());
                link.setOnAction(e -> {
                    if (hostServices != null) hostServices.showDocument(span.url());
                });
                link.setPadding(Insets.EMPTY);
                flow.getChildren().add(link);
                continue;
            }
            addText(flow, span, bold, sizeEm, marks);
        }
        return flow;
    }

    private static void addText(TextFlow flow, MarkdownBlocks.Span span, boolean bold, double sizeEm, Pattern marks) {
        String text = span.text();
        if (marks == null) {
            flow.getChildren().add(text(text, span, bold, sizeEm, false));
            return;
        }
        Matcher m = marks.matcher(text);
        int at = 0;
        while (m.find()) {
            if (m.start() > at) flow.getChildren().add(text(text.substring(at, m.start()), span, bold, sizeEm, false));
            flow.getChildren().add(text(m.group(), span, bold, sizeEm, true));
            at = m.end();
        }
        if (at < text.length()) flow.getChildren().add(text(text.substring(at), span, bold, sizeEm, false));
    }

    private static Text text(String s, MarkdownBlocks.Span span, boolean bold, double sizeEm, boolean found) {
        Text t = new Text(s);
        double size = UiScale.px(13) * sizeEm;
        if (span.code()) {
            t.setFont(Font.font("Consolas", bold || found ? FontWeight.BOLD : FontWeight.NORMAL, size * 0.95));
            t.setStyle("-fx-fill: " + (found ? "#d9731a" : "-t-blue-text") + ";");
        } else {
            t.setFont(Font.font(Font.getDefault().getFamily(), bold || found ? FontWeight.BOLD : FontWeight.NORMAL,
                    span.italic() ? FontPosture.ITALIC : FontPosture.REGULAR, size));
            t.setStyle("-fx-fill: " + (found ? "#d9731a" : "-t-text") + ";");
        }
        return t;
    }
}
