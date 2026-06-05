package com.venomproxy.ui;

import com.venomproxy.model.HttpTransaction;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
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
    private final ObservableList<HttpTransaction> history;
    private final TreeView<String> tree = new TreeView<>();
    private final ObservableList<EndpointMetadata> endpointRows = FXCollections.observableArrayList();
    private final FilteredList<EndpointMetadata> filteredRows = new FilteredList<>(endpointRows);
    private final TextField filter = new TextField();

    public SiteMapTab(ObservableList<HttpTransaction> history) {
        super("Site Map");
        setClosable(false);
        this.history = history;
        filter.setPromptText("Search host, path, method, status, technologies");

        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> rebuild());
        Button expand = new Button("Expand");
        expand.setOnAction(event -> setExpanded(tree.getRoot(), true));
        Button collapse = new Button("Collapse");
        collapse.setOnAction(event -> setExpanded(tree.getRoot(), false));
        filter.textProperty().addListener((obs, old, value) -> applyFilter());

        TableView<EndpointMetadata> table = new TableView<>(filteredRows);
        table.getColumns().add(column("Host", "host", 220));
        table.getColumns().add(column("Path", "path", 320));
        table.getColumns().add(column("Methods", "methods", 120));
        table.getColumns().add(column("Statuses", "statuses", 120));
        table.getColumns().add(column("Params", "parameters", 180));
        table.getColumns().add(column("Tech", "technologies", 200));
        table.getColumns().add(column("Avg Size", "averageSize", 90));
        table.getColumns().add(column("Count", "count", 80));

        HBox controls = new HBox(8, new Label("Filter"), filter, refresh, expand, collapse);
        HBox.setHgrow(filter, Priority.ALWAYS);
        javafx.scene.control.SplitPane split = new javafx.scene.control.SplitPane(tree, table);
        split.setDividerPositions(0.34);
        VBox root = new VBox(10, controls, split);
        VBox.setVgrow(split, Priority.ALWAYS);
        root.setPadding(new Insets(12));
        setContent(root);
        rebuild();
    }

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

    private void applyFilter() {
        String query = filter.getText() == null ? "" : filter.getText().trim().toLowerCase(Locale.ROOT);
        filteredRows.setPredicate(row -> query.isBlank()
                || row.getHost().toLowerCase(Locale.ROOT).contains(query)
                || row.getPath().toLowerCase(Locale.ROOT).contains(query)
                || row.getMethods().toLowerCase(Locale.ROOT).contains(query)
                || row.getStatuses().toLowerCase(Locale.ROOT).contains(query)
                || row.getParameters().toLowerCase(Locale.ROOT).contains(query)
                || row.getTechnologies().toLowerCase(Locale.ROOT).contains(query));
    }

    private TableColumn<EndpointMetadata, Object> column(String title, String property, int width) {
        TableColumn<EndpointMetadata, Object> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(width);
        return column;
    }

    private static class EndpointAccumulator {
        private final String host;
        private final String path;
        private final Set<String> methods = new java.util.TreeSet<>();
        private final Set<Integer> statuses = new java.util.TreeSet<>();
        private final Set<String> parameters = new java.util.TreeSet<>();
        private final Set<String> technologies = new java.util.TreeSet<>();
        private int count;
        private long totalSize;

        EndpointAccumulator(String host, String path) {
            this.host = host;
            this.path = path;
        }

        void add(HttpTransaction tx) {
            count++;
            totalSize += tx.getLength();
            methods.add(tx.getMethod());
            statuses.add(tx.getStatus());
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
                    count);
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

        EndpointMetadata(String host, String path, String methods, String statuses, String parameters,
                         String technologies, long averageSize, int count) {
            this.host = host;
            this.path = path;
            this.methods = methods;
            this.statuses = statuses;
            this.parameters = parameters;
            this.technologies = technologies;
            this.averageSize = averageSize;
            this.count = count;
        }

        public String getHost() { return host; }
        public String getPath() { return path; }
        public String getMethods() { return methods; }
        public String getStatuses() { return statuses; }
        public String getParameters() { return parameters; }
        public String getTechnologies() { return technologies; }
        public long getAverageSize() { return averageSize; }
        public int getCount() { return count; }
    }
}
