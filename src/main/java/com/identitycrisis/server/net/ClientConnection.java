package com.identitycrisis.server.net;

import com.identitycrisis.shared.net.MessageDecoder;
import com.identitycrisis.shared.net.MessageEncoder;
import com.identitycrisis.shared.net.MessageType;
import com.identitycrisis.shared.util.Logger;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Represents one connected TCP client. Implements {@link Runnable} so it can
 * run its blocking read loop on its own named daemon thread.
 *
 * Thread safety — synchronized send()
 * The game loop thread and the main server thread may both write to the same
 * client's socket concurrently (snapshot vs. lobby state). All writes MUST go
 * through {@link #send(byte[])} which is {@code synchronized} on this
 * {@code ClientConnection} instance. Never expose the raw
 * {@link DataOutputStream} to callers — doing so would bypass the lock and
 * cause interleaved/corrupted frames.
 *
 * DI note
 * The {@link MessageEncoder} and {@link MessageDecoder} are owned (created)
 * here because they are 1:1 wrappers around the socket streams and exist only
 * to serve this connection. Everything else ({@link ClientMessageRouter}) is
 * injected by the {@link GameServer} accept loop.
 */
public class ClientConnection implements Runnable {

    private static final Logger LOG = new Logger("ClientConnection");

    private final int clientId;
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final MessageDecoder decoder;
    private final ClientMessageRouter router;
    private final GameServer server;
    private volatile boolean connected;
    private String displayName;

    /**
     * @param clientId unique ID assigned by {@link GameServer}
     * @param socket   accepted TCP socket (already connected)
     * @param router   routes decoded messages to server subsystems
     * @throws IOException if stream creation fails (socket already closed, etc.)
     */
    public ClientConnection(int clientId, Socket socket,
            ClientMessageRouter router,
            GameServer server) throws IOException {
        this.clientId = clientId;
        this.socket = socket;
        this.router = router;
        this.server = server;
        // ── Critical: disable Nagle's algorithm on the server side ──────────────
        // Without TCP_NODELAY the OS buffers outgoing game-state snapshots
        // until the buffer fills or an ACK arrives, adding up to 200 ms of
        // artificial latency even on localhost.
        this.socket.setTcpNoDelay(true);
        this.socket.setKeepAlive(true);
        this.in = new DataInputStream(new java.io.BufferedInputStream(socket.getInputStream(), 8192));
        // Unbuffered output: we write pre-encoded byte[] chunks directly.
        // Using BufferedOutputStream here would re-introduce the coalescing we
        // just eliminated with TCP_NODELAY.
        this.out = new DataOutputStream(socket.getOutputStream());
        this.decoder = new MessageDecoder(in);
        this.connected = true;
    }

    // ── Runnable — reader thread ──────────────────────────────────────────────

    @Override
    public void run() {
        try {
            while (connected) {
                MessageType type = decoder.readNextType();
                router.route(this, type, decoder);
            }
        } catch (IOException e) {
            if (connected) {
                LOG.warn("Client " + clientId + " disconnected: " + e.getMessage());
            }
        } finally {
            disconnect();
            server.removeClient(this);
        }
    }

    // ── Write API — ALL writes must go through here ───────────────────────────

    /**
     * Sends raw encoded bytes to this client.
     *
     * <p>
     * <b>Synchronized</b> — safe to call from the game loop thread and the
     * main/lobby thread concurrently without interleaving bytes.
     */
    public synchronized void send(byte[] data) {
        if (!connected)
            return;
        try {
            out.write(data);
            out.flush();
        } catch (IOException e) {
            LOG.warn("Failed to send to client " + clientId + ": " + e.getMessage());
            disconnect();
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Closes the socket and marks this connection as disconnected. */
    public void disconnect() {
        if (!connected)
            return;
        connected = false;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public int getClientId() {
        return clientId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String name) {
        this.displayName = name;
    }

    public boolean isConnected() {
        return connected;
    }

    /**
     * @deprecated Do NOT use this method. Calling encoder methods directly writes
     *             to the raw socket stream without acquiring the synchronization
     *             lock
     *             held by {@link #send(byte[])}, which causes interleaved/corrupted
     *             frames under concurrent writes (game loop thread vs. lobby
     *             thread).
     *
     *             <p>
     *             <b>Correct pattern:</b>
     * 
     *             <pre>
     *   ByteArrayOutputStream baos = new ByteArrayOutputStream();
     *   MessageEncoder enc = new MessageEncoder(new DataOutputStream(baos));
     *   enc.encodeGameState(...);
     *   enc.flush();
     *   client.send(baos.toByteArray());
     *             </pre>
     */
    @Deprecated
    public MessageEncoder getEncoder() {
        throw new UnsupportedOperationException(
                "Direct encoder access bypasses send() synchronization. " +
                        "Encode to a ByteArrayOutputStream and pass byte[] to send() instead.");
    }

    /**
     * @deprecated Do NOT use this method. Call {@link #send(byte[])} instead.
     *             Direct stream access bypasses the synchronization lock and
     *             risks interleaved writes from concurrent threads.
     */
    @Deprecated
    public DataOutputStream getOutputStream() {
        throw new UnsupportedOperationException(
                "Direct output stream access is forbidden. Use send(byte[]) instead.");
    }
}
