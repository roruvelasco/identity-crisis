package com.identitycrisis.shared.net;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip tests for MessageEncoder + MessageDecoder.
 * Pattern: encode to ByteArrayOutputStream → decode from ByteArrayInputStream → assert fields match.
 */
class MessageCodecTest {

    // ── Helpers ────────────────────────────────────────────────────────────────

    private record Pair(MessageEncoder enc, ByteArrayOutputStream buf) { }

    private Pair encoder() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        return new Pair(new MessageEncoder(new DataOutputStream(buf)), buf);
    }

    private MessageDecoder decoder(ByteArrayOutputStream buf) {
        return new MessageDecoder(new DataInputStream(new ByteArrayInputStream(buf.toByteArray())));
    }

    // ── Client → Server ────────────────────────────────────────────────────────

    @Test
    void joinRequest_roundTrip() throws Exception {
        Pair p = encoder();
        p.enc().encodeJoinRequest("Alice");
        p.enc().flush();

        MessageDecoder dec = decoder(p.buf());
        assertEquals(MessageType.C_JOIN_REQUEST, dec.readNextType());
        assertEquals("Alice", dec.decodeJoinRequest());
    }

    @Test
    void joinRequest_emptyName_roundTrip() throws Exception {
        Pair p = encoder();
        p.enc().encodeJoinRequest("");
        p.enc().flush();

        MessageDecoder dec = decoder(p.buf());
        assertEquals(MessageType.C_JOIN_REQUEST, dec.readNextType());
        assertEquals("", dec.decodeJoinRequest());
    }

    @Test
    void ready_roundTrip() throws Exception {
        Pair p = encoder();
        p.enc().encodeReady();
        p.enc().flush();

        MessageDecoder dec = decoder(p.buf());
        assertEquals(MessageType.C_READY, dec.readNextType());
        dec.decodeReady(); // must not throw
    }

    @Test
    void playerInput_allFlagsTrue_roundTrip() throws Exception {
        Pair p = encoder();
        p.enc().encodePlayerInput(true, true, true, true, true, true);
        p.enc().flush();

        MessageDecoder dec = decoder(p.buf());
        assertEquals(MessageType.C_PLAYER_INPUT, dec.readNextType());
        boolean[] flags = dec.decodePlayerInput();
        assertArrayEquals(new boolean[]{true, true, true, true, true, true}, flags);
    }

    @Test
    void playerInput_allFlagsFalse_roundTrip() throws Exception {
        Pair p = encoder();
        p.enc().encodePlayerInput(false, false, false, false, false, false);
        p.enc().flush();

        MessageDecoder dec = decoder(p.buf());
        assertEquals(MessageType.C_PLAYER_INPUT, dec.readNextType());
        boolean[] flags = dec.decodePlayerInput();
        assertArrayEquals(new boolean[]{false, false, false, false, false, false}, flags);
    }

    @Test
    void playerInput_mixedFlags_roundTrip() throws Exception {
        Pair p = encoder();
        // up=T, down=F, left=T, right=F, carry=F, throw=T
        p.enc().encodePlayerInput(true, false, true, false, false, true);
        p.enc().flush();

        MessageDecoder dec = decoder(p.buf());
        assertEquals(MessageType.C_PLAYER_INPUT, dec.readNextType());
        boolean[] flags = dec.decodePlayerInput();
        assertTrue(flags[0],  "up must be true");
        assertFalse(flags[1], "down must be false");
        assertTrue(flags[2],  "left must be true");
        assertFalse(flags[3], "right must be false");
        assertFalse(flags[4], "carry must be false");
        assertTrue(flags[5],  "throw must be true");
    }

    @Test
    void chatSend_roundTrip() throws Exception {
        Pair p = encoder();
        p.enc().encodeChatSend("Hello, world!");
        p.enc().flush();

        MessageDecoder dec = decoder(p.buf());
        assertEquals(MessageType.C_CHAT_SEND, dec.readNextType());
        assertEquals("Hello, world!", dec.decodeChatSend());
    }

    // ── Server → Client ────────────────────────────────────────────────────────

    @Test
    void lobbyState_roundTrip() throws Exception {
        Pair p = encoder();
        p.enc().encodeLobbyState(3, 4, 1,
            new String[]{"Alice", "Bob", "Charlie"},
            new boolean[]{true, false, true});
        p.enc().flush();

        MessageDecoder dec = decoder(p.buf());
        assertEquals(MessageType.S_LOBBY_STATE, dec.readNextType());
        MessageDecoder.LobbyStateData d = dec.decodeLobbyState();
        assertEquals(3, d.connectedCount());
        assertEquals(4, d.requiredCount());
        assertEquals(1, d.selfIndex());
        assertArrayEquals(new String[]{"Alice", "Bob", "Charlie"}, d.names());
        assertArrayEquals(new boolean[]{true, false, true}, d.ready());
    }

    @Test
    void gameState_roundTrip() throws Exception {
        MessageEncoder.PlayerNetData[] players = {
            new MessageEncoder.PlayerNetData(1, "Alice", 100.0, 200.0, 1.5, -0.5,
                (byte) 0, 2, true, -1, -1),
            new MessageEncoder.PlayerNetData(2, "Bob",   300.0, 400.0, 0.0,  0.0,
                (byte) 1, 0, false, 1, -1)
        };
        MessageEncoder.SafeZoneNetData[] zones = {
            new MessageEncoder.SafeZoneNetData(3, 500.0, 300.0, 64.0, 32.0)
        };

        Pair p = encoder();
        p.enc().encodeGameState(3, 12.5, (byte) 2, (byte) 0, 0.0, 1, 7, players, zones);
        p.enc().flush();

        MessageDecoder dec = decoder(p.buf());
        assertEquals(MessageType.S_GAME_STATE, dec.readNextType());
        MessageDecoder.GameStateData d = dec.decodeGameState();

        assertEquals(3,    d.roundNumber());
        assertEquals(12.5, d.timerRemaining(), 1e-9);
        assertEquals(2,    d.phaseOrdinal());
        assertEquals(0,    d.chaosOrdinal());
        assertEquals(0.0,  d.chaosDuration(), 1e-9);
        assertEquals(1,    d.controlledPlayerId());
        assertEquals(7,    d.selfPlayerId());

        assertEquals(2, d.players().length);
        assertEquals(1,     d.players()[0].id());
        assertEquals("Alice", d.players()[0].name());
        assertEquals(100.0, d.players()[0].x(), 1e-9);
        assertEquals(200.0, d.players()[0].y(), 1e-9);
        assertEquals(1.5,   d.players()[0].vx(), 1e-9);
        assertEquals(-0.5,  d.players()[0].vy(), 1e-9);
        assertEquals(0,     d.players()[0].stateOrdinal());
        assertEquals(2,     d.players()[0].facing());
        assertTrue(d.players()[0].inSafeZone());
        assertEquals(-1,    d.players()[0].carriedBy());
        assertEquals(-1,    d.players()[0].carrying());

        assertEquals(2,    d.players()[1].id());
        assertFalse(d.players()[1].inSafeZone());
        assertEquals(1,    d.players()[1].carriedBy());

        assertEquals(1,     d.zones().length);
        assertEquals(3,     d.zones()[0].id());
        assertEquals(500.0, d.zones()[0].x(), 1e-9);
        assertEquals(300.0, d.zones()[0].y(), 1e-9);
        assertEquals(64.0,  d.zones()[0].w(), 1e-9);
        assertEquals(32.0,  d.zones()[0].h(), 1e-9);
    }

    @Test
    void safeZoneUpdate_roundTrip() throws Exception {
        Pair p = encoder();
        p.enc().encodeSafeZoneUpdate(
            new int[]    {1, 4, 7},
            new double[] {10.0, 50.0, 90.0},
            new double[] {20.0, 60.0, 100.0},
            new double[] {32.0, 48.0, 16.0},
            new double[] {32.0, 32.0, 64.0});
        p.enc().flush();

        MessageDecoder dec = decoder(p.buf());
        assertEquals(MessageType.S_SAFE_ZONE, dec.readNextType());
        MessageDecoder.SafeZoneData d = dec.decodeSafeZoneUpdate();
        assertArrayEquals(new int[]{1, 4, 7}, d.ids());
        assertArrayEquals(new double[]{10.0, 50.0, 90.0},   d.xs(), 1e-9);
        assertArrayEquals(new double[]{20.0, 60.0, 100.0},  d.ys(), 1e-9);
        assertArrayEquals(new double[]{32.0, 48.0, 16.0},   d.ws(), 1e-9);
        assertArrayEquals(new double[]{32.0, 32.0, 64.0},   d.hs(), 1e-9);
    }

    @Test
    void playerEliminated_roundTrip() throws Exception {
        Pair p = encoder();
        p.enc().encodePlayerEliminated(7, "Dave");
        p.enc().flush();

        MessageDecoder dec = decoder(p.buf());
        assertEquals(MessageType.S_PLAYER_ELIMINATED, dec.readNextType());
        MessageDecoder.EliminationData d = dec.decodePlayerEliminated();
        assertEquals(7,      d.playerId());
        assertEquals("Dave", d.playerName());
    }

    @Test
    void chaosEvent_roundTrip() throws Exception {
        Pair p = encoder();
        p.enc().encodeChaosEvent((byte) 2, 5.0);
        p.enc().flush();

        MessageDecoder dec = decoder(p.buf());
        assertEquals(MessageType.S_CHAOS_EVENT, dec.readNextType());
        MessageDecoder.ChaosEventData d = dec.decodeChaosEvent();
        assertEquals(2,   d.chaosOrdinal());
        assertEquals(5.0, d.duration(), 1e-9);
    }

    @Test
    void controlSwap_roundTrip() throws Exception {
        Pair p = encoder();
        p.enc().encodeControlSwap(3);
        p.enc().flush();

        MessageDecoder dec = decoder(p.buf());
        assertEquals(MessageType.S_CONTROL_SWAP, dec.readNextType());
        assertEquals(3, dec.decodeControlSwap());
    }

    @Test
    void gameOver_roundTrip() throws Exception {
        Pair p = encoder();
        p.enc().encodeGameOver(5, "Eve");
        p.enc().flush();

        MessageDecoder dec = decoder(p.buf());
        assertEquals(MessageType.S_GAME_OVER, dec.readNextType());
        MessageDecoder.GameOverData d = dec.decodeGameOver();
        assertEquals(5,     d.winnerPlayerId());
        assertEquals("Eve", d.winnerName());
    }

    @Test
    void chatBroadcast_roundTrip() throws Exception {
        Pair p = encoder();
        p.enc().encodeChatBroadcast("Alice", "gg ez");
        p.enc().flush();

        MessageDecoder dec = decoder(p.buf());
        assertEquals(MessageType.S_CHAT_BROADCAST, dec.readNextType());
        MessageDecoder.ChatData d = dec.decodeChatBroadcast();
        assertEquals("Alice", d.senderName());
        assertEquals("gg ez", d.text());
    }

    @Test
    void multipleMessages_sequentialDecoding() throws Exception {
        Pair p = encoder();
        p.enc().encodeJoinRequest("Alice");
        p.enc().encodeReady();
        p.enc().encodePlayerInput(true, false, false, true, false, false);
        p.enc().flush();

        MessageDecoder dec = decoder(p.buf());
        assertEquals(MessageType.C_JOIN_REQUEST,  dec.readNextType());
        assertEquals("Alice", dec.decodeJoinRequest());
        assertEquals(MessageType.C_READY,         dec.readNextType());
        dec.decodeReady();
        assertEquals(MessageType.C_PLAYER_INPUT,  dec.readNextType());
        boolean[] f = dec.decodePlayerInput();
        assertTrue(f[0]);
        assertFalse(f[1]);
        assertFalse(f[2]);
        assertTrue(f[3]);
    }

    @Test
    void fromTag_unknownTag_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> MessageType.fromTag((byte) 0xFF));
    }
}
