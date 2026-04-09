package com.identitycrisis.shared.net.client;

/** Sent by client when joining a game session. */
public record JoinRequestMessage(String displayName) { }
