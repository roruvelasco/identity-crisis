package com.identitycrisis.client.net;

import com.identitycrisis.shared.net.*;
import com.identitycrisis.shared.util.Logger;
import java.io.*;
import java.net.Socket;

/**
 * TCP connection to server. Reader thread + synchronized send methods.
 *
 * <p>Usage:
 * <pre>
 *   GameClient gc = new GameClient(new ServerMessageRouter(localGameState));
 *   gc.connect("192.168.1.42", 5137);   // throws IOException on failure
 *   gc.startListening();                // spawns daemon reader thread
 *   gc.sendJoinRequest("Alice");
 * </pre>
 *
 * <p>Thread-safety:
 * <ul>
 *   <li>All {@code send*} methods are {@code synchronized} on {@code this} to
 *       serialise writes to {@code out} (FX thread + game loop thread both write).</li>
 *   <li>The reader thread is the sole reader of {@code in}.</li>
 *   <li>{@code connected} is {@code volatile} so the reader sees
 *       {@link #disconnect()} immediately.</li>
 * </ul>
 */
public class GameClient {

    private static final Logger LOG = new Logger("GameClient");

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private MessageEncoder encoder;
    private MessageDecoder decoder;
    private final ServerMessageRouter router;
    private Thread readerThread;
    private volatile boolean connected;

    public GameClient(ServerMessageRouter router) { this.router = router; }

    /**
     * Opens a TCP socket to {@code host:port} and initialises the encoder/decoder.
     * Does NOT start the reader thread — call {@link #startListening()} after.
     *
     * @throws IOException if the socket cannot be opened
     */
    public void connect(String host, int port) throws IOException {
        if (connected) {
            throw new IllegalStateException("GameClient already connected");
        }
        this.socket  = new Socket(host, port);
        this.in      = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out     = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        this.encoder = new MessageEncoder(out);
        this.decoder = new MessageDecoder(in);
        this.connected = true;
        LOG.info("Connected to " + host + ":" + port);
    }

    /** Starts the reader thread (daemon). Safe to call only after {@link #connect}. */
    public void startListening() {
        if (!connected) {
            throw new IllegalStateException("Not connected — call connect() first");
        }
        readerThread = new Thread(this::readLoop, "client-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void readLoop() {
        try {
            while (connected) {
                MessageType type = decoder.readNextType();
                router.route(type, decoder);
            }
        } catch (EOFException eof) {
            LOG.info("Server closed the connection.");
        } catch (IOException e) {
            if (connected) LOG.error("Read error — disconnecting", e);
        } finally {
            disconnect();
        }
    }

    // ── Send methods (synchronized — called from JavaFX or game loop thread) ────

    public synchronized void sendJoinRequest(String displayName) {
        if (!connected) return;
        try {
            encoder.encodeJoinRequest(displayName);
            encoder.flush();
        } catch (IOException e) {
            LOG.error("sendJoinRequest failed", e);
            disconnect();
        }
    }

    public synchronized void sendReady() {
        if (!connected) return;
        try {
            encoder.encodeReady();
            encoder.flush();
        } catch (IOException e) {
            LOG.error("sendReady failed", e);
            disconnect();
        }
    }

    public synchronized void sendInput(boolean up, boolean down, boolean left,
                                       boolean right, boolean carry,
                                       boolean throwAction) {
        if (!connected) return;
        try {
            encoder.encodePlayerInput(up, down, left, right, carry, throwAction);
            encoder.flush();
        } catch (IOException e) {
            LOG.error("sendInput failed", e);
            disconnect();
        }
    }

    public synchronized void sendChat(String text) {
        if (!connected) return;
        try {
            encoder.encodeChatSend(text);
            encoder.flush();
        } catch (IOException e) {
            LOG.error("sendChat failed", e);
            disconnect();
        }
    }

    public boolean isConnected() { return connected; }

    /** Idempotent: closes the socket and stops the reader thread. */
    public void disconnect() {
        if (!connected) return;
        connected = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) { }
    }
}
