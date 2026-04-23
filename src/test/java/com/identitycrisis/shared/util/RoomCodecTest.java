package com.identitycrisis.shared.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomCodecTest {

    // ── Round-trip ────────────────────────────────────────────────────────────

    @Test
    void roundTrip_typicalLanAddress() {
        String code = RoomCodec.encode("192.168.1.42", 5137);
        RoomCodec.HostPort hp = RoomCodec.decode(code);
        assertEquals("192.168.1.42", hp.ip());
        assertEquals(5137, hp.port());
    }

    @Test
    void roundTrip_portBoundaries() {
        RoomCodec.HostPort lo = RoomCodec.decode(RoomCodec.encode("10.0.0.1", 1));
        assertEquals(1, lo.port());
        RoomCodec.HostPort hi = RoomCodec.decode(RoomCodec.encode("10.0.0.1", 65535));
        assertEquals(65535, hi.port());
    }

    @Test
    void roundTrip_ipBoundaries() {
        assertEquals("0.0.0.0",          RoomCodec.decode(RoomCodec.encode("0.0.0.0", 8080)).ip());
        assertEquals("255.255.255.255",  RoomCodec.decode(RoomCodec.encode("255.255.255.255", 8080)).ip());
    }

    // ── Output format ────────────────────────────────────────────────────────

    @Test
    void encode_producesDashedElevenCharUppercaseCode() {
        String code = RoomCodec.encode("192.168.1.42", 5137);
        assertEquals(11, code.length(), "10 chars + 1 separator dash");
        assertEquals('-', code.charAt(5), "separator in the middle");
        assertEquals(code.toUpperCase(), code, "uppercase only");
        assertTrue(code.replace("-", "").matches("[0-9A-Z]+"), "base-36 alphabet");
    }

    @Test
    void encode_isDeterministic() {
        assertEquals(
            RoomCodec.encode("192.168.1.42", 5137),
            RoomCodec.encode("192.168.1.42", 5137)
        );
    }

    // ── Decode normalisation ─────────────────────────────────────────────────

    @Test
    void decode_ignoresSeparator() {
        String dashed   = RoomCodec.encode("192.168.1.42", 5137);
        String stripped = dashed.replace("-", "");
        assertEquals(RoomCodec.decode(dashed), RoomCodec.decode(stripped));
    }

    @Test
    void decode_isCaseInsensitiveAndIgnoresWhitespace() {
        String code = RoomCodec.encode("192.168.1.42", 5137);
        RoomCodec.HostPort expected = RoomCodec.decode(code);
        assertEquals(expected, RoomCodec.decode("  " + code.toLowerCase() + "  "));
    }

    // ── Validation errors ────────────────────────────────────────────────────

    @Test
    void encode_rejectsInvalidPort() {
        assertThrows(IllegalArgumentException.class, () -> RoomCodec.encode("1.2.3.4", 0));
        assertThrows(IllegalArgumentException.class, () -> RoomCodec.encode("1.2.3.4", 65536));
        assertThrows(IllegalArgumentException.class, () -> RoomCodec.encode("1.2.3.4", -1));
    }

    @Test
    void encode_rejectsInvalidIp() {
        assertThrows(IllegalArgumentException.class, () -> RoomCodec.encode("1.2.3", 8080));
        assertThrows(IllegalArgumentException.class, () -> RoomCodec.encode("1.2.3.4.5", 8080));
        assertThrows(IllegalArgumentException.class, () -> RoomCodec.encode("1.2.3.256", 8080));
        assertThrows(IllegalArgumentException.class, () -> RoomCodec.encode("a.b.c.d", 8080));
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
        // 10 chars but contains a '!' which is not valid base-36
        assertThrows(IllegalArgumentException.class, () -> RoomCodec.decode("ABCDE-!1234"));
    }
}
