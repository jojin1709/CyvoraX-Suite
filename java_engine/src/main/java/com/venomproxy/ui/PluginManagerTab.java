package com.venomproxy.ui;

import com.venomproxy.db.Database;
import com.venomproxy.plugins.PluginLoader;
import com.venomproxy.plugins.PluginStatus;
import com.venomproxy.proxy.ScopeControl;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tab;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class PluginManagerTab extends Tab {
    private final PluginLoader pluginLoader;
    private final Database database;
    private final ScopeControl scopeControl;
    private final ObservableList<PluginStatus> statuses = FXCollections.observableArrayList();
    private final Label status = new Label("Ready");

    public PluginManagerTab(PluginLoader pluginLoader, Database database, ScopeControl scopeControl) {
        super("Plugins");
        this.pluginLoader = pluginLoader;
        this.database = database;
        this.scopeControl = scopeControl;
        setClosable(false);

        TableView<PluginStatus> table = new TableView<>(statuses);
        UiUtil.constrainTable(table);
        table.setPlaceholder(UiUtil.emptyState("No plugins discovered", "Drop plugin JARs into the plugins folder and reload discovery.", "Open Plugins Folder", () -> {
            try {
                java.awt.Desktop.getDesktop().open(pluginLoader.getPluginDirectory().toFile());
            } catch (Exception ex) {
                status.setText("Open folder failed: " + ex.getMessage());
            }
        }));
        table.getColumns().add(enabledColumn());
        table.getColumns().add(column("Name", "name", 220));
        table.getColumns().add(column("State", "state", 130));
        table.getColumns().add(column("Description", "description", 520));
        table.getColumns().add(column("Error", "error", 360));

        Button reload = new Button("Reload");
        reload.setOnAction(event -> reload());
        Button openFolder = new Button("Plugins Folder");
        openFolder.setOnAction(event -> {
            try {
                java.awt.Desktop.getDesktop().open(pluginLoader.getPluginDirectory().toFile());
                status.setText("Opened plugin folder");
            } catch (Exception ex) {
                status.setText("Open folder failed: " + ex.getMessage());
            }
        });

        VBox root = new VBox(8, new HBox(8, reload, openFolder, status), table);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setPadding(new Insets(12));
        setContent(root);
        reload();
    }

    private void reload() {
        pluginLoader.load(database, scopeControl);
        statuses.setAll(pluginLoader.statuses());
        long errors = statuses.stream().filter(plugin -> plugin.getState().toLowerCase().contains("error")).count();
        status.setText("Plugins: " + statuses.size() + " | Errors: " + errors);
    }

    private TableColumn<PluginStatus, Boolean> enabledColumn() {
        TableColumn<PluginStatus, Boolean> column = new TableColumn<>("Enabled");
        column.setPrefWidth(90);
        column.setCellValueFactory(cell -> new javafx.beans.property.SimpleBooleanProperty(cell.getValue().isEnabled()));
        column.setCellFactory(col -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();

            @Override
            protected void updateItem(Boolean enabled, boolean empty) {
                super.updateItem(enabled, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                PluginStatus status = (PluginStatus) getTableRow().getItem();
                checkBox.setSelected(status.isEnabled());
                checkBox.setOnAction(event -> {
                    status.setEnabled(checkBox.isSelected());
                    pluginLoader.setEnabled(status.getName(), checkBox.isSelected());
                    PluginManagerTab.this.status.setText(status.getName() + (checkBox.isSelected() ? " enabled" : " disabled"));
                });
                setGraphic(checkBox);
            }
        });
        return column;
    }

    private TableColumn<PluginStatus, Object> column(String title, String property, int width) {
        TableColumn<PluginStatus, Object> column = new TableColumn<>(title);
        column.setCellValueFactory(new PropertyValueFactory<>(property));
        column.setPrefWidth(width);
        return column;
    }
}
