package com.identitycrisis.server.net;

import com.identitycrisis.shared.net.MessageDecoder;
import com.identitycrisis.shared.net.MessageType;

/**
 * Routes decoded client messages to server-side handlers.
 * Runs on ClientConnection's reader thread — must be thread-safe.
 */
public class ClientMessageRouter {

    private final GameServer server;

    public ClientMessageRouter(GameServer server) { this.server = server; }

    public void route(ClientConnection sender, MessageType type,
                      MessageDecoder decoder) {
        // switch (type):
        //   C_JOIN_REQUEST → lobbyManager.handleJoin(sender, ...)
        //   C_READY        → lobbyManager.handleReady(sender)
        //   C_PLAYER_INPUT → enqueue into ServerGameLoop
        //   C_CHAT_SEND    → broadcast chat to all
    }
}
