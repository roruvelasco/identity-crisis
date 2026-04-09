package com.identitycrisis.client.net;

import com.identitycrisis.shared.net.*;
import java.io.*;
import java.net.Socket;

/**
 * TCP connection to server. Reader thread + synchronized send methods.
 */
public class GameClient {

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private MessageEncoder encoder;
    private MessageDecoder decoder;
    private ServerMessageRouter router;
    private Thread readerThread;
    private volatile boolean connected;

    public GameClient(ServerMessageRouter router) { this.router = router; }

    public void connect(String host, int port) throws IOException { }

    public void startListening() { }

    private void readLoop() {
        // while(connected): type = decoder.readNextType();
        //   router.route(type, decoder)
    }

    // Send methods (synchronized — called from JavaFX or game loop thread)

    public synchronized void sendJoinRequest(String displayName) { }

    public synchronized void sendReady() { }

    public synchronized void sendInput(boolean up, boolean down, boolean left,
                                       boolean right, boolean carry,
                                       boolean throwAction) { }

    public synchronized void sendChat(String text) { }

    public boolean isConnected() { throw new UnsupportedOperationException("stub"); }

    public void disconnect() { }
}
