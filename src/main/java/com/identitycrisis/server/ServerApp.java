package com.identitycrisis.server;

import com.identitycrisis.server.game.*;
import com.identitycrisis.server.net.ClientMessageRouter;
import com.identitycrisis.server.net.GameServer;
import com.identitycrisis.server.physics.CollisionDetector;
import com.identitycrisis.server.physics.PhysicsEngine;
import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.shared.util.Logger;

/**
 * Server entry point and <strong>Composition Root</strong>.
 *
 * <h2>Composition Root pattern (manual / "poor-man's" DI)</h2>
 * <p>This is the <em>only</em> place in the server that calls {@code new} on
 * collaborating objects. Every class that needs to talk to another class
 * receives it through its constructor or a setter — never by constructing it
 * internally. This makes every class independently testable and keeps
 * responsibilities clearly separated.
 *
 * <h2>Wiring order</h2>
 * <pre>
 *  1. GameState                             (foundation — shared mutable state)
 *  2. Game managers (szm, cem, cm, em, rm)  (all injected with GameState)
 *  3. Physics (pe, cd)                      (stateless utilities)
 *  4. GameContext                           (groups game managers into one object)
 *  5. GameServer(port)                      (created first; router/lobby/loop set later)
 *  6. ClientMessageRouter(server)           (needs server reference)
 *  7. LobbyManager(server)                  (needs server reference)
 *  8. server.set*(router, lobbyManager)     (resolve circular refs via setter injection)
 *  9. ServerGameLoop(server, ctx, pe, cd)   (all deps now fully wired)
 * 10. server.setGameLoop(loop)              (complete the wiring)
 * 11. Register JVM shutdown hook            (clean socket teardown on Ctrl+C)
 * 12. server.start()                        (blocking accept loop)
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>
 *   mvn exec:java -Dexec.mainClass="com.identitycrisis.server.ServerApp"
 *   mvn exec:java -Dexec.mainClass="com.identitycrisis.server.ServerApp" -Dexec.args="5137"
 * </pre>
 */
public class ServerApp {

    private static final Logger LOG = new Logger("ServerApp");

    public static void main(String[] args) {
        int port = parsePort(args);
        LOG.info("Identity Crisis Server — starting on port " + port);

        // ── 1. Foundation ────────────────────────────────────────────────────
        GameState gameState = new GameState();

        // ── 2. Game managers (constructor-injected with GameState) ───────────
        SafeZoneManager    szm = new SafeZoneManager(gameState);
        ChaosEventManager  cem = new ChaosEventManager(gameState);
        CarryManager       cm  = new CarryManager(gameState);
        EliminationManager em  = new EliminationManager(gameState, cm);
        RoundManager       rm  = new RoundManager(gameState, szm, cem, em);

        // ── 3. Stateless physics utilities ───────────────────────────────────
        PhysicsEngine     pe = new PhysicsEngine();
        CollisionDetector cd = new CollisionDetector();

        // ── 4. GameContext — groups all game managers ─────────────────────────
        GameContext ctx = new GameContext(gameState, szm, cem, cm, em, rm);

        // ── 5-8. Network layer — setter injection for circular references ─────
        //  GameServer ↔ ClientMessageRouter ↔ LobbyManager form a triangle that
        //  can't be resolved by constructor injection alone. Setter injection
        //  breaks the cycle in a controlled, explicit way.
        GameServer          server      = new GameServer(port);
        ClientMessageRouter router      = new ClientMessageRouter(server);
        LobbyManager        lobbyMgr   = new LobbyManager(server);
        server.setRouter(router);
        server.setLobbyManager(lobbyMgr);
        lobbyMgr.setGameState(gameState);
        lobbyMgr.setSafeZoneManager(szm);

        // ── 9-10. Game loop — fully constructor-injected ──────────────────────
        ServerGameLoop loop = new ServerGameLoop(server, ctx, pe, cd);
        server.setGameLoop(loop);

        // ── 11. JVM shutdown hook — clean socket teardown on Ctrl+C ──────────
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutdown signal received — stopping server gracefully.");
            server.shutdown();
        }, "server-shutdown-hook"));

        // ── 12. Start — blocks on the accept loop ────────────────────────────
        server.start();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static int parsePort(String[] args) {
        if (args.length > 0) {
            try {
                int p = Integer.parseInt(args[0]);
                if (p < 1 || p > 65535) {
                    throw new IllegalArgumentException("Port out of range: " + p);
                }
                return p;
            } catch (NumberFormatException e) {
                LOG.warn("Invalid port argument '" + args[0] + "', using default.");
            }
        }
        return GameConfig.SERVER_PORT;
    }
}
