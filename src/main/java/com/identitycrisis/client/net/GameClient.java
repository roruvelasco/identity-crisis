package com.identitycrisis.client.net;

import com.identitycrisis.shared.net.*;
import com.identitycrisis.shared.util.Logger;
import java.io.*;
import java.net.Socket;

/**
 * TCP connection to server. Reader thread + synchronized send methods.
 *
 * Key performance settings applied on every socket:
 *   TCP_NODELAY = true  → disables Nagle's algorithm so small input
 *                          packets (4 bytes) are sent immediately instead of
 *                          being coalesced for up to 200 ms.
 *   SO_KEEPALIVE = true → lets the OS detect dead peers automatically.
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
    /** Timestamp (nanoTime) of the last sendInput call — used for rate-limiting. */
    private long lastInputSendNs = 0;
    /** Minimum gap between input sends: ~16 ms = 60 sends/sec (matches server tick). */
    private static final long INPUT_SEND_INTERVAL_NS = 16_000_000L;

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
        this.socket = new Socket(host, port);
        // ── Critical for low-latency multiplayer on localhost ──────────────────
        // TCP_NODELAY disables Nagle's algorithm. Without it the OS may buffer
        // small packets (our 4-byte input messages) for up to 200 ms before
        // sending them — making the game feel very laggy even on LAN.
        this.socket.setTcpNoDelay(true);
        this.socket.setKeepAlive(true);
        this.socket.setSoTimeout(0);
        this.in      = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 8192));
        // DataOutputStream is used DIRECTLY (no BufferedOutputStream wrapper)
        // so that every flush() goes straight to the socket — no double buffering.
        this.out     = new DataOutputStream(socket.getOutputStream());
        this.encoder = new MessageEncoder(out);
        this.decoder = new MessageDecoder(in);
        this.connected = true;
        LOG.info("Connected to " + host + ":" + port + " (TCP_NODELAY=true)");
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
        // Rate-limit to ~60 sends/sec so we never flood the server loop.
        // The server processes inputs at TICK_RATE (60 TPS); sending faster
        // than that only wastes bandwidth. On localhost this matters because
        // AnimationTimer can fire >60fps under some JVM configurations.
        long now = System.nanoTime();
        if (now - lastInputSendNs < INPUT_SEND_INTERVAL_NS) return;
        lastInputSendNs = now;
        try {
            encoder.encodePlayerInput(up, down, left, right, carry, throwAction);
            encoder.flush(); // flushes directly to OS socket buffer (no BufferedOutputStream)
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
