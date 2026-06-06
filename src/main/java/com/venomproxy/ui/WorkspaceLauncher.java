package com.venomproxy.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.function.Consumer;

public class WorkspaceLauncher extends BorderPane {
    public WorkspaceLauncher(String version, Path profileDirectory, Consumer<WorkspaceSelection> onOpen) {
        getStyleClass().add("workspace-launcher");

        Label title = new Label("CyvoraX Suite");
        title.getStyleClass().add("launcher-title");
        Label subtitle = new Label("Workspace Launcher");
        subtitle.getStyleClass().add("launcher-subtitle");
        Label versionLabel = new Label("Version " + version);
        versionLabel.getStyleClass().add("launcher-version");

        VBox heading = new VBox(4, title, subtitle, versionLabel);
        HBox brand = new HBox(14, logo(), heading);
        brand.setAlignment(Pos.CENTER_LEFT);

        GridPane actions = new GridPane();
        actions.getStyleClass().add("launcher-grid");
        actions.setHgap(14);
        actions.setVgap(14);

        actions.add(action("Temporary Workspace", "Use the current profile for an isolated session.",
                () -> onOpen.accept(new WorkspaceSelection("Temporary Workspace", profileDirectory))), 0, 0);
        actions.add(action("New Workspace", "Start from the current CyvoraX profile.",
                () -> onOpen.accept(new WorkspaceSelection("New Workspace", profileDirectory))), 1, 0);
        actions.add(action("Open Existing Workspace", "Open the current CyvoraX profile.",
                () -> onOpen.accept(new WorkspaceSelection("Existing Workspace", profileDirectory))), 0, 1);
        actions.add(action("Recent Workspaces", profileDirectory.toString(),
                () -> onOpen.accept(new WorkspaceSelection("Recent Workspace", profileDirectory))), 1, 1);

        Label profile = new Label("Profile: " + profileDirectory);
        profile.getStyleClass().add("launcher-profile");

        VBox panel = new VBox(24, brand, actions, profile);
        panel.getStyleClass().add("launcher-panel");
        panel.setPadding(new Insets(28));
        panel.setMaxWidth(760);

        setCenter(panel);
        BorderPane.setAlignment(panel, Pos.CENTER);
    }

    private VBox action(String title, String detail, Runnable runnable) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("launcher-action-title");
        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("launcher-action-detail");
        detailLabel.setWrapText(true);

        Button button = new Button(title);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> runnable.run());

        VBox box = new VBox(10, titleLabel, detailLabel, button);
        box.getStyleClass().add("launcher-action");
        box.setMinWidth(300);
        box.setPrefWidth(340);
        GridPane.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private ImageView logo() {
        ImageView imageView = new ImageView();
        try (InputStream stream = getClass().getResourceAsStream("/icons/cyvorax-logo.png")) {
            if (stream != null) {
                imageView.setImage(new Image(stream));
            }
        } catch (Exception ignored) {
        }
        imageView.setFitWidth(58);
        imageView.setFitHeight(58);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.getStyleClass().add("launcher-logo");
        return imageView;
    }

    public record WorkspaceSelection(String name, Path profileDirectory) {
    }
}
