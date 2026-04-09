package com.identitycrisis.shared.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Vector2DTest {

    @Test
    void zero_hasOriginCoordinates() {
        Vector2D z = Vector2D.zero();
        assertEquals(0.0, z.x());
        assertEquals(0.0, z.y());
    }

    @Test
    void add_sumsComponents() {
        Vector2D a = new Vector2D(1.0, 2.0);
        Vector2D b = new Vector2D(3.0, 4.0);
        Vector2D result = a.add(b);
        assertEquals(4.0, result.x());
        assertEquals(6.0, result.y());
    }

    @Test
    void subtract_diffsComponents() {
        Vector2D a = new Vector2D(5.0, 7.0);
        Vector2D b = new Vector2D(2.0, 3.0);
        Vector2D result = a.subtract(b);
        assertEquals(3.0, result.x());
        assertEquals(4.0, result.y());
    }

    @Test
    void multiply_scalesComponents() {
        Vector2D v = new Vector2D(2.0, 3.0);
        Vector2D result = v.multiply(2.5);
        assertEquals(5.0, result.x());
        assertEquals(7.5, result.y());
    }

    @Test
    void magnitude_computesEuclideanLength() {
        Vector2D v = new Vector2D(3.0, 4.0);
        assertEquals(5.0, v.magnitude(), 1e-9);
    }

    @Test
    void distanceTo_isSymmetric() {
        Vector2D a = new Vector2D(0.0, 0.0);
        Vector2D b = new Vector2D(3.0, 4.0);
        assertEquals(5.0, a.distanceTo(b), 1e-9);
        assertEquals(5.0, b.distanceTo(a), 1e-9);
    }

    @Test
    void normalize_producesUnitVector() {
        Vector2D v = new Vector2D(3.0, 4.0);
        Vector2D unit = v.normalize();
        assertEquals(1.0, unit.magnitude(), 1e-9);
    }
}
