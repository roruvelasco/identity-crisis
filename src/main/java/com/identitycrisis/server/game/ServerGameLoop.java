package com.identitycrisis.server.game;

import com.identitycrisis.server.net.GameServer;
import com.identitycrisis.server.physics.CollisionDetector;
import com.identitycrisis.server.physics.PhysicsEngine;
import com.identitycrisis.shared.model.GameConfig;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Authoritative game loop. Runs on its own thread at a fixed tick rate
 * ({@link GameConfig#TICK_RATE} tps).
 *
 * <p>Each tick:
 * <ol>
 *   <li>Drain {@link #inputQueue} → apply inputs via {@link PhysicsEngine}.</li>
 *   <li>Step physics ({@link PhysicsEngine#step}) and resolve collisions
 *       ({@link CollisionDetector#resolve}).</li>
 *   <li>Tick game managers in order: round → safe-zone → carry → chaos → elimination.</li>
 *   <li>Build a personalized {@code GameStateSnapshot} for every client and
 *       send via {@link GameServer#sendToClient}.</li>
 * </ol>
 *
 * <h2>DI pattern</h2>
 * All collaborators are <em>injected</em> via the constructor; none are created
 * here. The Composition Root ({@code ServerApp.main()}) is the only place that
 * calls {@code new} on these objects.
 *
 * <h2>Thread safety</h2>
 * <ul>
 *   <li>{@link #inputQueue} is a {@link ConcurrentLinkedQueue} — network threads
 *       enqueue, this thread drains. No lock needed.</li>
 *   <li>{@link #running} is {@code volatile} — {@link #stop()} from any thread
 *       is visible immediately.</li>
 *   <li>Do <em>NOT</em> call {@code Thread.interrupt()} on this thread while
 *       sockets are open — it will close them abruptly.</li>
 * </ul>
 */
public class ServerGameLoop implements Runnable {

    // ── Injected dependencies ────────────────────────────────────────────────
    private final GameServer         server;
    private final GameContext        ctx;
    private final PhysicsEngine      physics;
    private final CollisionDetector  collisions;

    // ── Owned internals ──────────────────────────────────────────────────────
    private final ConcurrentLinkedQueue<QueuedInput> inputQueue;
    private volatile boolean running;

    /**
     * Full constructor-injection entry point.
     *
     * @param server     the TCP server used to send per-client snapshots
     * @param ctx        all game-manager collaborators, pre-wired to the same GameState
     * @param physics    stateless physics integrator
     * @param collisions stateless collision resolver
     */
    public ServerGameLoop(GameServer server,
                          GameContext ctx,
                          PhysicsEngine physics,
                          CollisionDetector collisions) {
        this.server     = server;
        this.ctx        = ctx;
        this.physics    = physics;
        this.collisions = collisions;
        this.inputQueue = new ConcurrentLinkedQueue<>();
        this.running    = false;
    }

    // ── Runnable ─────────────────────────────────────────────────────────────

    @Override
    public void run() {
        running = true;
        long previousTime = System.nanoTime();

        while (running) {
            long currentTime = System.nanoTime();
            double dt = (currentTime - previousTime) / 1_000_000_000.0;
            previousTime = currentTime;

            processInputs();
            update(dt);
            broadcastState();

            sleepUntilNextTick(currentTime);
        }
    }

    // ── Private tick helpers ─────────────────────────────────────────────────

    private void processInputs() {
        // Drain the queue: apply each pending input to the authoritative GameState
        // via PhysicsEngine.applyInput().
    }

    private void update(double dt) {
        physics.step(ctx.gameState(), dt);
        collisions.resolve(ctx.gameState());
        ctx.roundManager().tick(dt);
        ctx.safeZoneManager().updateOccupancy();
        ctx.carryManager().tick(dt);
        ctx.chaosEventManager().tick(dt);
    }

    private void broadcastState() {
        // For EACH client: build personalized GameStateSnapshot
        // (different safeZones list, different controlledPlayerId after swap).
        // Encode and send via server.sendToClient(client, encoded).
    }

    private void sleepUntilNextTick(long tickStartNs) {
        long elapsed = System.nanoTime() - tickStartNs;
        long sleepNs = GameConfig.TICK_DURATION_NS - elapsed;
        if (sleepNs > 0) {
            try {
                Thread.sleep(sleepNs / 1_000_000, (int) (sleepNs % 1_000_000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Thread-safe: called from network (ClientConnection reader) threads.
     * Drops silently if the queue is full — the client will send again next frame.
     */
    public void enqueueInput(int clientId, boolean[] inputFlags) {
        if (inputQueue.size() < GameConfig.MAX_QUEUED_INPUTS) {
            inputQueue.offer(new QueuedInput(clientId, inputFlags));
        }
        // else: silently drop — prevents memory DoS from malicious/laggy clients
    }

    /**
     * Signals the game loop to exit cleanly after the current tick completes.
     * Safe to call from any thread. Do NOT use {@code Thread.interrupt()} —
     * it closes sockets abruptly.
     */
    public void stop() {
        running = false;
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    /** Immutable record of a single client's input snapshot queued for processing. */
    public record QueuedInput(int clientId, boolean[] flags) { }
}
