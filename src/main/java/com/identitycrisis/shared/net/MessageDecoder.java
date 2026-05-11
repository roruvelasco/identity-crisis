package com.identitycrisis.shared.net;

import java.io.ByteArrayInputStream;
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
    public MessageType readNextType() throws IOException {
        byte tag = in.readByte();
        int length = in.readUnsignedShort();
        payloadBuffer = new byte[length];
        in.readFully(payloadBuffer);
        return MessageType.fromTag(tag);
    }

    private DataInputStream payloadStream() {
        return new DataInputStream(new ByteArrayInputStream(payloadBuffer));
    }

    // ── Client → Server decoders ───────────────────────────────────────────

    public String decodeJoinRequest() {
        try { return payloadStream().readUTF(); }
        catch (IOException e) { throw new RuntimeException(e); }
    }

    public void decodeReady() { }

    /** Returns [up, down, left, right, carry, throw]. */
    public boolean[] decodePlayerInput() {
        try {
            int bits = payloadStream().readByte() & 0xFF;
            return new boolean[] {
                (bits & 0x01) != 0,
                (bits & 0x02) != 0,
                (bits & 0x04) != 0,
                (bits & 0x08) != 0,
                (bits & 0x10) != 0,
                (bits & 0x20) != 0
            };
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public String decodeChatSend() {
        try { return payloadStream().readUTF(); }
        catch (IOException e) { throw new RuntimeException(e); }
    }

    // ── Server → Client decoders ───────────────────────────────────────────

    public LobbyStateData decodeLobbyState() {
        try {
            DataInputStream p = payloadStream();
            int connectedCount = p.readInt();
            int requiredCount  = p.readInt();
            String[] names  = new String[connectedCount];
            boolean[] ready = new boolean[connectedCount];
            for (int i = 0; i < connectedCount; i++) {
                names[i] = p.readUTF();
                ready[i] = p.readByte() != 0;
            }
            return new LobbyStateData(connectedCount, requiredCount, names, ready);
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public GameStateData decodeGameState() {
        try {
            DataInputStream p = payloadStream();
            int roundNumber   = p.readInt();
            double timer      = p.readDouble();
            byte phase        = p.readByte();
            byte chaos        = p.readByte();
            double chaosDur   = p.readDouble();
            int controlledId  = p.readInt();
            int playerCount   = p.readInt();
            PlayerNetData[] players = new PlayerNetData[playerCount];
            for (int i = 0; i < playerCount; i++) {
                players[i] = new PlayerNetData(
                    p.readInt(), p.readUTF(),
                    p.readDouble(), p.readDouble(),
                    p.readDouble(), p.readDouble(),
                    p.readByte(), p.readInt(),
                    p.readByte() != 0,
                    p.readInt(), p.readInt()
                );
            }
            int zoneCount = p.readInt();
            SafeZoneNetData[] zones = new SafeZoneNetData[zoneCount];
            for (int i = 0; i < zoneCount; i++) {
                zones[i] = new SafeZoneNetData(
                    p.readInt(),
                    p.readDouble(), p.readDouble(),
                    p.readDouble(), p.readDouble());
            }
            return new GameStateData(roundNumber, timer, phase, chaos, chaosDur,
                                     controlledId, players, zones);
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public RoundStateData decodeRoundState() {
        try {
            DataInputStream p = payloadStream();
            int roundNumber     = p.readInt();
            byte phaseOrdinal   = p.readByte();
            double timerRemaining = p.readDouble();
            return new RoundStateData(roundNumber, phaseOrdinal, timerRemaining);
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public SafeZoneData decodeSafeZoneUpdate() {
        try {
            DataInputStream p = payloadStream();
            int count = p.readInt();
            int[] ids   = new int[count];
            double[] xs = new double[count];
            double[] ys = new double[count];
            double[] ws = new double[count];
            double[] hs = new double[count];
            for (int i = 0; i < count; i++) {
                ids[i] = p.readInt();
                xs[i]  = p.readDouble();
                ys[i]  = p.readDouble();
                ws[i]  = p.readDouble();
                hs[i]  = p.readDouble();
            }
            return new SafeZoneData(ids, xs, ys, ws, hs);
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public EliminationData decodePlayerEliminated() {
        try {
            DataInputStream p = payloadStream();
            int playerId      = p.readInt();
            String playerName = p.readUTF();
            return new EliminationData(playerId, playerName);
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public ChaosEventData decodeChaosEvent() {
        try {
            DataInputStream p   = payloadStream();
            byte chaosOrdinal   = p.readByte();
            double duration     = p.readDouble();
            return new ChaosEventData(chaosOrdinal, duration);
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public int decodeControlSwap() {
        try { return payloadStream().readInt(); }
        catch (IOException e) { throw new RuntimeException(e); }
    }

    public GameOverData decodeGameOver() {
        try {
            DataInputStream p  = payloadStream();
            int winnerPlayerId = p.readInt();
            String winnerName  = p.readUTF();
            return new GameOverData(winnerPlayerId, winnerName);
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public ChatData decodeChatBroadcast() {
        try {
            DataInputStream p = payloadStream();
            String senderName = p.readUTF();
            String text       = p.readUTF();
            return new ChatData(senderName, text);
        } catch (IOException e) { throw new RuntimeException(e); }
    }

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

    /**
     * Wire form of a single safe-zone rectangle in world-pixel space.  Mirror
     * of {@link MessageEncoder.SafeZoneNetData} — see that class for field
     * meanings.
     */
    public record SafeZoneNetData(int id, double x, double y, double w, double h) { }

    public record RoundStateData(int roundNumber, byte phaseOrdinal,
                                 double timerRemaining) { }

    /**
     * Bulk safe-zone update payload (S_SAFE_ZONE).  Arrays are aligned by
     * index: rectangle {@code i} is {@code (ids[i], xs[i], ys[i], ws[i], hs[i])}.
     */
    public record SafeZoneData(int[] ids, double[] xs, double[] ys,
                               double[] ws, double[] hs) { }

    public record EliminationData(int playerId, String playerName) { }

    public record ChaosEventData(byte chaosOrdinal, double duration) { }

    public record GameOverData(int winnerPlayerId, String winnerName) { }

    public record ChatData(String senderName, String text) { }
}
