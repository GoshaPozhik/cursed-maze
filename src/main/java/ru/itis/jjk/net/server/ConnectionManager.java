package ru.itis.jjk.net.server;

import ru.itis.jjk.core.Constants;
import ru.itis.jjk.net.codec.FramedTcp;
import ru.itis.jjk.net.msg.NetMessage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class ConnectionManager {

    private final int port;
    private final FramedTcp framed;

    private final ExecutorService acceptPool =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "server-accept"));

    private final List<ClientPeer> peers = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    private volatile boolean running;
    private ServerSocket serverSocket;

    private final BiConsumer<ClientPeer, NetMessage> onMessage;
    private final Consumer<ClientPeer> onPeerConnected;
    private final Consumer<ClientPeer> onPeerClosed;

    public ConnectionManager(
            int port,
            FramedTcp framed,
            BiConsumer<ClientPeer, NetMessage> onMessage,
            Consumer<ClientPeer> onPeerConnected,
            Consumer<ClientPeer> onPeerClosed
    ) {
        this.port = port;
        this.framed = framed;
        this.onMessage = onMessage;
        this.onPeerConnected = onPeerConnected;
        this.onPeerClosed = onPeerClosed;
    }

    public void start() throws IOException {
        if (running) return;
        running = true;
        serverSocket = new ServerSocket(port);
        acceptPool.submit(this::acceptLoop);
    }

    public void shutdown() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        for (ClientPeer p : peers) p.close();
        acceptPool.shutdownNow();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);

                if (peers.size() >= Constants.MAX_PLAYERS) {
                    socket.close();
                    continue;
                }

                int id = nextId.getAndIncrement();
                ClientPeer peer = new ClientPeer(
                        id,
                        socket,
                        framed,
                        onMessage,
                        p -> {
                            peers.remove(p);
                            onPeerClosed.accept(p);
                        }
                );

                peers.add(peer);
                onPeerConnected.accept(peer);
                peer.start();

            } catch (IOException e) {
                if (running) e.printStackTrace();
            }
        }
    }

    public void broadcast(NetMessage msg) {
        for (ClientPeer peer : peers) peer.send(msg);
    }
}
