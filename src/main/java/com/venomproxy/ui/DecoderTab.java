package com.venomproxy.ui;

import com.venomproxy.util.TextCodecs;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class DecoderTab extends Tab {
    public DecoderTab() {
        super("Decoder");
        setClosable(false);
        TextArea input = UiUtil.codeArea("Input");
        TextArea output = UiUtil.codeArea("Output");
        ComboBox<String> operation = new ComboBox<>(FXCollections.observableArrayList(
                "Base64 Encode", "Base64 Decode", "URL Encode", "URL Decode", "HTML Encode", "HTML Decode",
                "Hex Encode", "Hex Decode", "Binary Encode", "Binary Decode", "Gzip Encode", "Gzip Decode",
                "MD5", "SHA1", "SHA256", "SHA512", "JWT Decode", "Smart Decode"
        ));
        operation.getSelectionModel().select("Smart Decode");
        Button run = new Button("Run");
        run.setOnAction(event -> output.setText(TextCodecs.apply(operation.getValue(), input.getText())));
        Button chain = new Button("Chain");
        chain.setOnAction(event -> input.setText(output.getText()));
        SplitPane split = new SplitPane(input, output);
        split.setDividerPositions(0.5);
        VBox root = new VBox(8, new HBox(8, operation, run, chain), split);
        VBox.setVgrow(split, Priority.ALWAYS);
        root.setPadding(new Insets(12));
        setContent(root);
    }
}
