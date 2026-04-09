package com.identitycrisis.shared.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerTest {

    @Test
    void constructor_setsCarryIdsToMinusOne() {
        Player p = new Player(1, "Alice");
        assertEquals(-1, p.getCarriedByPlayerId(),
            "carriedByPlayerId must default to -1, not 0 (which is a valid player ID)");
        assertEquals(-1, p.getCarryingPlayerId(),
            "carryingPlayerId must default to -1, not 0 (which is a valid player ID)");
    }

    @Test
    void constructor_setsStateToAlive() {
        Player p = new Player(2, "Bob");
        assertEquals(PlayerState.ALIVE, p.getState());
    }

    @Test
    void constructor_setsDisplayName() {
        Player p = new Player(3, "Charlie");
        assertEquals("Charlie", p.getDisplayName());
    }

    @Test
    void equals_trueForSamePlayerId() {
        Player a = new Player(1, "Alice");
        Player b = new Player(1, "AliceCopy");
        assertEquals(a, b, "Players with the same ID must be equal regardless of name");
    }

    @Test
    void equals_falseForDifferentPlayerId() {
        Player a = new Player(1, "Alice");
        Player b = new Player(2, "Alice");
        assertNotEquals(a, b);
    }

    @Test
    void hashCode_consistentWithEquals() {
        Player a = new Player(5, "Dave");
        Player b = new Player(5, "Dave");
        assertEquals(a.hashCode(), b.hashCode());
    }
}
