package com.venomproxy.ui;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public class StartupSplash extends BorderPane {
    private final ProgressBar progress = new ProgressBar(0);
    private final Label status = new Label("Starting");
    private final Map<String, Label> steps = new LinkedHashMap<>();

    public StartupSplash(String version) {
        getStyleClass().add("startup-screen");

        Label name = new Label("CyvoraX Suite");
        name.getStyleClass().add("startup-title");
        Label versionLabel = new Label("Version " + version);
        versionLabel.getStyleClass().add("startup-version");

        VBox title = new VBox(4, name, versionLabel);
        HBox brand = new HBox(16, logo(), title);
        brand.setAlignment(Pos.CENTER_LEFT);

        VBox stepList = new VBox(8);
        addStep(stepList, "Loading database");
        addStep(stepList, "Loading certificates");
        addStep(stepList, "Loading plugins");
        addStep(stepList, "Loading tools");

        progress.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(progress, Priority.ALWAYS);
        status.getStyleClass().add("startup-status");

        VBox panel = new VBox(22, brand, stepList, progress, status);
        panel.getStyleClass().add("startup-panel");
        panel.setMaxWidth(520);
        panel.setPadding(new Insets(28));

        setCenter(panel);
        BorderPane.setAlignment(panel, Pos.CENTER);
    }

    public void bind(Task<?> task) {
        progress.progressProperty().bind(task.progressProperty());
        task.messageProperty().addListener((obs, old, value) -> {
            status.setText(value == null || value.isBlank() ? "Starting" : value);
            updateSteps(value);
        });
    }

    public void showFailure(String message) {
        status.setText("Startup failed: " + (message == null || message.isBlank() ? "unknown error" : message));
        status.getStyleClass().add("startup-error");
    }

    private void addStep(VBox parent, String text) {
        Label label = new Label(text);
        label.getStyleClass().add("startup-step");
        steps.put(text, label);
        parent.getChildren().add(label);
    }

    private void updateSteps(String activeStep) {
        boolean markComplete = true;
        for (Map.Entry<String, Label> entry : steps.entrySet()) {
            Label label = entry.getValue();
            label.getStyleClass().removeAll("startup-step-active", "startup-step-complete");
            if (entry.getKey().equals(activeStep)) {
                label.getStyleClass().add("startup-step-active");
                markComplete = false;
            } else if (markComplete || "Ready".equals(activeStep)) {
                label.getStyleClass().add("startup-step-complete");
            }
        }
    }

    private ImageView logo() {
        ImageView imageView = new ImageView();
        try (InputStream stream = getClass().getResourceAsStream("/icons/cyvorax-logo.png")) {
            if (stream != null) {
                imageView.setImage(new Image(stream));
            }
        } catch (Exception ignored) {
        }
        imageView.setFitWidth(76);
        imageView.setFitHeight(76);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.getStyleClass().add("startup-logo");
        return imageView;
    }
}
