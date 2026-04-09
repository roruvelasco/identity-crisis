package com.identitycrisis.shared.net;

import java.io.ByteArrayOutputStream;
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

    public void encodeJoinRequest(String displayName) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream tmp = new DataOutputStream(buf);
        tmp.writeUTF(displayName);
        tmp.flush();
        byte[] payload = buf.toByteArray();
        writeHeader(MessageType.C_JOIN_REQUEST, payload.length);
        out.write(payload);
    }

    public void encodeReady() throws IOException {
        writeHeader(MessageType.C_READY, 0);
    }

    public void encodePlayerInput(boolean up, boolean down, boolean left,
                                  boolean right, boolean carry,
                                  boolean throwAction) throws IOException {
        int bits = 0;
        if (up)          bits |= 0x01;
        if (down)        bits |= 0x02;
        if (left)        bits |= 0x04;
        if (right)       bits |= 0x08;
        if (carry)       bits |= 0x10;
        if (throwAction) bits |= 0x20;
        writeHeader(MessageType.C_PLAYER_INPUT, 1);
        out.writeByte(bits);
    }

    public void encodeChatSend(String text) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream tmp = new DataOutputStream(buf);
        tmp.writeUTF(text);
        tmp.flush();
        byte[] payload = buf.toByteArray();
        writeHeader(MessageType.C_CHAT_SEND, payload.length);
        out.write(payload);
    }

    // ── Server → Client ────────────────────────────────────────────────────

    public void encodeLobbyState(int connectedCount, int requiredCount,
                                 String[] playerNames,
                                 boolean[] readyFlags) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream tmp = new DataOutputStream(buf);
        tmp.writeInt(connectedCount);
        tmp.writeInt(requiredCount);
        for (int i = 0; i < connectedCount; i++) {
            tmp.writeUTF(playerNames[i]);
            tmp.writeByte(readyFlags[i] ? 1 : 0);
        }
        tmp.flush();
        byte[] payload = buf.toByteArray();
        writeHeader(MessageType.S_LOBBY_STATE, payload.length);
        out.write(payload);
    }

    public void encodeGameState(int roundNumber, double timerRemaining,
                                byte phaseOrdinal, byte chaosOrdinal,
                                double chaosDuration, int controlledPlayerId,
                                PlayerNetData[] players,
                                SafeZoneNetData[] zones) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream tmp = new DataOutputStream(buf);
        tmp.writeInt(roundNumber);
        tmp.writeDouble(timerRemaining);
        tmp.writeByte(phaseOrdinal);
        tmp.writeByte(chaosOrdinal);
        tmp.writeDouble(chaosDuration);
        tmp.writeInt(controlledPlayerId);
        tmp.writeInt(players.length);
        for (PlayerNetData p : players) {
            tmp.writeInt(p.id());
            tmp.writeUTF(p.name());
            tmp.writeDouble(p.x());
            tmp.writeDouble(p.y());
            tmp.writeDouble(p.vx());
            tmp.writeDouble(p.vy());
            tmp.writeByte(p.stateOrdinal());
            tmp.writeInt(p.facing());
            tmp.writeByte(p.inSafeZone() ? 1 : 0);
            tmp.writeInt(p.carriedBy());
            tmp.writeInt(p.carrying());
        }
        tmp.writeInt(zones.length);
        for (SafeZoneNetData z : zones) {
            tmp.writeDouble(z.x());
            tmp.writeDouble(z.y());
            tmp.writeDouble(z.radius());
        }
        tmp.flush();
        byte[] payload = buf.toByteArray();
        writeHeader(MessageType.S_GAME_STATE, payload.length);
        out.write(payload);
    }

    public void encodeRoundState(int roundNumber, byte phaseOrdinal,
                                 double timerRemaining) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream tmp = new DataOutputStream(buf);
        tmp.writeInt(roundNumber);
        tmp.writeByte(phaseOrdinal);
        tmp.writeDouble(timerRemaining);
        tmp.flush();
        byte[] payload = buf.toByteArray();
        writeHeader(MessageType.S_ROUND_STATE, payload.length);
        out.write(payload);
    }

    public void encodeSafeZoneUpdate(double[] xs, double[] ys,
                                     double[] radii) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream tmp = new DataOutputStream(buf);
        tmp.writeInt(xs.length);
        for (int i = 0; i < xs.length; i++) {
            tmp.writeDouble(xs[i]);
            tmp.writeDouble(ys[i]);
            tmp.writeDouble(radii[i]);
        }
        tmp.flush();
        byte[] payload = buf.toByteArray();
        writeHeader(MessageType.S_SAFE_ZONE, payload.length);
        out.write(payload);
    }

    public void encodePlayerEliminated(int playerId,
                                       String playerName) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream tmp = new DataOutputStream(buf);
        tmp.writeInt(playerId);
        tmp.writeUTF(playerName);
        tmp.flush();
        byte[] payload = buf.toByteArray();
        writeHeader(MessageType.S_PLAYER_ELIMINATED, payload.length);
        out.write(payload);
    }

    public void encodeChaosEvent(byte chaosOrdinal,
                                 double duration) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream tmp = new DataOutputStream(buf);
        tmp.writeByte(chaosOrdinal);
        tmp.writeDouble(duration);
        tmp.flush();
        byte[] payload = buf.toByteArray();
        writeHeader(MessageType.S_CHAOS_EVENT, payload.length);
        out.write(payload);
    }

    public void encodeControlSwap(int newControlledPlayerId) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream tmp = new DataOutputStream(buf);
        tmp.writeInt(newControlledPlayerId);
        tmp.flush();
        byte[] payload = buf.toByteArray();
        writeHeader(MessageType.S_CONTROL_SWAP, payload.length);
        out.write(payload);
    }

    public void encodeGameOver(int winnerPlayerId,
                               String winnerName) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream tmp = new DataOutputStream(buf);
        tmp.writeInt(winnerPlayerId);
        tmp.writeUTF(winnerName);
        tmp.flush();
        byte[] payload = buf.toByteArray();
        writeHeader(MessageType.S_GAME_OVER, payload.length);
        out.write(payload);
    }

    public void encodeChatBroadcast(String senderName,
                                    String text) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        DataOutputStream tmp = new DataOutputStream(buf);
        tmp.writeUTF(senderName);
        tmp.writeUTF(text);
        tmp.flush();
        byte[] payload = buf.toByteArray();
        writeHeader(MessageType.S_CHAT_BROADCAST, payload.length);
        out.write(payload);
    }

    // ── Internal ───────────────────────────────────────────────────────────

    private void writeHeader(MessageType type,
                             int payloadLength) throws IOException {
        out.writeByte(type.getTag());
        out.writeShort(payloadLength);
    }

    public void flush() throws IOException { out.flush(); }

    // ── Inner data carriers ────────────────────────────────────────────────

    public record PlayerNetData(int id, String name, double x, double y,
                                double vx, double vy, byte stateOrdinal,
                                int facing, boolean inSafeZone,
                                int carriedBy, int carrying) { }

    public record SafeZoneNetData(double x, double y, double radius) { }
}
