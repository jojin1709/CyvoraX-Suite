package com.venomproxy.ui;

import com.venomproxy.auth.AuthenticationManager;
import com.venomproxy.model.AuthAccount;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.util.regex.Pattern;

public class AuthManagerTab extends Tab {
    private final AuthenticationManager manager;
    private final ObservableList<AuthAccount> accounts = FXCollections.observableArrayList();
    private final TableView<AuthAccount> table = new TableView<>(accounts);
    private final Label status = new Label();
    private final TextField name = new TextField();
    private final TextArea hostPattern = UiUtil.codeArea("example.com, *.example.com, or /regex/");
    private final TextArea bearerToken = UiUtil.codeArea("Bearer token value without the Bearer prefix");
    private final TextArea cookieJar = UiUtil.codeArea("sid=...; csrf=...");
    private final TextField expiresAt = new TextField();
    private final TextField validationUrl = new TextField("https://example.com/");
    private final CheckBox active = new CheckBox("Active");
    private AuthAccount selected;

    public AuthManagerTab(AuthenticationManager manager) {
        super("Auth Manager");
        this.manager = manager;
        setClosable(false);
        configureTable();

        Button save = new Button("Save Account");
        save.setOnAction(event -> save());
        Button activate = new Button("Toggle Active");
        activate.setOnAction(event -> toggleActive());
        Button delete = new Button("Delete");
        delete.setOnAction(event -> delete());
        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> refresh());
        Button validate = new Button("Validate Match");
        validate.setOnAction(event -> validateMatch());

        GridPane form = form();
        VBox editor = new VBox(8, form, new HBox(8, save, activate, delete, refresh, validate), status);
        editor.setPadding(new Insets(12));
        VBox.setVgrow(hostPattern, Priority.ALWAYS);
        VBox.setVgrow(bearerToken, Priority.ALWAYS);
        VBox.setVgrow(cookieJar, Priority.ALWAYS);

        SplitPane split = new SplitPane(table, editor);
        split.setDividerPositions(0.48);
        setContent(split);
        refresh();
    }

    private void configureTable() {
        table.setPlaceholder(UiUtil.emptyState("No auth profiles", "Create bearer-token or cookie profiles to auto-apply credentials to matching hosts.", null, null));
        TableColumn<AuthAccount, Long> id = new TableColumn<>("#");
        id.setCellValueFactory(new PropertyValueFactory<>("id"));
        TableColumn<AuthAccount, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        TableColumn<AuthAccount, String> scopeColumn = new TableColumn<>("Scope");
        scopeColumn.setCellValueFactory(new PropertyValueFactory<>("hostPattern"));
        TableColumn<AuthAccount, Boolean> activeColumn = new TableColumn<>("Active");
        activeColumn.setCellValueFactory(new PropertyValueFactory<>("active"));
        TableColumn<AuthAccount, Boolean> expiredColumn = new TableColumn<>("Expired");
        expiredColumn.setCellValueFactory(new PropertyValueFactory<>("expired"));
        table.getColumns().addAll(id, nameColumn, scopeColumn, activeColumn, expiredColumn);
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, account) -> load(account));
    }

    private GridPane form() {
        expiresAt.setPromptText("ISO-8601, e.g. 2026-12-31T23:59:59Z");
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Name"), 0, 0);
        grid.add(name, 1, 0);
        grid.add(new Label("Host Pattern"), 0, 1);
        grid.add(hostPattern, 1, 1);
        grid.add(new Label("Bearer Token"), 0, 2);
        grid.add(bearerToken, 1, 2);
        grid.add(new Label("Cookie Jar"), 0, 3);
        grid.add(cookieJar, 1, 3);
        grid.add(new Label("Expires At"), 0, 4);
        grid.add(expiresAt, 1, 4);
        grid.add(new Label("Validate URL"), 0, 5);
        grid.add(validationUrl, 1, 5);
        grid.add(active, 1, 6);
        return grid;
    }

    private void load(AuthAccount account) {
        selected = account;
        if (account == null) {
            name.clear();
            hostPattern.clear();
            bearerToken.clear();
            cookieJar.clear();
            expiresAt.clear();
            active.setSelected(false);
            return;
        }
        name.setText(account.getName());
        hostPattern.setText(account.getHostPattern());
        bearerToken.setText(account.getBearerToken());
        cookieJar.setText(account.getCookieJar());
        expiresAt.setText(account.getExpiresAt());
        active.setSelected(account.isActive());
    }

    private void save() {
        String validationError = validationError();
        if (!validationError.isBlank()) {
            status.setText(validationError);
            return;
        }
        AuthAccount account = selected == null
                ? new AuthAccount(name.getText(), hostPattern.getText(), bearerToken.getText(), cookieJar.getText(), expiresAt.getText(), active.isSelected())
                : selected;
        account.setName(name.getText());
        account.setHostPattern(hostPattern.getText());
        account.setBearerToken(bearerToken.getText());
        account.setCookieJar(cookieJar.getText());
        account.setExpiresAt(expiresAt.getText());
        account.setActive(active.isSelected());
        manager.save(account);
        refresh();
        status.setText("Saved " + account.getName());
    }

    private void toggleActive() {
        AuthAccount account = table.getSelectionModel().getSelectedItem();
        if (account == null) {
            return;
        }
        manager.setActive(account.getId(), !account.isActive());
        refresh();
    }

    private void delete() {
        AuthAccount account = table.getSelectionModel().getSelectedItem();
        if (account == null) {
            return;
        }
        manager.delete(account.getId());
        selected = null;
        load(null);
        refresh();
    }

    private void refresh() {
        accounts.setAll(manager.accounts());
        status.setText("Accounts: " + accounts.size() + " | Expired: " + manager.expiredCount());
    }

    private void validateMatch() {
        String validationError = validationError();
        if (!validationError.isBlank()) {
            status.setText(validationError);
            return;
        }
        AuthAccount account = new AuthAccount(name.getText(), hostPattern.getText(), bearerToken.getText(),
                cookieJar.getText(), expiresAt.getText(), true);
        status.setText(account.matches(validationUrl.getText()) ? "Profile matches validation URL" : "Profile does not match validation URL");
    }

    private String validationError() {
        if (name.getText().isBlank()) {
            return "Name is required.";
        }
        if (hostPattern.getText().isBlank()) {
            return "Host pattern is required.";
        }
        if (bearerToken.getText().isBlank() && cookieJar.getText().isBlank()) {
            return "Add a bearer token, cookie jar, or both.";
        }
        if (!expiresAt.getText().isBlank()) {
            try {
                Instant.parse(expiresAt.getText().trim());
            } catch (Exception ex) {
                return "Expires At must be ISO-8601, for example 2026-12-31T23:59:59Z.";
            }
        }
        for (String rawPattern : hostPattern.getText().split("[,\\r\\n]+")) {
            String pattern = rawPattern.trim();
            if (pattern.startsWith("/") && pattern.endsWith("/") && pattern.length() > 2) {
                try {
                    Pattern.compile(pattern.substring(1, pattern.length() - 1));
                } catch (Exception ex) {
                    return "Invalid host regex: " + ex.getMessage();
                }
            }
        }
        return "";
    }
}
