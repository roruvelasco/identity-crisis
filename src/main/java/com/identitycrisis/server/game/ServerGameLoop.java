package com.identitycrisis.server.game;

import com.identitycrisis.server.net.ClientConnection;
import com.identitycrisis.server.net.GameServer;
import com.identitycrisis.server.physics.CollisionDetector;
import com.identitycrisis.server.physics.PhysicsEngine;
import com.identitycrisis.shared.model.GameConfig;
import com.identitycrisis.shared.model.Player;
import com.identitycrisis.shared.model.SafeZone;
import com.identitycrisis.shared.net.MessageEncoder;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Authoritative game loop. Runs on its own thread at a fixed tick rate
 * ({@link GameConfig#TICK_RATE} tps).
 *
 * Each tick:
 * Drain {@link #inputQueue} -> apply inputs via {@link PhysicsEngine}.
 * Step physics ({@link PhysicsEngine#step}) and resolve collisions
 * ({@link CollisionDetector#resolve}).
 * Tick game managers in order: carry -> safe-zone -> chaos -> round
 * Build a personalized {@code GameStateSnapshot} for every client and
 * send via {@link GameServer#sendToClient}.
 *
 * DI pattern
 * All collaborators are injected via the constructor; none are created
 * here. The Composition Root ({@code ServerApp.main()}) is the only place that
 * calls {@code new} on these objects.
 *
 * Thread safety
 * {@link #inputQueue} is a {@link ConcurrentLinkedQueue} — network threads
 * enqueue, this thread drains. No lock needed.
 * {@link #running} is {@code volatile} — {@link #stop()} from any thread
 * is visible immediately.
 * Do not call {@code Thread.interrupt()} on this thread while
 * sockets are open — it will close them abruptly.
 */
public class ServerGameLoop implements Runnable {

    // ── Injected dependencies ────────────────────────────────────────────────
    private final GameServer server;
    private final GameContext ctx;
    private final PhysicsEngine physics;
    private final CollisionDetector collisions;

    // ── Owned internals ──────────────────────────────────────────────────────
    private final ConcurrentLinkedQueue<QueuedInput> inputQueue;
    private volatile boolean running;

    /**
     * Full constructor-injection entry point.
     *
     * @param server     the TCP server used to send per-client snapshots
     * @param ctx        all game-manager collaborators, pre-wired to the same
     *                   GameState
     * @param physics    stateless physics integrator
     * @param collisions stateless collision resolver
     */
    public ServerGameLoop(GameServer server,
            GameContext ctx,
            PhysicsEngine physics,
            CollisionDetector collisions) {
        this.server = server;
        this.ctx = ctx;
        this.physics = physics;
        this.collisions = collisions;
        this.inputQueue = new ConcurrentLinkedQueue<>();
        this.running = false;
    }

    // ── Runnable ─────────────────────────────────────────────────────────────

    @Override
    public void run() {
        running = true;
        // Fixed timestep: dt is always 1/TICK_RATE regardless of actual wall-clock
        // time.
        // This keeps physics and round timers deterministic across machines and load
        // spikes.
        final double dt = 1.0 / GameConfig.TICK_RATE;

        while (running) {
            long tickStart = System.nanoTime();

            processInputs();
            update(dt);
            broadcastState();

            sleepUntilNextTick(tickStart);
        }
    }

    // ── Private tick helpers ─────────────────────────────────────────────────

    private void processInputs() {
        QueuedInput qi;
        while ((qi = inputQueue.poll()) != null) {
            Map<Integer, Integer> controlMap = ctx.gameState().getControlMap();
            int controlledPlayer = controlMap.getOrDefault(qi.clientId(), qi.clientId());
            // REVERSED_CONTROLS inversion is handled client-side
            // (ClientGameLoop.applyChaosModifications).
            // The server receives already-inverted input and must not invert again.
            boolean[] f = qi.flags();
            physics.applyInput(ctx.gameState(), controlledPlayer,
                    f[0], f[1], f[2], f[3], false);
            if (f[4])
                ctx.carryManager().tryCarry(controlledPlayer);
            if (f[5])
                ctx.carryManager().throwCarried(controlledPlayer);
        }
    }

    private void update(double dt) {
        physics.step(ctx.gameState(), dt);
        collisions.resolve(ctx.gameState());
        ctx.carryManager().tick(dt);
        ctx.safeZoneManager().updateOccupancy();
        ctx.chaosEventManager().tick(dt);
        ctx.roundManager().tick(dt);
    }

    private void broadcastState() {
        GameState gs = ctx.gameState();
        List<Player> allPlayers = gs.getPlayers();
        boolean fakeSafeZones = ctx.chaosEventManager().isFakeSafeZonesActive();

        // Send dedicated S_PLAYER_ELIMINATED messages for this tick's eliminations
        List<Integer> eliminated = List.copyOf(gs.getPendingEliminationIds());
        gs.clearPendingEliminationIds();
        if (!eliminated.isEmpty()) {
            for (Integer pid : eliminated) {
                Player ep = gs.getPlayerById(pid);
                String eName = ep != null ? ep.getDisplayName() : "?";
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    MessageEncoder enc = new MessageEncoder(new DataOutputStream(baos));
                    enc.encodePlayerEliminated(pid, eName);
                    enc.flush();
                    server.broadcastToAll(baos.toByteArray());
                } catch (IOException e) {
                    /* continue */ }
            }
        }

        // Send S_GAME_OVER once when winner is known
        int winnerId = gs.getPendingGameOverWinnerId();
        if (winnerId != -1) {
            gs.setPendingGameOverWinnerId(-1);
            Player winner = gs.getPlayerById(winnerId);
            String winnerName = winner != null ? winner.getDisplayName() : "?";
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                MessageEncoder enc = new MessageEncoder(new DataOutputStream(baos));
                enc.encodeGameOver(winnerId, winnerName);
                enc.flush();
                server.broadcastToAll(baos.toByteArray());
            } catch (IOException e) {
                /* continue */ }
        }

        for (ClientConnection client : server.getClients()) {
            int clientId = client.getClientId();
            int controlledPlayerId = gs.getControlMap().getOrDefault(clientId, clientId);

            List<SafeZone> zones = ctx.safeZoneManager().generateClientSafeZones(clientId, fakeSafeZones);

            MessageEncoder.PlayerNetData[] playerData = new MessageEncoder.PlayerNetData[allPlayers.size()];
            for (int i = 0; i < allPlayers.size(); i++) {
                Player p = allPlayers.get(i);
                playerData[i] = new MessageEncoder.PlayerNetData(
                        p.getPlayerId(), p.getDisplayName(),
                        p.getPosition().x(), p.getPosition().y(),
                        p.getVelocity().x(), p.getVelocity().y(),
                        (byte) p.getState().ordinal(), p.getFacingDirection(),
                        p.isInSafeZone(), p.getCarriedByPlayerId(), p.getCarryingPlayerId());
            }

            MessageEncoder.SafeZoneNetData[] zoneData = new MessageEncoder.SafeZoneNetData[zones.size()];
            for (int i = 0; i < zones.size(); i++) {
                SafeZone z = zones.get(i);
                zoneData[i] = new MessageEncoder.SafeZoneNetData(
                        z.id(), z.x(), z.y(), z.w(), z.h());
            }

            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                MessageEncoder enc = new MessageEncoder(new DataOutputStream(baos));
                enc.encodeGameState(
                        gs.getRoundNumber(), gs.getRoundTimer(),
                        (byte) gs.getPhase().ordinal(),
                        (byte) gs.getActiveChaosEvent().ordinal(),
                        gs.getChaosEventTimer(), controlledPlayerId,
                        playerData, zoneData);
                enc.flush();
                server.sendToClient(client, baos.toByteArray());
            } catch (IOException e) {
                // Client may have disconnected — send() handles this gracefully
            }
        }
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
     * Signal the loop to exit cleanly. Do NOT use Thread.interrupt().
     */
    public void stop() {
        running = false;
    }

    /**
     * Releases any game-state resources held for the given client.
     * Called by {@link com.identitycrisis.server.net.GameServer#removeClient} when
     * a client disconnects so the carried/carrying player is not permanently stuck.
     * Safe to call from any thread — delegates to
     * {@link CarryManager#releaseCarry}.
     */
    public void cleanupClient(int clientId) {
        ctx.carryManager().releaseCarry(clientId);
        // Prune controlMap so no living client remains swapped onto the disconnected
        // player's character. Mirrors the same pruning done in EliminationManager.
        Map<Integer, Integer> cm = ctx.gameState().getControlMap();
        cm.remove(clientId);
        cm.replaceAll((cid, controlled) -> controlled.equals(clientId) ? cid : controlled);
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    /**
     * Immutable record of a single client's input snapshot queued for processing.
     */
    public record QueuedInput(int clientId, boolean[] flags) {
    }
}
