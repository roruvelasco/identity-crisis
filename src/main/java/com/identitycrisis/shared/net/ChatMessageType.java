package com.identitycrisis.shared.net;

public enum ChatMessageType {
    NORMAL((byte) 0),
    JOIN((byte) 1),
    LEAVE((byte) 2);

    private final byte code;

    ChatMessageType(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static ChatMessageType fromCode(byte code) {
        for (ChatMessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return NORMAL;
    }
}
