package com.identitycrisis.server.game;

import com.identitycrisis.server.net.ClientConnection;
import com.identitycrisis.server.net.GameServer;
import com.identitycrisis.shared.net.ChatMessageType;
import com.identitycrisis.shared.net.MessageEncoder;
import com.identitycrisis.shared.util.Logger;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class ChatManager {

    private static final Logger LOG = new Logger("ChatManager");
    private static final int MAX_CHAT_LENGTH = 200;

    private final GameServer server;

    public ChatManager(GameServer server) {
        this.server = server;
    }

    public void handleChat(ClientConnection sender, String rawText) {
        if (sender == null || rawText == null) {
            return;
        }

        String text = sanitizeText(rawText);
        if (text.isEmpty()) {
            return;
        }

        String senderName = resolveSenderName(sender);
        broadcast(senderName, text, ChatMessageType.NORMAL);
    }

    public void broadcastPlayerJoined(ClientConnection client) {
        if (client == null) {
            return;
        }
        broadcast(resolveSenderName(client), "has joined the game", ChatMessageType.JOIN);
    }

    public void broadcastPlayerLeft(ClientConnection client) {
        if (client == null) {
            return;
        }
        broadcast(resolveSenderName(client), "has left the game", ChatMessageType.LEAVE);
    }

    private String sanitizeText(String rawText) {
        String text = rawText.trim();
        if (text.length() > MAX_CHAT_LENGTH) {
            text = text.substring(0, MAX_CHAT_LENGTH);
        }
        return text;
    }

    private String resolveSenderName(ClientConnection sender) {
        String name = sender.getDisplayName();
        if (name == null || name.isBlank()) {
            return "Player " + sender.getClientId();
        }
        return name;
    }

    private void broadcast(String senderName, String text, ChatMessageType messageType) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MessageEncoder enc = new MessageEncoder(new DataOutputStream(baos));
            enc.encodeChatBroadcast(senderName, text, messageType);
            enc.flush();
            server.broadcastToAll(baos.toByteArray());
        } catch (IOException e) {
            LOG.warn("Failed to broadcast chat message: " + e.getMessage());
        }
    }
}
