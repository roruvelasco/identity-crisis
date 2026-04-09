package com.identitycrisis.shared.net;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Writes typed messages to DataOutputStream.
 * Wire format: [1B type][2B payload length][payload bytes]
 */
public class MessageEncoder {

    private final DataOutputStream out;

    public MessageEncoder(DataOutputStream out) { this.out = out; }

    // ── Client → Server ────────────────────────────────────────────────────

    public void encodeJoinRequest(String displayName) throws IOException { }

    public void encodeReady() throws IOException { }

    public void encodePlayerInput(boolean up, boolean down, boolean left,
                                  boolean right, boolean carry,
                                  boolean throwAction) throws IOException { }

    public void encodeChatSend(String text) throws IOException { }

    // ── Server → Client ────────────────────────────────────────────────────

    public void encodeLobbyState(int connectedCount, int requiredCount,
                                 String[] playerNames,
                                 boolean[] readyFlags) throws IOException { }

    public void encodeGameState(int roundNumber, double timerRemaining,
                                byte phaseOrdinal, byte chaosOrdinal,
                                double chaosDuration, int controlledPlayerId,
                                PlayerNetData[] players,
                                SafeZoneNetData[] zones) throws IOException { }

    public void encodeRoundState(int roundNumber, byte phaseOrdinal,
                                 double timerRemaining) throws IOException { }

    public void encodeSafeZoneUpdate(double[] xs, double[] ys,
                                     double[] radii) throws IOException { }

    public void encodePlayerEliminated(int playerId,
                                       String playerName) throws IOException { }

    public void encodeChaosEvent(byte chaosOrdinal,
                                 double duration) throws IOException { }

    public void encodeControlSwap(int newControlledPlayerId) throws IOException { }

    public void encodeGameOver(int winnerPlayerId,
                               String winnerName) throws IOException { }

    public void encodeChatBroadcast(String senderName,
                                    String text) throws IOException { }

    // ── Internal ───────────────────────────────────────────────────────────

    private void writeHeader(MessageType type,
                             int payloadLength) throws IOException { }

    public void flush() throws IOException { }

    // ── Inner data carriers ────────────────────────────────────────────────

    public record PlayerNetData(int id, String name, double x, double y,
                                double vx, double vy, byte stateOrdinal,
                                int facing, boolean inSafeZone,
                                int carriedBy, int carrying) { }

    public record SafeZoneNetData(double x, double y, double radius) { }
}
