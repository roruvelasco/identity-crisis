package com.identitycrisis.shared.net;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * Reads framed messages from DataInputStream.
 * Usage: type = readNextType(); switch(type) { case X -> decodeX(); }
 */
public class MessageDecoder {

    private final DataInputStream in;
    private byte[] payloadBuffer;

    public MessageDecoder(DataInputStream in) { this.in = in; }

    /** Blocks until next message header. Returns type. */
    public MessageType readNextType() throws IOException { throw new UnsupportedOperationException("stub"); }

    // ── Client → Server decoders ───────────────────────────────────────────

    public String decodeJoinRequest() { throw new UnsupportedOperationException("stub"); }

    public void decodeReady() { }

    /** Returns [up, down, left, right, carry, throw]. */
    public boolean[] decodePlayerInput() { throw new UnsupportedOperationException("stub"); }

    public String decodeChatSend() { throw new UnsupportedOperationException("stub"); }

    // ── Server → Client decoders ───────────────────────────────────────────

    public LobbyStateData decodeLobbyState() { throw new UnsupportedOperationException("stub"); }

    public GameStateData decodeGameState() { throw new UnsupportedOperationException("stub"); }

    public RoundStateData decodeRoundState() { throw new UnsupportedOperationException("stub"); }

    public SafeZoneData decodeSafeZoneUpdate() { throw new UnsupportedOperationException("stub"); }

    public EliminationData decodePlayerEliminated() { throw new UnsupportedOperationException("stub"); }

    public ChaosEventData decodeChaosEvent() { throw new UnsupportedOperationException("stub"); }

    public int decodeControlSwap() { throw new UnsupportedOperationException("stub"); }

    public GameOverData decodeGameOver() { throw new UnsupportedOperationException("stub"); }

    public ChatData decodeChatBroadcast() { throw new UnsupportedOperationException("stub"); }

    // ── Decoded payload containers ─────────────────────────────────────────

    public record LobbyStateData(int connectedCount, int requiredCount,
                                 String[] names, boolean[] ready) { }

    public record GameStateData(int roundNumber, double timerRemaining,
                                byte phaseOrdinal, byte chaosOrdinal,
                                double chaosDuration, int controlledPlayerId,
                                PlayerNetData[] players,
                                SafeZoneNetData[] zones) { }

    public record PlayerNetData(int id, String name, double x, double y,
                                double vx, double vy, byte stateOrdinal,
                                int facing, boolean inSafeZone,
                                int carriedBy, int carrying) { }

    public record SafeZoneNetData(double x, double y, double radius) { }

    public record RoundStateData(int roundNumber, byte phaseOrdinal,
                                 double timerRemaining) { }

    public record SafeZoneData(double[] xs, double[] ys, double[] radii) { }

    public record EliminationData(int playerId, String playerName) { }

    public record ChaosEventData(byte chaosOrdinal, double duration) { }

    public record GameOverData(int winnerPlayerId, String winnerName) { }

    public record ChatData(String senderName, String text) { }
}
