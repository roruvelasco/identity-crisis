package com.identitycrisis.shared.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Encodes and decodes a host IP + port into a short human-readable room code.
 *
 * <h2>Format</h2>
 * A room code is a 10-character uppercase alphanumeric string, displayed with a
 * dash in the middle for readability: {@code XXXXX-XXXXX}. The dash is cosmetic
 * only and is ignored during decoding.
 *
 * <h2>Encoding</h2>
 * The four IPv4 octets and the 16-bit port are packed into a 48-bit {@code long}:
 * <pre>
 *   bits 47-40  octet 1
 *   bits 39-32  octet 2
 *   bits 31-24  octet 3
 *   bits 23-16  octet 4
 *   bits 15-0   port
 * </pre>
 * That {@code long} is then represented in base-36 (digits 0-9, letters A-Z),
 * zero-padded to exactly 10 characters.
 *
 * <h2>Example</h2>
 * {@code 192.168.1.42:5137} → {@code "0C0A8012B1"} → displayed as
 * {@code "0C0A8-012B1"}.
 */
public final class RoomCodec {

    private static final int  CODE_LENGTH = 5;
    private static final int  RADIX       = 36;
    public static final int ROOM_PORT_BASE = 61000;
    public static final int ROOM_PORT_COUNT = 390;
    private static final char SEPARATOR   = '-';

    private RoomCodec() {}

    // ── Encoding ─────────────────────────────────────────────────────────────

    /**
     * Encodes an IPv4 address and port into a 10-character room code
     * (displayed as XXXXX-XXXXX).
     *
     * @param ip   IPv4 address string, e.g. {@code "192.168.1.42"}
     * @param port TCP port in range 1–65535
     * @return     uppercase 11-character display code (10 chars + 1 dash)
     * @throws IllegalArgumentException if the IP is malformed or port is out of range
     */
    public static String encode(String ip, int port) {
        if (port < ROOM_PORT_BASE || port >= ROOM_PORT_BASE + ROOM_PORT_COUNT) {
            throw new IllegalArgumentException("Port out of range: " + port);
        }
        int[] parts = parseIpv4(ip);
        int value = parts[3] * ROOM_PORT_COUNT + (port - ROOM_PORT_BASE);
        String raw = Integer.toString(value, RADIX).toUpperCase();
        while (raw.length() < CODE_LENGTH) {
            raw = "0" + raw;
        }
        return raw;
    }

    // ── Decoding ─────────────────────────────────────────────────────────────

    /**
     * Decodes a room code back into the host IP and port.
     *
     * @param code the code returned by {@link #encode} (separator optional)
     * @return a {@link HostPort} record with the IP string and port
     * @throws IllegalArgumentException if the code is malformed
     */
    public static HostPort decode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Room code must not be null");
        }
        return decode(code, NetworkUtils.getLanIp());
    }

    public static HostPort decode(String code, String localIpForSubnet) {
        if (code == null) {
            throw new IllegalArgumentException("Room code must not be null");
        }
        String raw = code.replace(String.valueOf(SEPARATOR), "").trim().toUpperCase();
        if (raw.length() != CODE_LENGTH) {
            throw new IllegalArgumentException(
                "Invalid room code length (expected " + CODE_LENGTH + "): " + code);
        }
        int value;
        try {
            value = Integer.parseInt(raw, RADIX);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid room code: " + code, e);
        }
        int maxValue = 256 * ROOM_PORT_COUNT;
        if (value < 0 || value >= maxValue) {
            throw new IllegalArgumentException("Invalid room code: " + code);
        }

        int hostOctet = value / ROOM_PORT_COUNT;
        int port = ROOM_PORT_BASE + (value % ROOM_PORT_COUNT);
        int[] subnet = parseIpv4(localIpForSubnet);
        String ip = subnet[0] + "." + subnet[1] + "." + subnet[2] + "." + hostOctet;
        return new HostPort(ip, port);
    }

    public static List<HostPort> decodeCandidates(String code) {
        List<HostPort> candidates = new ArrayList<>();
        for (String localIp : NetworkUtils.getLanIpCandidates()) {
            try {
                HostPort candidate = decode(code, localIp);
                if (!candidates.contains(candidate)) {
                    candidates.add(candidate);
                }
            } catch (IllegalArgumentException ignored) {}
        }
        return candidates;
    }

    private static int[] parseIpv4(String ip) {
        String[] raw = ip.split("\\.", -1);
        if (raw.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + ip);
        }
        int[] parts = new int[4];
        for (int i = 0; i < 4; i++) {
            try {
                parts[i] = Integer.parseInt(raw[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid IPv4 address: " + ip);
            }
            if (parts[i] < 0 || parts[i] > 255) {
                throw new IllegalArgumentException("Invalid IPv4 octet: " + parts[i]);
            }
        }
        return parts;
    }

    // ── Inner type ────────────────────────────────────────────────────────────

    /**
     * Immutable holder for a decoded host address and port.
     *
     * @param ip   IPv4 address string
     * @param port TCP port
     */
    public record HostPort(String ip, int port) {
        @Override
        public String toString() {
            return ip + ":" + port;
        }
    }
}
