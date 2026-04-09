package com.identitycrisis.shared.net.client;

/** Sent by client to broadcast a chat message. */
public record ChatSendMessage(String text) { }
