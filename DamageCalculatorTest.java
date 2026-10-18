package com.astrax.elementer;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DamageCalculatorTest {
    @Test
    void baseDamageAtLevelOne() {
        assertEquals(7.0, DamageCalculator.skillDamage(7, 1, 0.12, 1, 0.02, 1, 1, 40), 1e-9);
    }

    @Test
    void scalesWithSkillAndElementLevel() {
        double d = DamageCalculator.skillDamage(10, 3, 0.1, 11, 0.02, 1, 1, 100);
        assertEquals(10 * 1.2 * 1.2, d, 1e-9);
    }

    @Test
    void affinityAndPvpMultiply() {
        double d = DamageCalculator.skillDamage(10, 1, 0, 1, 0, 1.25, 0.6, 100);
        assertEquals(7.5, d, 1e-9);
    }

    @Test
    void neverExceedsCapOrGoesNegative() {
        assertEquals(40, DamageCalculator.skillDamage(1000, 5, 1, 50, 1, 1.5, 1, 40), 1e-9);
        assertEquals(0, DamageCalculator.skillDamage(-5, 1, 0, 1, 0, 1, 1, 40), 1e-9);
        assertEquals(0, DamageCalculator.skillDamage(Double.NaN, 1, 0, 1, 0, 1, 1, 40), 1e-9);
    }

    @Test
    void clampHandlesNaN() {
        assertEquals(0.5, DamageCalculator.clamp(Double.NaN, 0.5, 1.5));
        assertEquals(1.5, DamageCalculator.clamp(9, 0.5, 1.5));
    }

    @Test
    void affinityRingIsConsistent() {
        Map<Element, Set<Element>> adv = new EnumMap<>(Element.class);
        adv.put(Element.FIRE, EnumSet.of(Element.ICE));
        adv.put(Element.ICE, EnumSet.of(Element.WIND));
        AffinityTable t = new AffinityTable(1.25, 0.85, adv);
        assertEquals(1.25, t.multiplier(Element.FIRE, Element.ICE));
        assertEquals(0.85, t.multiplier(Element.ICE, Element.FIRE));
        assertEquals(1.0, t.multiplier(Element.FIRE, Element.EARTH));
        assertEquals(1.0, t.multiplier(Element.LIGHT, Element.FIRE));
        assertEquals(1.0, t.multiplier(null, Element.FIRE));
        assertEquals(1.0, t.multiplier(Element.FIRE, Element.FIRE));
    }

    @Test
    void elementParseAcceptsVietnameseAndEnglish() {
        assertEquals(Element.FIRE, Element.parse("hoa"));
        assertEquals(Element.ICE, Element.parse("Băng"));
        assertEquals(Element.LIGHTNING, Element.parse("loi"));
        assertEquals(Element.EARTH, Element.parse("THO"));
        assertEquals(Element.WIND, Element.parse("phong"));
        assertEquals(Element.LIGHT, Element.parse("light"));
        assertNull(Element.parse("nope"));
        assertNull(Element.parse(null));
    }
}
