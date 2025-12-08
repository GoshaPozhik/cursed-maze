package ru.itis.jjk.net.codec;

import ru.itis.jjk.net.msg.NetMessage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class FramedTcp {

    private final MessageCodec codec = new MessageCodec();

    public void send(DataOutputStream out, NetMessage msg) throws IOException {
        byte[] payload = codec.encode(msg);
        synchronized (out) {
            out.writeInt(payload.length);
            out.write(payload);
            out.flush();
        }
    }

    public NetMessage read(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len <= 0 || len > 5_000_000) {
            throw new IOException("Invalid frame length: " + len);
        }
        byte[] payload = new byte[len];
        in.readFully(payload);
        return codec.decode(payload);
    }
}
