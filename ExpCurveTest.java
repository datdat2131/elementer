package com.astrax.elementer;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExpCurveTest {
    private final ExpCurve curve = new ExpCurve(100, 1.12, 50, 1);

    private PlayerData awakened() {
        PlayerData d = new PlayerData(UUID.randomUUID(), "t");
        d.setElement(Element.FIRE);
        return d;
    }

    @Test
    void requirementGrowsAndEndsAtMax() {
        assertEquals(100, curve.required(1));
        assertTrue(curve.required(2) > curve.required(1));
        assertTrue(curve.required(49) > curve.required(48));
        assertEquals(0, curve.required(50));
    }

    @Test
    void noExpWithoutElement() {
        PlayerData d = new PlayerData(UUID.randomUUID(), "t");
        assertEquals(0, curve.addExp(d, 1000));
        assertEquals(1, d.getLevel());
        assertEquals(0, d.getExp());
    }

    @Test
    void levelUpGivesSkillPointAndKeepsRemainder() {
        PlayerData d = awakened();
        assertEquals(1, curve.addExp(d, 150));
        assertEquals(2, d.getLevel());
        assertEquals(50, d.getExp());
        assertEquals(1, d.getSkillPoints());
    }

    @Test
    void multipleLevelsInOneGrant() {
        PlayerData d = awakened();
        long need = curve.required(1) + curve.required(2) + curve.required(3);
        assertEquals(3, curve.addExp(d, need));
        assertEquals(4, d.getLevel());
        assertEquals(0, d.getExp());
        assertEquals(3, d.getSkillPoints());
    }

    @Test
    void cappedAtMaxLevel() {
        PlayerData d = awakened();
        curve.addExp(d, Long.MAX_VALUE / 4);
        assertEquals(50, d.getLevel());
        assertEquals(0, d.getExp());
        int points = d.getSkillPoints();
        assertEquals(0, curve.addExp(d, 10_000));
        assertEquals(points, d.getSkillPoints());
    }

    @Test
    void ignoresNonPositive() {
        PlayerData d = awakened();
        assertEquals(0, curve.addExp(d, 0));
        assertEquals(0, curve.addExp(d, -50));
        assertEquals(0, d.getExp());
    }
}
