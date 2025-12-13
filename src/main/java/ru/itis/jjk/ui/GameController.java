package ru.itis.jjk.ui;

import ru.itis.jjk.net.client.GameClient;
import ru.itis.jjk.net.msg.*;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public final class GameController {

    private final BorderPane root = new BorderPane();
    private final Canvas canvas = new Canvas(900, 650);
    private final Label connLabel = new Label("Connected");
    private final ListView<String> log = new ListView<>();
    private final TextField chatField = new TextField();
    private final Button sendBtn = new Button("Send");
    private final Button rematchBtn = new Button("Rematch");

    private final GameClient client;
    private final Runnable onExitToLobby;

    private volatile int yourId = -1;
    private volatile String yourCharacter = "UNKNOWN";
    private volatile int[][] mazeCells = null;
    private volatile int mazeW = 0;
    private volatile int mazeH = 0;

    private final Map<Integer, PlayerStateDTO> players = new ConcurrentHashMap<>();
    private final Map<Integer, ProjectileStateDTO> projectiles = new ConcurrentHashMap<>();
    private final Map<Integer, Long> wallFx = new ConcurrentHashMap<>();

    private long lastServerTimeMs = 0L;
    private volatile MatchInfo matchInfo = null;

    private long lastBlueCastMs = 0L;
    private long lastDashCastMs = 0L;
    private long lastDismantleCastMs = 0L;

    private static final long PURPLE_HINT_WINDOW_MS = 300;
    private static final long BLUE_MAX_HINT_WINDOW_MS = 900;
    private static final long WCS_HINT_WINDOW_MS = 900;

    private final Set<KeyCode> held = EnumSet.noneOf(KeyCode.class);
    private final Set<KeyCode> actionHeldLock = EnumSet.noneOf(KeyCode.class);
    private final AtomicLong inputSeq = new AtomicLong(1);
    private final ScheduledExecutorService netSendExec = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "client-send"));
    private AnimationTimer timer;

    public GameController(GameClient client, Runnable onExitToLobby) {
        this.client = client;
        this.onExitToLobby = onExitToLobby;
        buildUi();
        wireInput();
        wireChat();
    }

    public Parent getRoot() { return root; }

    public void start() {
        netSendExec.scheduleAtFixedRate(() -> {
            try { if (client.isConnected()) client.ping(); } catch (IOException ignored) {}
        }, 500, 1000, TimeUnit.MILLISECONDS);

        timer = new AnimationTimer() {
            @Override public void handle(long now) {
                pumpInbox();
                render();
            }
        };
        timer.start();
    }

    private void stop() {
        if (timer != null) timer.stop();
        netSendExec.shutdownNow();
        client.close();
    }

    private void buildUi() {
        StackPane canvasPane = new StackPane();
        canvasPane.setPadding(new Insets(10));
        canvasPane.getChildren().add(canvas);

        rematchBtn.setVisible(false);
        rematchBtn.setManaged(false);
        rematchBtn.setFocusTraversable(false);
        rematchBtn.getStyleClass().add("rematch-btn");
        rematchBtn.setOnAction(e -> requestRematchReady());
        StackPane.setAlignment(rematchBtn, Pos.CENTER);
        canvasPane.getChildren().add(rematchBtn);

        VBox side = new VBox(10);
        side.setPadding(new Insets(10));
        side.setPrefWidth(280);

        Button exitBtn = new Button("Back to Lobby");
        exitBtn.getStyleClass().add("ghost-btn");
        exitBtn.setOnAction(e -> { stop(); onExitToLobby.run(); });

        connLabel.setWrapText(true);
        connLabel.getStyleClass().add("muted");
        log.setPrefHeight(380);
        log.getItems().add("Waiting for WELCOME from server...");

        HBox chatBox = new HBox(6, chatField, sendBtn);
        HBox.setHgrow(chatField, Priority.ALWAYS);
        chatField.setPromptText("Chat...");
        sendBtn.getStyleClass().add("accent-btn");

        TitledPane logPane = new TitledPane("Events", log);
        logPane.setExpanded(false); logPane.setCollapsible(true);
        TitledPane chatPane = new TitledPane("Chat", chatBox);
        chatPane.setExpanded(true); chatPane.setCollapsible(true);

        side.getChildren().addAll(new Label("Status"), connLabel, logPane, chatPane, exitBtn);
        VBox.setVgrow(logPane, Priority.ALWAYS);

        root.setCenter(canvasPane);
        root.setRight(side);
        BorderPane.setAlignment(side, Pos.TOP_RIGHT);
    }

    private void wireChat() {
        Runnable send = () -> {
            String txt = chatField.getText();
            if (txt == null || txt.trim().isEmpty()) return;
            chatField.clear();
            String finalTxt = txt.trim();
            netSendExec.execute(() -> {
                try { client.send(new ChatMsg(yourId, finalTxt)); } catch (IOException e) { pushLog("Chat send error: " + e.getMessage()); }
            });
        };
        sendBtn.setOnAction(e -> send.run());
        chatField.setOnAction(e -> send.run());
    }

    private void wireInput() {
        root.setOnKeyPressed(e -> {
            KeyCode code = e.getCode();
            if (code == KeyCode.ENTER && !chatField.isFocused()) {
                if (matchInfo != null && matchInfo.matchOver()) { requestRematchReady(); e.consume(); return; }
            }
            held.add(code);
            if (isActionKey(code) && !actionHeldLock.contains(code)) {
                actionHeldLock.add(code);
                String action = actionForKey(code);
                if (!(code == KeyCode.R && "BLUE".equals(action))) registerLocalCastHint(action);
                sendInput(Collections.singletonList(action));
            } else if (isMoveKey(code)) sendInput(Collections.emptyList());
            e.consume();
        });

        root.setOnKeyReleased(e -> {
            KeyCode code = e.getCode();
            held.remove(code);
            actionHeldLock.remove(code);
            if (isMoveKey(code)) sendInput(Collections.emptyList());
            e.consume();
        });
        Platform.runLater(root::requestFocus);
    }

    private boolean isMoveKey(KeyCode c) { return c == KeyCode.W || c == KeyCode.A || c == KeyCode.S || c == KeyCode.D; }

    private boolean isActionKey(KeyCode c) { return c == KeyCode.Q || c == KeyCode.E || c == KeyCode.R || c == KeyCode.SPACE || c == KeyCode.SHIFT; }

    private String actionForKey(KeyCode c) {
        PlayerStateDTO me = players.get(yourId);
        boolean gojo = me != null ? "GOJO".equalsIgnoreCase(me.character) : "GOJO".equalsIgnoreCase(yourCharacter);
        return switch (c) {
            case Q -> gojo ? "BLUE" : "DISMANTLE";
            case E -> gojo ? "RED" : "CLEAVE";
            case R -> gojo ? "BLUE" : "FUGA";
            case SPACE -> "DASH";
            case SHIFT -> gojo ? "DOMAIN_GOJO" : "DOMAIN_SUKUNA";
            default -> "UNKNOWN";
        };
    }

    private void sendInput(List<String> actionsPressed) {
        InputMsg im = new InputMsg();
        im.up = held.contains(KeyCode.W); im.down = held.contains(KeyCode.S);
        im.left = held.contains(KeyCode.A); im.right = held.contains(KeyCode.D);
        if (actionsPressed != null) im.actionsPressed.addAll(actionsPressed);
        im.seq = inputSeq.getAndIncrement();
        netSendExec.execute(() -> { try { client.sendInput(im); } catch (IOException e) { pushLog("Input send error: " + e.getMessage()); } });
    }

    private void pumpInbox() {
        int budget = 300;
        while (budget-- > 0) {
            NetMessage msg = client.inbox().poll();
            if (msg == null) break;
            switch (msg.type()) {
                case WELCOME -> onWelcome((WelcomeMsg) msg);
                case STATE -> onState((StateMsg) msg);
                case EVENT -> onEvent((EventMsg) msg);
                case PONG -> onPong((PongMsg) msg);
                case CHAT -> onChat((ChatMsg) msg);
                case MAZE_DELTA -> onMazeDelta((MazeDeltaMsg) msg);
                case MAZE_FULL -> onMazeFull((MazeFullMsg) msg);
                default -> {}
            }
        }
    }

    private void onMazeFull(MazeFullMsg mf) {
        lastServerTimeMs = mf.serverTimeMs; mazeW = mf.mazeW; mazeH = mf.mazeH; mazeCells = mf.mazeCells;
        wallFx.clear();
        connLabel.setText("Maze reset for new round");
        pushLog("MAZE_FULL: received full maze snapshot (" + mazeW + "x" + mazeH + ")");
    }

    private void onWelcome(WelcomeMsg wm) {
        yourId = wm.yourPlayerId; mazeW = wm.mazeW; mazeH = wm.mazeH; mazeCells = wm.mazeCells;
        connLabel.setText("WELCOME received. Seed=" + wm.seed + ", Maze=" + wm.mazeW + "x" + wm.mazeH);
        pushLog("WELCOME: you are player #" + yourId);
    }

    private void onState(StateMsg sm) {
        lastServerTimeMs = sm.serverTimeMs;
        matchInfo = new MatchInfo(sm.roundNumber, sm.winScore, sm.p1Id, sm.p2Id, sm.p1Score, sm.p2Score, sm.roundActive, sm.lastRoundWinnerId, sm.nextRoundStartMs, sm.matchOver, sm.matchWinnerId, sm.p1RematchReady, sm.p2RematchReady);
        boolean show = matchInfo.matchOver();
        rematchBtn.setVisible(show); rematchBtn.setManaged(show); rematchBtn.setDisable(show && isLocalRematchReady(matchInfo));

        for (PlayerStateDTO p : sm.players) players.put(p.id, p);
        projectiles.clear();
        if (sm.projectiles != null) for (ProjectileStateDTO pr : sm.projectiles) projectiles.put(pr.id, pr);
    }

    private void onEvent(EventMsg ev) { pushLog("EVENT [" + ev.eventType + "] from #" + ev.actorId + " : " + ev.payload); }

    private void onPong(PongMsg pong) { connLabel.setText("Connected. RTT ~ " + (System.currentTimeMillis() - pong.clientTimeMs) + " ms"); }

    private void onMazeDelta(MazeDeltaMsg md) {
        if (mazeCells == null || md.indices == null) return;
        long nowNs = System.nanoTime();
        for (int idx : md.indices) {
            int x = idx % mazeW, y = idx / mazeW;
            if (y >= 0 && y < mazeH && x >= 0 && x < mazeW) { mazeCells[y][x] = 0; wallFx.put(idx, nowNs); }
        }
        wallFx.entrySet().removeIf(e -> nowNs - e.getValue() > 700_000_000L);
    }

    private void onChat(ChatMsg cm) { pushLog("CHAT #" + cm.fromId + ": " + cm.text); }

    private int computeComboGlowMask(PlayerStateDTO me, long nowMs) {
        if (me == null) return 0;
        boolean gojo = "GOJO".equalsIgnoreCase(me.character);
        int mask = 0;
        if (gojo) {
            if (lastBlueCastMs > 0) {
                long dt = nowMs - lastBlueCastMs;
                if (dt >= 0 && dt <= PURPLE_HINT_WINDOW_MS) mask |= (1 << 1);
                if (dt >= 0 && dt <= BLUE_MAX_HINT_WINDOW_MS) mask |= (1 << 2);
            }
        } else {
            if (lastDashCastMs > 0) {
                long dtd = nowMs - lastDashCastMs;
                if (dtd >= 0 && dtd <= WCS_HINT_WINDOW_MS) {
                    if (!(lastDismantleCastMs > lastDashCastMs && (nowMs - lastDismantleCastMs) <= WCS_HINT_WINDOW_MS)) mask |= (1 << 0);
                }
            }
            if (lastDismantleCastMs > lastDashCastMs && lastDismantleCastMs > 0) {
                long dtq = nowMs - lastDismantleCastMs;
                if (dtq >= 0 && dtq <= WCS_HINT_WINDOW_MS) mask |= (1 << 1);
            }
        }
        return mask;
    }

    private long nowMsForHints() { return lastServerTimeMs > 0 ? lastServerTimeMs : System.currentTimeMillis(); }

    private void registerLocalCastHint(String action) {
        PlayerStateDTO me = players.get(yourId);
        if (me == null) return;
        long now = nowMsForHints();
        boolean gojo = "GOJO".equalsIgnoreCase(me.character);
        switch (action) {
            case "BLUE" -> { if (!gojo) { if (now >= me.cdDismantleUntilMs && me.cursedEnergy >= 10) lastDismantleCastMs = now; } else { if (now >= me.cdBlueUntilMs && me.cursedEnergy >= 12) lastBlueCastMs = now; } }
            case "DISMANTLE" -> { if (!gojo && now >= me.cdDismantleUntilMs && me.cursedEnergy >= 10) lastDismantleCastMs = now; }
            case "DASH" -> { if (now >= me.cdDashUntilMs && me.cursedEnergy >= 6) lastDashCastMs = now; }
            default -> {}
        }
    }

    private void render() {
        Renderer.render(canvas.getGraphicsContext2D(), mazeCells, mazeW, mazeH, players, projectiles, yourId, wallFx, lastServerTimeMs, computeComboGlowMask(players.get(yourId), nowMsForHints()), matchInfo);
    }

    private boolean isLocalRematchReady(MatchInfo mi) {
        if (mi == null || yourId <= 0) return false;
        if (yourId == mi.p1Id()) return mi.p1RematchReady();
        if (yourId == mi.p2Id()) return mi.p2RematchReady();
        return false;
    }

    private void requestRematchReady() {
        if (matchInfo == null || !matchInfo.matchOver() || isLocalRematchReady(matchInfo)) return;
        sendInput(Collections.singletonList("REMATCH_READY"));
        rematchBtn.setDisable(true);
        pushLog("Rematch: READY sent");
    }

    private void pushLog(String s) {
        Platform.runLater(() -> {
            log.getItems().add(0, s);
            if (log.getItems().size() > 220) log.getItems().remove(log.getItems().size() - 1);
        });
    }
}