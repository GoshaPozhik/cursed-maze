package ru.itis.jjk.net.client;

import ru.itis.jjk.net.codec.FramedTcp;
import ru.itis.jjk.net.msg.*;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public final class GameClient {

    private final String host;
    private final int port;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private final FramedTcp framed = new FramedTcp();

    private final BlockingQueue<NetMessage> inbox = new LinkedBlockingQueue<>();

    private volatile boolean connected = false;

    public GameClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void connect() throws IOException {
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);

        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

        connected = true;
        Thread readerThread = new Thread(this::readLoop, "client-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public boolean isConnected() {
        return connected && socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void close() {
        connected = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    public BlockingQueue<NetMessage> inbox() {
        return inbox;
    }

    public void send(NetMessage msg) throws IOException {
        framed.send(out, msg);
    }

    public void sendHello(String name, String character) throws IOException {
        send(new HelloMsg(name, character));
    }

    public void sendInput(InputMsg msg) throws IOException {
        send(msg);
    }

    public void ping() throws IOException {
        send(new PingMsg(System.currentTimeMillis()));
    }

    private void readLoop() {
        try {
            while (connected && !socket.isClosed()) {
                NetMessage msg = framed.read(in);
                inbox.offer(msg);
            }
        } catch (IOException ignored) {
        } finally {
            close();
        }
    }
}
