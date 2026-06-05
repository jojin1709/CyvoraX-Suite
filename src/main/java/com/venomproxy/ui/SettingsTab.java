package com.venomproxy.ui;

import com.venomproxy.proxy.ScopeControl;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class SettingsTab extends Tab {
    public SettingsTab(MainWindow mainWindow, ScopeControl scopeControl) {
        super("Settings");
        setClosable(false);

        TextField host = new TextField("127.0.0.1");
        TextField port = new TextField("8080");
        TextField upstream = new TextField();
        upstream.setPromptText("host:port");
        TextField timeout = new TextField("60");
        TextField tlsProtocols = new TextField("TLSv1.2,TLSv1.3");
        TextField ignoreMime = new TextField("image/,font/,video/");
        ComboBox<String> theme = new ComboBox<>();
        theme.getItems().addAll(mainWindow.themes());
        theme.getSelectionModel().select(mainWindow.currentTheme());
        theme.setOnAction(event -> mainWindow.applyTheme(theme.getSelectionModel().getSelectedItem()));
        CheckBox autoSave = new CheckBox("Auto-save history");
        autoSave.setSelected(true);
        CheckBox passthrough = new CheckBox("Out-of-scope passthrough");
        passthrough.setSelected(scopeControl.isOutOfScopePassthrough());
        TextArea includes = UiUtil.codeArea("Include domains, wildcards, IPs, regex:...");
        TextArea excludes = UiUtil.codeArea("Exclude domains, wildcards, IPs, regex:...");
        TextArea ignores = UiUtil.codeArea("Ignore domains, IPs, regex:... Traffic passes but is not saved/scanned.");
        includes.setText(scopeControl.includesAsText());
        excludes.setText(scopeControl.excludesAsText());
        ignores.setText(scopeControl.ignoresAsText());

        Button start = new Button("Start Listener");
        start.setOnAction(event -> mainWindow.startProxy(host.getText(), Integer.parseInt(port.getText())));
        Button stop = new Button("Stop Listener");
        stop.setOnAction(event -> mainWindow.stopProxy());
        Button intercept = new Button("Toggle Intercept");
        intercept.setOnAction(event -> mainWindow.setIntercept(!mainWindow.isInterceptEnabled()));
        Button applyScope = new Button("Apply Scope");
        applyScope.setOnAction(event -> {
            scopeControl.setIncludesFromText(includes.getText());
            scopeControl.setExcludesFromText(excludes.getText());
            scopeControl.setIgnoresFromText(ignores.getText());
            scopeControl.setOutOfScopePassthrough(passthrough.isSelected());
            mainWindow.configureNetwork(upstream.getText(), Integer.parseInt(timeout.getText()));
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(new Label("Listener IP"), 0, 0);
        form.add(host, 1, 0);
        form.add(new Label("Port"), 0, 1);
        form.add(port, 1, 1);
        form.add(new Label("Upstream proxy"), 0, 2);
        form.add(upstream, 1, 2);
        form.add(new Label("Timeout seconds"), 0, 3);
        form.add(timeout, 1, 3);
        form.add(new Label("TLS protocols"), 0, 4);
        form.add(tlsProtocols, 1, 4);
        form.add(new Label("Ignore MIME prefixes"), 0, 5);
        form.add(ignoreMime, 1, 5);
        form.add(new Label("Theme"), 0, 6);
        form.add(theme, 1, 6);
        form.add(autoSave, 1, 7);
        form.add(passthrough, 1, 8);
        form.add(start, 2, 0);
        form.add(stop, 2, 1);
        form.add(intercept, 2, 2);

        VBox scope = new VBox(8,
                new Label("Include Scope"), includes,
                new Label("Exclude Scope"), excludes,
                new Label("Ignore List"), ignores,
                applyScope);
        VBox.setVgrow(includes, Priority.ALWAYS);
        VBox.setVgrow(excludes, Priority.ALWAYS);
        VBox.setVgrow(ignores, Priority.ALWAYS);
        VBox root = new VBox(14, form, scope);
        root.setPadding(new Insets(16));
        setContent(root);
    }
}
