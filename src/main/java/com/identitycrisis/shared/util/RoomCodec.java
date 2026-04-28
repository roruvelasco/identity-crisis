package com.identitycrisis.shared.util;


public final class RoomCodec {

    private static final int  CODE_LENGTH = 10;
    private static final int  RADIX       = 36;
    private static final char SEPARATOR   = '-';

    private RoomCodec() {}

    // Encoding 

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
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port out of range: " + port);
        }
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4 address: " + ip);
        }
        long value = 0;
        for (int i = 0; i < 4; i++) {
            int octet;
            try {
                octet = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid IPv4 address: " + ip);
            }
            if (octet < 0 || octet > 255) {
                throw new IllegalArgumentException("Invalid IPv4 octet: " + octet);
            }
            value = (value << 8) | octet;
        }
        value = (value << 16) | (port & 0xFFFFL);

        String raw = Long.toString(value, RADIX).toUpperCase();
        // Zero-pad to CODE_LENGTH
        while (raw.length() < CODE_LENGTH) {
            raw = "0" + raw;
        }
        // Insert separator in the middle for readability
        return raw.substring(0, CODE_LENGTH / 2) + SEPARATOR + raw.substring(CODE_LENGTH / 2);
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
        // Strip separator and whitespace, normalise to uppercase
        String raw = code.replace(String.valueOf(SEPARATOR), "").trim().toUpperCase();
        if (raw.length() != CODE_LENGTH) {
            throw new IllegalArgumentException(
                "Invalid room code length (expected " + CODE_LENGTH + "): " + code);
        }
        long value;
        try {
            value = Long.parseLong(raw, RADIX);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid room code: " + code, e);
        }

        int port = (int) (value & 0xFFFFL);
        value >>= 16;
        int o4 = (int) (value & 0xFF); value >>= 8;
        int o3 = (int) (value & 0xFF); value >>= 8;
        int o2 = (int) (value & 0xFF); value >>= 8;
        int o1 = (int) (value & 0xFF);

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Decoded port out of range: " + port);
        }
        String ip = o1 + "." + o2 + "." + o3 + "." + o4;
        return new HostPort(ip, port);
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
