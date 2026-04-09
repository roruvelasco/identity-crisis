package com.identitycrisis.shared.net;

/**
 * Every wire message starts with a 1-byte type tag.
 * 0x01–0x3F = client→server. 0x40–0x7F = server→client.
 */
public enum MessageType {

    C_JOIN_REQUEST      ((byte) 0x01),
    C_READY             ((byte) 0x02),
    C_PLAYER_INPUT      ((byte) 0x03),
    C_CHAT_SEND         ((byte) 0x04),

    S_LOBBY_STATE       ((byte) 0x40),
    S_GAME_STATE        ((byte) 0x41),
    S_ROUND_STATE       ((byte) 0x42),
    S_SAFE_ZONE         ((byte) 0x43),
    S_PLAYER_ELIMINATED ((byte) 0x44),
    S_CHAOS_EVENT       ((byte) 0x45),
    S_CONTROL_SWAP      ((byte) 0x46),
    S_GAME_OVER         ((byte) 0x47),
    S_CHAT_BROADCAST    ((byte) 0x48);

    private final byte tag;

    MessageType(byte tag) { this.tag = tag; }

    public byte getTag() { return tag; }

    /** Loop or map lookup. */
    public static MessageType fromTag(byte tag) {
        for (MessageType t : values()) {
            if (t.tag == tag) return t;
        }
        throw new IllegalArgumentException("Unknown message tag: 0x" + String.format("%02X", tag));
    }
}
