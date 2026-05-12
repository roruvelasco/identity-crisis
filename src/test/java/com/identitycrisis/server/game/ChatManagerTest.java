package com.identitycrisis.server.game;

import com.identitycrisis.server.net.ClientConnection;
import com.identitycrisis.server.net.ClientMessageRouter;
import com.identitycrisis.server.net.GameServer;
import com.identitycrisis.shared.net.ChatMessageType;
import com.identitycrisis.shared.net.MessageDecoder;
import com.identitycrisis.shared.net.MessageType;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import static org.junit.jupiter.api.Assertions.*;

class ChatManagerTest {

    @Test
    void handleChat_broadcastsSenderNameAndText() throws Exception {
        CapturingGameServer server = new CapturingGameServer();
        ChatManager manager = new ChatManager(server);
        try (ConnectionFixture fixture = ConnectionFixture.open(server)) {
            fixture.connection().setDisplayName("Alice");

            manager.handleChat(fixture.connection(), "hello");

            MessageDecoder.ChatData data = decodeBroadcast(server.lastBroadcast());
            assertEquals("Alice", data.senderName());
            assertEquals("hello", data.text());
            assertEquals(ChatMessageType.NORMAL, data.messageType());
        }
    }

    @Test
    void handleChat_trimsTextBeforeBroadcast() throws Exception {
        CapturingGameServer server = new CapturingGameServer();
        ChatManager manager = new ChatManager(server);
        try (ConnectionFixture fixture = ConnectionFixture.open(server)) {
            fixture.connection().setDisplayName("Bob");

            manager.handleChat(fixture.connection(), "  gg  ");

            MessageDecoder.ChatData data = decodeBroadcast(server.lastBroadcast());
            assertEquals("Bob", data.senderName());
            assertEquals("gg", data.text());
        }
    }

    @Test
    void handleChat_ignoresBlankText() throws Exception {
        CapturingGameServer server = new CapturingGameServer();
        ChatManager manager = new ChatManager(server);
        try (ConnectionFixture fixture = ConnectionFixture.open(server)) {
            fixture.connection().setDisplayName("Alice");

            manager.handleChat(fixture.connection(), "   ");

            assertNull(server.lastBroadcast());
        }
    }

    @Test
    void handleChat_ignoresNullText() throws Exception {
        CapturingGameServer server = new CapturingGameServer();
        ChatManager manager = new ChatManager(server);
        try (ConnectionFixture fixture = ConnectionFixture.open(server)) {
            fixture.connection().setDisplayName("Alice");

            manager.handleChat(fixture.connection(), null);

            assertNull(server.lastBroadcast());
        }
    }

    @Test
    void handleChat_truncatesLongText() throws Exception {
        CapturingGameServer server = new CapturingGameServer();
        ChatManager manager = new ChatManager(server);
        try (ConnectionFixture fixture = ConnectionFixture.open(server)) {
            fixture.connection().setDisplayName("Alice");

            manager.handleChat(fixture.connection(), "x".repeat(250));

            MessageDecoder.ChatData data = decodeBroadcast(server.lastBroadcast());
            assertEquals(200, data.text().length());
        }
    }

    @Test
    void handleChat_usesFallbackNameWhenDisplayNameBlank() throws Exception {
        CapturingGameServer server = new CapturingGameServer();
        ChatManager manager = new ChatManager(server);
        try (ConnectionFixture fixture = ConnectionFixture.open(server)) {
            fixture.connection().setDisplayName(" ");

            manager.handleChat(fixture.connection(), "hello");

            MessageDecoder.ChatData data = decodeBroadcast(server.lastBroadcast());
            assertEquals("Player " + fixture.connection().getClientId(), data.senderName());
            assertEquals("hello", data.text());
        }
    }

    @Test
    void broadcastPlayerJoined_broadcastsJoinMessageType() throws Exception {
        CapturingGameServer server = new CapturingGameServer();
        ChatManager manager = new ChatManager(server);
        try (ConnectionFixture fixture = ConnectionFixture.open(server)) {
            fixture.connection().setDisplayName("Alice");

            manager.broadcastPlayerJoined(fixture.connection());

            MessageDecoder.ChatData data = decodeBroadcast(server.lastBroadcast());
            assertEquals("Alice", data.senderName());
            assertEquals("has joined the game", data.text());
            assertEquals(ChatMessageType.JOIN, data.messageType());
        }
    }

    @Test
    void broadcastPlayerLeft_broadcastsLeaveMessageType() throws Exception {
        CapturingGameServer server = new CapturingGameServer();
        ChatManager manager = new ChatManager(server);
        try (ConnectionFixture fixture = ConnectionFixture.open(server)) {
            fixture.connection().setDisplayName("Bob");

            manager.broadcastPlayerLeft(fixture.connection());

            MessageDecoder.ChatData data = decodeBroadcast(server.lastBroadcast());
            assertEquals("Bob", data.senderName());
            assertEquals("has left the game", data.text());
            assertEquals(ChatMessageType.LEAVE, data.messageType());
        }
    }

    private static MessageDecoder.ChatData decodeBroadcast(byte[] payload) throws Exception {
        assertNotNull(payload);
        MessageDecoder decoder = new MessageDecoder(new DataInputStream(new ByteArrayInputStream(payload)));
        assertEquals(MessageType.S_CHAT_BROADCAST, decoder.readNextType());
        return decoder.decodeChatBroadcast();
    }

    private static final class CapturingGameServer extends GameServer {
        private byte[] lastBroadcast;

        private CapturingGameServer() {
            super(0);
        }

        @Override
        public void broadcastToAll(byte[] data) {
            this.lastBroadcast = data;
        }

        private byte[] lastBroadcast() {
            return lastBroadcast;
        }
    }

    private record ConnectionFixture(ClientConnection connection, Socket clientSocket) implements AutoCloseable {
        private static ConnectionFixture open(GameServer server) throws Exception {
            try (ServerSocket serverSocket = new ServerSocket(0)) {
                Socket clientSocket = new Socket("127.0.0.1", serverSocket.getLocalPort());
                Socket serverSocketSide = serverSocket.accept();
                ClientConnection connection = new ClientConnection(7, serverSocketSide, new ClientMessageRouter(server), server);
                return new ConnectionFixture(connection, clientSocket);
            }
        }

        @Override
        public void close() throws Exception {
            connection.disconnect();
            clientSocket.close();
        }
    }
}
