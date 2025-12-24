package ru.itis.jjk;

import ru.itis.jjk.net.client.GameClient;
import ru.itis.jjk.net.server.GameServer;
import ru.itis.jjk.ui.controller.GameController;
import ru.itis.jjk.ui.controller.LobbyController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

public class MainApp extends Application {

    private Stage stage;
    private GameServer hostedServer;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        this.stage.setTitle("JJK Maze Duel — JavaFX + Sockets");
        showLobby();
        this.stage.show();
    }

    private void showLobby() {
        LobbyController lobby = new LobbyController(this::onHostRequested, this::onJoinRequested, this::onExitRequested);
        Scene scene = new Scene(lobby.getRoot(), 900, 600);
        scene.getStylesheets().add(Objects.requireNonNull(MainApp.class.getResource("/ru/itis/jjk/style.css")).toExternalForm());
        stage.setScene(scene);
    }

    private void showGame(GameClient client) {
        GameController game = new GameController(client, this::backToLobby);
        Scene scene = new Scene(game.getRoot(), 1100, 700);
        scene.getStylesheets().add(Objects.requireNonNull(MainApp.class.getResource("/ru/itis/jjk/style.css")).toExternalForm());
        stage.setScene(scene);
        game.start();
    }

    private void backToLobby() {
        if (hostedServer != null) {
            hostedServer.shutdown();
            hostedServer = null;
        }
        showLobby();
    }

    private void onHostRequested(String name, String character, int port) {
        try {
            hostedServer = new GameServer(port);
            hostedServer.start();

            GameClient client = new GameClient("127.0.0.1", port);
            client.connect();
            client.sendHello(name, character);

            showGame(client);
        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(this::showLobby);
        }
    }

    private void onJoinRequested(String name, String character, String host, int port) {
        try {
            GameClient client = new GameClient(host, port);
            client.connect();
            client.sendHello(name, character);
            showGame(client);
        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(this::showLobby);
        }
    }

    private void onExitRequested() {
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
