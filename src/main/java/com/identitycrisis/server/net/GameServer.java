package com.identitycrisis.server.net;

import com.identitycrisis.server.game.LobbyManager;
import com.identitycrisis.server.game.ServerGameLoop;
import com.identitycrisis.shared.util.Logger;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TCP server. Accepts connections, manages the client list, and dispatches to
 * game subsystems.
 *
 * DI pattern — setter injection for circular references
 * {@code GameServer}, {@link ClientMessageRouter}, and {@link LobbyManager}
 * form a three-way circular reference that cannot be resolved by constructor
 * injection alone. The solution: construct {@code GameServer} with only its
 * port, then inject the router, lobby, and game loop via the three setters
 * ({@link #setRouter}, {@link #setLobbyManager}, {@link #setGameLoop}) from
 * the Composition Root ({@code ServerApp.main()}) before calling
 * {@link #start()}.
 *
 * Invariant checked at runtime: {@link #start()} throws
 * {@link IllegalStateException} if any mandatory dep is still {@code null}.
 *
 * Thread safety
 * {@link #clients} is a {@link CopyOnWriteArrayList} — rarely modified
 * (connect/disconnect) but iterated every tick for broadcast. COW is ideal
 * for this access pattern. Writes to individual clients go through
 * {@link ClientConnection#send(byte[])} which is internally
 * {@code synchronized}.
 */
public class GameServer {

    private static final Logger LOG = new Logger("GameServer");

    private final int port;
    private final List<ClientConnection> clients = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextClientId = new AtomicInteger(1);
    private ServerSocket serverSocket;
    private volatile boolean gameInProgress = false;

    // ── Setter-injected (circular ref trio) ──────────────────────────────────
    private ClientMessageRouter router;
    private LobbyManager lobbyManager;
    private ServerGameLoop gameLoop;

    /**
     * Minimal constructor — port only.
     * Use the three setters before calling {@link #start()}.
     */
    public GameServer(int port) {
        this.port = port;
    }

    // ── Setter injection ──────────────────────────────────────────────────────

    /** Inject the message router (must be called before {@link #start()}). */
    public void setRouter(ClientMessageRouter router) {
        this.router = router;
    }

    /** Inject the lobby manager (must be called before {@link #start()}). */
    public void setLobbyManager(LobbyManager lobbyManager) {
        this.lobbyManager = lobbyManager;
    }

    /**
     * Inject the game loop (called after it is constructed by {@code ServerApp}).
     */
    public void setGameLoop(ServerGameLoop gameLoop) {
        this.gameLoop = gameLoop;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Opens the {@link ServerSocket} and blocks accepting connections.
     * Each accepted connection gets its own named daemon thread.
     *
     * @throws IllegalStateException if router or lobbyManager were not injected
     */
    public void start() {
        if (router == null || lobbyManager == null) {
            throw new IllegalStateException(
                    "GameServer.start() called before router/lobbyManager were injected. " +
                            "Check ServerApp.main() composition order.");
        }
        try {
            serverSocket = new ServerSocket(port);
            LOG.info("Server listening on port " + port);
            LOG.info("[HEALTH OK] Identity Crisis server is up and waiting for players.");
            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                int id = nextClientId.getAndIncrement();
                if (gameInProgress) {
                    LOG.warn("Rejecting late connection " + id + " — game already in progress.");
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                    continue;
                }
                try {
                    ClientConnection conn = new ClientConnection(id, socket, router, this);
                    clients.add(conn);
                    Thread t = new Thread(conn, "client-conn-" + id);
                    t.setDaemon(true);
                    t.start();
                    LOG.info("Client " + id + " connected from " + socket.getInetAddress());
                } catch (IOException e) {
                    LOG.error("Failed to set up client connection " + id, e);
                }
            }
        } catch (IOException e) {
            if (serverSocket != null && serverSocket.isClosed()) {
                LOG.info("Server socket closed — shutting down.");
            } else {
                LOG.error("Server error", e);
            }
        }
    }

    /**
     * Called when lobby signals all ready. Starts the game loop on a named thread.
     */
    public void startGame() {
        if (gameLoop == null) {
            throw new IllegalStateException("gameLoop not injected before startGame()");
        }
        gameInProgress = true;
        Thread gameThread = new Thread(gameLoop, "server-game-loop");
        gameThread.setDaemon(false);
        gameThread.start();
    }

    /**
     * Removes a disconnected client from the active list and cleans up any
     * carry state involving that client's player so no other player gets stuck.
     */
    public void removeClient(ClientConnection client) {
        clients.remove(client);
        if (gameLoop != null) {
            gameLoop.cleanupClient(client.getClientId());
        }
        if (lobbyManager != null && !clients.isEmpty()) {
            lobbyManager.broadcastLobbyState();
        }
    }

    /** Broadcast raw bytes to ALL connected clients. */
    public void broadcastToAll(byte[] data) {
        for (ClientConnection client : clients) {
            client.send(data);
        }
    }

    /** Send raw bytes to a specific client. */
    public void sendToClient(ClientConnection client, byte[] data) {
        client.send(data);
    }

    /** Stops the game loop and closes all client connections. */
    public void shutdown() {
        if (gameLoop != null) {
            gameLoop.stop();
        }
        for (ClientConnection client : clients) {
            client.disconnect();
        }
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception ignored) {
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public List<ClientConnection> getClients() {
        return clients;
    }

    public LobbyManager getLobbyManager() {
        return lobbyManager;
    }

    public ServerGameLoop getGameLoop() {
        return gameLoop;
    }

    public int getPort() {
        return port;
    }
}
