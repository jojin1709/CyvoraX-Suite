package com.venomproxy.ui;

import com.venomproxy.db.Database;
import com.venomproxy.model.HttpTransaction;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class SiteMapTab extends Tab {
    private final Database database;
    private final ObservableList<HttpTransaction> history;
    private final TreeView<String> tree = new TreeView<>();
    private final ObservableList<EndpointMetadata> endpointRows = FXCollections.observableArrayList();
    private final FilteredList<EndpointMetadata> filteredRows = new FilteredList<>(endpointRows);
    private final TextField filter = new TextField();
    private final TextArea requestViewer = UiUtil.codeArea("Request");
    private final TextArea responseViewer = UiUtil.codeArea("Response");
    private final HttpInspectorPane inspector = new HttpInspectorPane();
    private final Label status = new Label("Ready");

    public SiteMapTab(Database database, ObservableList<HttpTransaction> history) {
        super("Site Map");
        setClosable(false);
        this.database = database;
        this.history = history;
        filter.setPromptText("Search host, path, method, status, technologies");
        ComboBox<String> highlightFilter = new ComboBox<>();
        highlightFilter.getItems().add("Any Highlight");
        highlightFilter.getItems().addAll(RequestAnnotationActions.HIGHLIGHT_COLORS);
        highlightFilter.getSelectionModel().select("Any Highlight");

        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> rebuild());
        Button expand = new Button("Expand");
        expand.setOnAction(event -> setExpanded(tree.getRoot(), true));
        Button collapse = new Button("Collapse");
        collapse.setOnAction(event -> setExpanded(tree.getRoot(), false));
        filter.textProperty().addListener((obs, old, value) -> applyFilter());
        highlightFilter.valueProperty().addListener((obs, old, value) -> applyFilter());

        TableView<EndpointMetadata> table = new TableView<>(filteredRows);
        UiUtil.constrainTable(table);
        table.setPlaceholder(UiUtil.emptyState("No endpoints mapped", "Captured traffic and crawler results will build the target site map automatically.", "Refresh", this::rebuild));
        table.getColumns().add(column("Note", "noteIndicator", 70));
        table.getColumns().add(column("Highlight", "highlightColors", 110));
        table.getColumns().add(column("Host", "host", 220));
        table.getColumns().add(column("Path", "path", 320));
        table.getColumns().add(column("Methods", "methods", 120));
        table.getColumns().add(column("Statuses", "statuses", 120));
        table.getColumns().add(column("Params", "parameters", 180));
        table.getColumns().add(column("Tech", "technologies", 200));
        table.getColumns().add(column("Avg Size", "averageSize", 90));
        table.getColumns().add(column("Count", "count", 80));
        table.setRowFactory(view -> new TableRow<>() {
            @Override
            protected void updateItem(EndpointMetadata row, boolean empty) {
                super.updateItem(row, empty);
                RequestAnnotationActions.applyHighlightStyle(this, empty || row == null ? "" : row.primaryHighlight());
            }
        });
        Menu highlight = RequestAnnotationActions.highlightMenu(() -> selectedTransaction(table), database, () -> {
            rebuild();
            table.refresh();
        });
        table.setContextMenu(new ContextMenu(
                RequestAnnotationActions.addNote(() -> selectedTransaction(table), database, () -> {
                    rebuild();
                    table.refresh();
                }),
                RequestAnnotationActions.editNote(() -> selectedTransaction(table), database, () -> {
                    rebuild();
                    table.refresh();
                }),
                RequestAnnotationActions.deleteNote(() -> selectedTransaction(table), database, () -> {
                    rebuild();
                    table.refresh();
                }),
                highlight));
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                showTransaction(selected.representative());
            }
        });
        tree.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> showTransaction(transactionForTree(selected)));

        HBox controls = new HBox(8, new Label("Filter"), filter, new Label("Highlight"), highlightFilter, refresh, expand, collapse, status);
        HBox.setHgrow(filter, Priority.ALWAYS);
        javafx.scene.control.SplitPane requestResponse = new javafx.scene.control.SplitPane(requestViewer, responseViewer);
        requestResponse.setDividerPositions(0.5);
        UiUtil.bindDividerPositions(database, "layout.target.requestResponse", requestResponse, 0.5);
        javafx.scene.control.SplitPane evidence = new javafx.scene.control.SplitPane(requestResponse, inspector);
        evidence.setDividerPositions(0.74);
        UiUtil.bindDividerPositions(database, "layout.target.inspector", evidence, 0.74);
        javafx.scene.control.SplitPane tableAndEvidence = new javafx.scene.control.SplitPane(table, evidence);
        tableAndEvidence.setOrientation(javafx.geometry.Orientation.VERTICAL);
        tableAndEvidence.setDividerPositions(0.58);
        UiUtil.bindDividerPositions(database, "layout.target.detail", tableAndEvidence, 0.58);
        javafx.scene.control.SplitPane split = new javafx.scene.control.SplitPane(tree, tableAndEvidence);
        split.setDividerPositions(0.34);
        UiUtil.bindDividerPositions(database, "layout.target.main", split, 0.34);
        VBox root = new VBox(10, controls, split);
        VBox.setVgrow(split, Priority.ALWAYS);
        root.setPadding(new Insets(12));
        setContent(root);
        this.highlightFilter = highlightFilter;
        rebuild();
        Platform.runLater(() -> {
            if (!filteredRows.isEmpty()) {
                table.getSelectionModel().select(0);
            }
        });
    }

    private ComboBox<String> highlightFilter;

    public void rebuild() {
        TreeItem<String> root = new TreeItem<>("Targets");
        root.setExpanded(true);
        Map<String, TreeItem<String>> hosts = new TreeMap<>();
        Map<String, EndpointAccumulator> endpoints = new LinkedHashMap<>();

        for (HttpTransaction tx : history) {
            String host = tx.getHost() == null || tx.getHost().isBlank() ? "(unknown)" : tx.getHost();
            TreeItem<String> hostItem = hosts.computeIfAbsent(host, key -> {
                TreeItem<String> item = new TreeItem<>(key);
                root.getChildren().add(item);
                return item;
            });
            String path = tx.getPath() == null || tx.getPath().isBlank() ? "/" : tx.getPath();
            addPath(hostItem, path);
            endpoints.computeIfAbsent(host + "\n" + path, key -> new EndpointAccumulator(host, path)).add(tx);
        }

        tree.setRoot(root);
        endpointRows.setAll(endpoints.values().stream().map(EndpointAccumulator::toMetadata).toList());
        applyFilter();
        status.setText("Endpoints: " + endpointRows.size());
    }

    private void addPath(TreeItem<String> hostItem, String path) {
        String clean = path.startsWith("/") ? path.substring(1) : path;
        TreeItem<String> current = hostItem;
        if (clean.isBlank()) {
            addChild(current, "/");
            return;
        }
        for (String part : clean.split("/")) {
            if (!part.isBlank()) {
                current = addChild(current, part);
            }
        }
    }

    private TreeItem<String> addChild(TreeItem<String> parent, String value) {
        for (TreeItem<String> child : parent.getChildren()) {
            if (child.getValue().equals(value)) {
                return child;
            }
        }
        TreeItem<String> child = new TreeItem<>(value);
        parent.getChildren().add(child);
        return child;
    }

    private void setExpanded(TreeItem<String> item, boolean expanded) {
        if (item == null) {
            return;
        }
        item.setExpanded(expanded);
        for (TreeItem<String> child : item.getChildren()) {
            setExpanded(child, expanded);
        }
    }

    private void showTransaction(HttpTransaction tx) {
        if (tx == null) {
            requestViewer.clear();
            responseViewer.clear();
            inspector.inspect("", "", "");
            return;
        }
        requestViewer.setText(tx.getRequestRaw());
        responseViewer.setText(tx.getResponseRaw());
        inspector.inspect(tx.getRequestRaw(), tx.getResponseRaw(), tx.getNotes());
        status.setText("Selected #" + tx.getId() + " " + tx.getMethod() + " " + tx.getHost());
    }

    private HttpTransaction transactionForTree(TreeItem<String> item) {
        if (item == null || item.getParent() == null) {
            return null;
        }
        TreeItem<String> cursor = item;
        java.util.LinkedList<String> parts = new java.util.LinkedList<>();
        while (cursor.getParent() != null && cursor.getParent().getParent() != null) {
            parts.addFirst(cursor.getValue());
            cursor = cursor.getParent();
        }
        String host = cursor.getValue();
        if (host == null || "Targets".equals(host)) {
            return null;
        }
        String path = parts.isEmpty() ? "" : "/" + String.join("/", parts);
        for (HttpTransaction tx : history) {
            if (!host.equals(tx.getHost())) {
                continue;
            }
            if (path.isBlank() || path.equals(tx.getPath()) || tx.getPath().startsWith(path + "?")) {
                return tx;
            }
        }
        return null;
    }

    private void applyFilter() {
        String query = filter.getText() == null ? "" : filter.getText().trim().toLowerCase(Locale.ROOT);
        String color = highlightFilter == null ? "Any Highlight" : highlightFilter.getSelectionModel().getSelectedItem();
        filteredRows.setPredicate(row -> (query.isBlank()
                || row.getHost().toLowerCase(Locale.ROOT).contains(query)
                || row.getPath().toLowerCase(Locale.ROOT).contains(query)
                || row.getMethods().toLowerCase(Locale.ROOT).contains(query)
                || row.getStatuses().toLowerCase(Locale.ROOT).contains(query)
                || row.getParameters().toLowerCase(Locale.ROOT).contains(query)
                || row.getTechnologies().toLowerCase(Locale.ROOT).contains(query)
                || row.getHighlightColors().toLowerCase(Locale.ROOT).contains(query))
                && (color == null || color.equals("Any Highlight") || row.getHighlightColors().contains(color)));
    }

    private TableColumn<EndpointMetadata, Object> column(String title, String property, int width) {
        TableColumn<EndpointMetadata, Object> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(width);
        UiUtil.addTooltipCellFactory(column);
        return column;
    }

    private static class EndpointAccumulator {
        private final String host;
        private final String path;
        private final Set<String> methods = new java.util.TreeSet<>();
        private final Set<Integer> statuses = new java.util.TreeSet<>();
        private final Set<String> parameters = new java.util.TreeSet<>();
        private final Set<String> technologies = new java.util.TreeSet<>();
        private final Set<String> highlights = new java.util.TreeSet<>();
        private boolean hasNotes;
        private HttpTransaction representative;
        private int count;
        private long totalSize;

        EndpointAccumulator(String host, String path) {
            this.host = host;
            this.path = path;
        }

        void add(HttpTransaction tx) {
            count++;
            if (representative == null) {
                representative = tx;
            }
            totalSize += tx.getLength();
            methods.add(tx.getMethod());
            statuses.add(tx.getStatus());
            if (!tx.getNotes().isBlank()) {
                hasNotes = true;
            }
            String highlight = RequestAnnotationActions.normalizeColor(tx.getColorLabel());
            if (!highlight.isBlank()) {
                highlights.add(highlight);
            }
            try {
                String query = URI.create(tx.getUrl()).getRawQuery();
                if (query != null) {
                    for (String pair : query.split("&")) {
                        int eq = pair.indexOf('=');
                        parameters.add(eq >= 0 ? pair.substring(0, eq) : pair);
                    }
                }
            } catch (RuntimeException ignored) {
            }
            technologies.addAll(detectTechnologies(tx));
        }

        EndpointMetadata toMetadata() {
            return new EndpointMetadata(host, path,
                    String.join(",", methods),
                    statuses.stream().map(String::valueOf).collect(Collectors.joining(",")),
                    String.join(",", parameters),
                    String.join(",", technologies),
                    count == 0 ? 0 : totalSize / count,
                    count,
                    hasNotes ? "Note" : "",
                    String.join(",", highlights),
                    representative);
        }

        private Set<String> detectTechnologies(HttpTransaction tx) {
            Map<String, String> headers = parseHeaders(tx.getResponseRaw());
            Set<String> found = new java.util.TreeSet<>();
            addIfPresent(found, headers, "server", "Server");
            addIfPresent(found, headers, "x-powered-by", "PoweredBy");
            String body = tx.getResponseRaw() == null ? "" : tx.getResponseRaw().toLowerCase(Locale.ROOT);
            if (body.contains("wp-content")) {
                found.add("WordPress");
            }
            if (body.contains("react") || body.contains("__next")) {
                found.add("React/Next");
            }
            return found;
        }

        private void addIfPresent(Set<String> found, Map<String, String> headers, String key, String label) {
            String value = headers.get(key);
            if (value != null && !value.isBlank()) {
                found.add(label + ":" + value);
            }
        }

        private Map<String, String> parseHeaders(String raw) {
            Map<String, String> headers = new HashMap<>();
            if (raw == null) {
                return headers;
            }
            for (String line : raw.replace("\r\n", "\n").split("\n")) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
                }
                if (line.isBlank()) {
                    break;
                }
            }
            return headers;
        }
    }

    public static class EndpointMetadata {
        private final String host;
        private final String path;
        private final String methods;
        private final String statuses;
        private final String parameters;
        private final String technologies;
        private final long averageSize;
        private final int count;
        private final String noteIndicator;
        private final String highlightColors;
        private final HttpTransaction representative;

        EndpointMetadata(String host, String path, String methods, String statuses, String parameters,
                         String technologies, long averageSize, int count, String noteIndicator,
                         String highlightColors, HttpTransaction representative) {
            this.host = host;
            this.path = path;
            this.methods = methods;
            this.statuses = statuses;
            this.parameters = parameters;
            this.technologies = technologies;
            this.averageSize = averageSize;
            this.count = count;
            this.noteIndicator = noteIndicator;
            this.highlightColors = highlightColors;
            this.representative = representative;
        }

        public String getHost() { return host; }
        public String getPath() { return path; }
        public String getMethods() { return methods; }
        public String getStatuses() { return statuses; }
        public String getParameters() { return parameters; }
        public String getTechnologies() { return technologies; }
        public long getAverageSize() { return averageSize; }
        public int getCount() { return count; }
        public String getNoteIndicator() { return noteIndicator; }
        public String getHighlightColors() { return highlightColors; }
        public HttpTransaction representative() { return representative; }
        public String primaryHighlight() {
            if (highlightColors == null || highlightColors.isBlank()) {
                return "";
            }
            int comma = highlightColors.indexOf(',');
            return comma >= 0 ? highlightColors.substring(0, comma) : highlightColors;
        }
    }

    private HttpTransaction selectedTransaction(TableView<EndpointMetadata> table) {
        EndpointMetadata selected = table.getSelectionModel().getSelectedItem();
        return selected == null ? null : selected.representative();
    }
}
