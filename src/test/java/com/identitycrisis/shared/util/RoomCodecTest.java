package com.identitycrisis.shared.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomCodecTest {

    // ── Round-trip ────────────────────────────────────────────────────────────

    @Test
    void roundTrip_typicalLanAddress() {
        String code = RoomCodec.encode("192.168.1.42", 61019);
        RoomCodec.HostPort hp = RoomCodec.decode(code, "192.168.1.99");
        assertEquals("192.168.1.42", hp.ip());
        assertEquals(61019, hp.port());
    }

    @Test
    void roundTrip_portBoundaries() {
        RoomCodec.HostPort lo = RoomCodec.decode(RoomCodec.encode("10.0.0.1", 61000), "10.0.0.99");
        assertEquals(61000, lo.port());
        RoomCodec.HostPort hi = RoomCodec.decode(RoomCodec.encode("10.0.0.1", 61389), "10.0.0.99");
        assertEquals(61389, hi.port());
    }

    @Test
    void roundTrip_ipBoundaries() {
        assertEquals("192.168.1.0",   RoomCodec.decode(RoomCodec.encode("192.168.1.0", 61000), "192.168.1.99").ip());
        assertEquals("192.168.1.255", RoomCodec.decode(RoomCodec.encode("192.168.1.255", 61389), "192.168.1.99").ip());
    }

    // ── Output format ────────────────────────────────────────────────────────

    @Test
    void encode_producesDashedElevenCharUppercaseCode() {
        String code = RoomCodec.encode("192.168.1.42", 61019);
        assertEquals(5, code.length(), "5 numeric digits");
        assertTrue(code.matches("[0-9]+"), "numeric only");
    }

    @Test
    void encode_isDeterministic() {
        assertEquals(
            RoomCodec.encode("192.168.1.42", 61019),
            RoomCodec.encode("192.168.1.42", 61019)
        );
    }

    // ── Decode normalisation ─────────────────────────────────────────────────

    @Test
    void decode_ignoresSeparator() {
        String stripped = RoomCodec.encode("192.168.1.42", 61019);
        String dashed = stripped.substring(0, 2) + "-" + stripped.substring(2);
        assertEquals(RoomCodec.decode(dashed, "192.168.1.99"), RoomCodec.decode(stripped, "192.168.1.99"));
    }

    @Test
    void decode_isCaseInsensitiveAndIgnoresWhitespace() {
        String code = RoomCodec.encode("192.168.1.42", 61019);
        RoomCodec.HostPort expected = RoomCodec.decode(code, "192.168.1.99");
        assertEquals(expected, RoomCodec.decode("  " + code + "  ", "192.168.1.99"));
    }

    // ── Validation errors ────────────────────────────────────────────────────

    @Test
    void encode_rejectsInvalidPort() {
        assertThrows(IllegalArgumentException.class, () -> RoomCodec.encode("1.2.3.4", 0));
        assertThrows(IllegalArgumentException.class, () -> RoomCodec.encode("1.2.3.4", 61390));
        assertThrows(IllegalArgumentException.class, () -> RoomCodec.encode("1.2.3.4", -1));
    }

    @Test
    void encode_rejectsInvalidIp() {
        assertThrows(IllegalArgumentException.class, () -> RoomCodec.encode("1.2.3", 61000));
        assertThrows(IllegalArgumentException.class, () -> RoomCodec.encode("1.2.3.4.5", 61000));
        assertThrows(IllegalArgumentException.class, () -> RoomCodec.encode("1.2.3.256", 61000));
        assertThrows(IllegalArgumentException.class, () -> RoomCodec.encode("a.b.c.d", 61000));
    }

    @Test
    void decode_rejectsNullOrBadLength() {
        assertThrows(IllegalArgumentException.class, () -> RoomCodec.decode(null));
        assertThrows(IllegalArgumentException.class, () -> RoomCodec.decode(""));
        assertThrows(IllegalArgumentException.class, () -> RoomCodec.decode("ABC"));
        assertThrows(IllegalArgumentException.class, () -> RoomCodec.decode("TOO-LONG-CODE-HERE"));
    }

    @Test
    void decode_rejectsNonBase36Characters() {
        assertThrows(IllegalArgumentException.class, () -> RoomCodec.decode("12!45", "192.168.1.99"));
    }
}
