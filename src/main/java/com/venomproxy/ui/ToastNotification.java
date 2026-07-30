package com.venomproxy.ui;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public final class ToastNotification {
    private static VBox toastContainer;

    private ToastNotification() {
    }

    public static synchronized void init(StackPane rootStackPane) {
        if (toastContainer != null && rootStackPane.getChildren().contains(toastContainer)) {
            return;
        }
        toastContainer = new VBox(8);
        toastContainer.setPickOnBounds(false);
        toastContainer.setAlignment(Pos.TOP_RIGHT);
        toastContainer.setPadding(new Insets(16, 20, 0, 0));
        toastContainer.setMaxWidth(380);
        StackPane.setAlignment(toastContainer, Pos.TOP_RIGHT);
        rootStackPane.getChildren().add(toastContainer);
    }

    public static void showSuccess(StackPane root, String message) {
        show(root, "✓ " + message, "toast-success");
    }

    public static void showError(StackPane root, String message) {
        show(root, "⚠ " + message, "toast-error");
    }

    public static void showWarning(StackPane root, String message) {
        show(root, "⚡ " + message, "toast-warning");
    }

    public static void showInfo(StackPane root, String message) {
        show(root, "ℹ " + message, "toast-info");
    }

    public static void show(StackPane root, String message, String styleClass) {
        Platform.runLater(() -> {
            if (root != null) {
                init(root);
            }
            if (toastContainer == null) {
                return;
            }

            Label textLabel = new Label(message);
            textLabel.setWrapText(true);
            textLabel.getStyleClass().add("toast-text");

            HBox toast = new HBox(textLabel);
            toast.getStyleClass().addAll("cx-toast", styleClass);
            toast.setMaxWidth(360);
            toast.setPadding(new Insets(10, 14, 10, 14));

            toastContainer.getChildren().add(0, toast);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(250), toast);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);

            TranslateTransition slideIn = new TranslateTransition(Duration.millis(250), toast);
            slideIn.setFromY(-15.0);
            slideIn.setToY(0.0);

            ParallelTransition entrance = new ParallelTransition(fadeIn, slideIn);
            entrance.play();

            entrance.setOnFinished(event -> {
                FadeTransition fadeOut = new FadeTransition(Duration.millis(350), toast);
                fadeOut.setFromValue(1.0);
                fadeOut.setToValue(0.0);
                fadeOut.setDelay(Duration.millis(3200));

                fadeOut.setOnFinished(evt -> toastContainer.getChildren().remove(toast));
                fadeOut.play();
            });
        });
    }
}
