package com.identitycrisis.client.input;

/** Immutable snapshot of one frame's input. */
public record InputSnapshot(
    boolean up, boolean down, boolean left, boolean right,
    boolean carry, boolean throwAction, boolean release, boolean chatToggle
) {}
