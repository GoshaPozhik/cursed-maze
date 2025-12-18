package ru.itis.jjk.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class LobbyController {

    public interface HostHandler {
        void host(String name, String character, int port);
    }
    public interface JoinHandler {
        void join(String name, String character, String host, int port);
    }

    private final BorderPane root = new BorderPane();

    private final TextField nameField = new TextField("Player");
    private final TextField hostField = new TextField("127.0.0.1");
    private final TextField portField = new TextField("7777");

    private final ToggleGroup charGroup = new ToggleGroup();
    private final RadioButton gojo = new RadioButton("Gojo");
    private final RadioButton sukuna = new RadioButton("Sukuna");

    private final Label status = new Label("Ready.");

    public LobbyController(HostHandler hostHandler, JoinHandler joinHandler, Runnable exitHandler) {
        Label title = new Label("JJK Maze Duel");
        title.setFont(Font.font(28));

        gojo.setToggleGroup(charGroup);
        sukuna.setToggleGroup(charGroup);
        gojo.setSelected(true);

        VBox center = new VBox(12);
        center.setPadding(new Insets(20));
        center.setAlignment(Pos.CENTER_LEFT);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);

        int r = 0;
        grid.add(new Label("Name:"), 0, r);
        grid.add(nameField, 1, r++);

        grid.add(new Label("Host IP:"), 0, r);
        grid.add(hostField, 1, r++);

        grid.add(new Label("Port:"), 0, r);
        grid.add(portField, 1, r++);

        HBox chars = new HBox(10, new Label("Character:"), gojo, sukuna);
        chars.setAlignment(Pos.CENTER_LEFT);

        HBox buttons = new HBox(10);
        Button hostBtn = new Button("Host");
        Button joinBtn = new Button("Join");
        Button exitBtn = new Button("Exit");
        buttons.getChildren().addAll(hostBtn, joinBtn, exitBtn);

        hostBtn.setOnAction(e -> {
            try {
                int port = Integer.parseInt(portField.getText().trim());
                hostHandler.host(safeName(), selectedChar(), port);
                status.setText("Hosting on port " + port + " ...");
            } catch (Exception ex) {
                status.setText("Host error: " + ex.getMessage());
            }
        });

        joinBtn.setOnAction(e -> {
            try {
                int port = Integer.parseInt(portField.getText().trim());
                joinHandler.join(safeName(), selectedChar(), hostField.getText().trim(), port);
                status.setText("Connecting to " + hostField.getText().trim() + ":" + port + " ...");
            } catch (Exception ex) {
                status.setText("Join error: " + ex.getMessage());
            }
        });

        exitBtn.setOnAction(e -> exitHandler.run());

        center.getChildren().addAll(title, grid, chars, buttons, new Separator(), status);

        root.setCenter(center);
        root.setPadding(new Insets(10));
    }

    public Parent getRoot() {
        return root;
    }

    private String selectedChar() {
        return gojo.isSelected() ? "GOJO" : "SUKUNA";
    }

    private String safeName() {
        String s = nameField.getText() == null ? "Player" : nameField.getText().trim();
        if (s.isEmpty()) s = "Player";
        if (s.length() > 16) s = s.substring(0, 16);
        return s;
    }
}
