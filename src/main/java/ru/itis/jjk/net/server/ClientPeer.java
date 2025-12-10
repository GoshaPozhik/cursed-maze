package ru.itis.jjk.net.server;

import ru.itis.jjk.net.codec.FramedTcp;
import ru.itis.jjk.net.msg.NetMessage;

import java.io.*;
import java.net.Socket;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class ClientPeer {

    public final int id;

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final FramedTcp framed;

    private final BiConsumer<ClientPeer, NetMessage> onMessage;
    private final Consumer<ClientPeer> onClosed;

    private final Thread readerThread;

    ClientPeer(int id, Socket socket, FramedTcp framed,
               BiConsumer<ClientPeer, NetMessage> onMessage,
               Consumer<ClientPeer> onClosed) throws IOException {

        this.id = id;
        this.socket = socket;
        this.framed = framed;
        this.onMessage = onMessage;
        this.onClosed = onClosed;

        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

        this.readerThread = new Thread(this::readLoop, "server-peer-" + id);
        this.readerThread.setDaemon(true);
    }

    void start() {
        readerThread.start();
    }

    void send(NetMessage msg) {
        try {
            framed.send(out, msg);
        } catch (IOException e) {
            close();
        }
    }

    void close() {
        try { socket.close(); } catch (IOException ignored) {}
    }

    private void readLoop() {
        try {
            while (!socket.isClosed()) {
                NetMessage msg = framed.read(in);
                onMessage.accept(this, msg);
            }
        } catch (IOException ignored) {
        } finally {
            onClosed.accept(this);
            close();
        }
    }
}
