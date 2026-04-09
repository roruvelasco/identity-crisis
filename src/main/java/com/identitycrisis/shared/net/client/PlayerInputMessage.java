package com.identitycrisis.shared.net.client;

/** Input sent from client to server each tick. */
public record PlayerInputMessage(boolean up, boolean down, boolean left,
                                  boolean right, boolean carry,
                                  boolean throwAction) { }
