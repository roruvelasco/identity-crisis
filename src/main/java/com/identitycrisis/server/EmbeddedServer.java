package com.identitycrisis.server;

import com.identitycrisis.server.game.*;
import com.identitycrisis.server.net.ClientMessageRouter;
import com.identitycrisis.server.net.GameServer;
import com.identitycrisis.server.physics.CollisionDetector;
import com.identitycrisis.server.physics.PhysicsEngine;
import com.identitycrisis.shared.util.Logger;

/**
 * Runs a full game server inside the client process on a background
 * daemon thread. Used by the host player's "Create Room" flow so that one
 * client can act as both host and player without needing a separate server
 * process.
 *
 * Usage (Create Room flow)
 * int port = NetworkUtils.findFreePort();
 * EmbeddedServer embedded = new EmbeddedServer();
 * embedded.start(port);
 *
 * String code = RoomCodec.encode(NetworkUtils.getLanIp(), port);
 * // display code to user ...
 *
 * gameClient.connect("localhost", port);
 * gameClient.sendJoinRequest(displayName);
 * sceneManager.showLobby();
 *
 * Wiring
 * Mirrors {@link ServerApp#main} exactly — same composition-root wiring order,
 * same objects, same shutdown hook registration. The only difference is that
 * {@link GameServer#start()} runs on a named daemon thread so the JavaFX
 * application thread is not blocked.
 *
 * Lifecycle
 * Call {@link #start(int)} once to bind and begin accepting connections.
 * Call {@link #stop()} to shut down cleanly (closes sockets, stops loop).
 * Do not call {@link #start(int)} more than once per instance.
 */
public class EmbeddedServer {

    private static final Logger LOG = new Logger("EmbeddedServer");

    private GameServer server;
    private int port;

    /**
     * Wires and starts the embedded server on the given port.
     * Returns immediately — the accept loop runs on a daemon thread.
     *
     * @param port TCP port to bind; use
     *             {@link com.identitycrisis.shared.util.NetworkUtils#findFreePort()}
     *             to obtain a free port automatically.
     * @throws IllegalStateException if this instance has already been started.
     */
    public void start(int port) {
        if (this.server != null) {
            throw new IllegalStateException("EmbeddedServer already started.");
        }
        this.port = port;
        LOG.info("Starting embedded server on port " + port);

        // ── Composition Root (mirrors ServerApp.main) ─────────────────────
        GameState gameState = new GameState();
        SafeZoneManager szm = new SafeZoneManager(gameState);
        ChaosEventManager cem = new ChaosEventManager(gameState);
        CarryManager cm = new CarryManager(gameState);
        EliminationManager em = new EliminationManager(gameState, cm);
        RoundManager rm = new RoundManager(gameState, szm, cem, em);
        PhysicsEngine pe = new PhysicsEngine();
        CollisionDetector cd = new CollisionDetector();
        GameContext ctx = new GameContext(gameState, szm, cem, cm, em, rm);

        server = new GameServer(port);
        ClientMessageRouter router = new ClientMessageRouter(server);
        LobbyManager lobbyMgr = new LobbyManager(server);
        server.setRouter(router);
        server.setLobbyManager(lobbyMgr);
        lobbyMgr.setGameState(gameState);
        lobbyMgr.setSafeZoneManager(szm);

        ServerGameLoop loop = new ServerGameLoop(server, ctx, pe, cd);
        server.setGameLoop(loop);

        // ── Accept loop on daemon thread ──────────────────────────────────
        Thread acceptThread = new Thread(() -> {
            try {
                server.start();
            } catch (Exception e) {
                LOG.error("Embedded server error", e);
            }
        }, "embedded-server-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();

        LOG.info("Embedded server running on port " + port);
    }

    /**
     * Stops the embedded server gracefully (closes all sockets, stops game loop).
     * Safe to call even if the server was never started.
     */
    public void stop() {
        if (server != null) {
            LOG.info("Stopping embedded server on port " + port);
            server.shutdown();
            server = null;
        }
    }

    /**
     * @return the port this server is (or was) bound to, or {@code -1} if not
     *         started.
     */
    public int getPort() {
        return server != null ? port : -1;
    }

    /** @return {@code true} while the server is running. */
    public boolean isRunning() {
        return server != null;
    }
}
