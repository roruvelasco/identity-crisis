package com.identitycrisis.server.net;

import com.identitycrisis.shared.net.*;
import java.io.*;
import java.net.Socket;

/**
 * One connected client. Reader thread reads messages and routes them.
 */
public class ClientConnection implements Runnable {

    private final int clientId;
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final MessageEncoder encoder;
    private final MessageDecoder decoder;
    private final ClientMessageRouter router;
    private volatile boolean connected;
    private String displayName;

    public ClientConnection(int clientId, Socket socket,
                            ClientMessageRouter router) throws IOException {
        this.clientId = clientId;
        this.socket = socket;
        this.router = router;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
        this.encoder = new MessageEncoder(out);
        this.decoder = new MessageDecoder(in);
        this.connected = true;
    }

    @Override
    public void run() {
        // while (connected): type = decoder.readNextType();
        //   router.route(this, type, decoder)
    }

    public int getClientId() { throw new UnsupportedOperationException("stub"); }

    public String getDisplayName() { throw new UnsupportedOperationException("stub"); }

    public void setDisplayName(String name) { }

    public MessageEncoder getEncoder() { throw new UnsupportedOperationException("stub"); }

    public DataOutputStream getOutputStream() { throw new UnsupportedOperationException("stub"); }

    public boolean isConnected() { throw new UnsupportedOperationException("stub"); }

    public void disconnect() { }
}
