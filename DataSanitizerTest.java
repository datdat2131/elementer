package com.astrax.elementer;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class DataSanitizerTest {
    private final ExpCurve curve = new ExpCurve(100, 1.12, 50, 1);
    private final Logger log = Logger.getLogger("test");

    private PlayerData run(PlayerSnapshot s) {
        return DataSanitizer.sanitize(s, "Name", curve, id -> id.equals("fire_fireball") ? 5 : null, log);
    }

    @Test
    void validDataSurvivesRoundTrip() {
        UUID id = UUID.randomUUID();
        PlayerData d = run(new PlayerSnapshot(id, "Old", "FIRE", 12, 40, 3, 2, 10, 5, 99, Map.of("fire_fireball", 3), Set.of("FIRE", "ICE"), false, false));
        assertEquals(Element.FIRE, d.getElement());
        assertEquals(12, d.getLevel());
        assertEquals(40, d.getExp());
        assertEquals(3, d.getSkillPoints());
        assertEquals(2, d.getSelectedSlot());
        assertEquals(3, d.skillLevel("fire_fireball"));
        assertTrue(d.hasUnlocked(Element.FIRE) && d.hasUnlocked(Element.ICE));
        assertEquals(2, d.unlockedCount());
        assertFalse(d.isAuraEnabled());
        assertFalse(d.isWingsEnabled());
        assertEquals("Name", d.getName());
        assertFalse(d.isDirty());
    }

    @Test
    void invalidValuesAreRepaired() {
        PlayerData d = run(new PlayerSnapshot(UUID.randomUUID(), "x", "NOPE", 999, -5, -3, 77, 0, 0, 0,
                Map.of("fire_fireball", 99, "ghost_skill", 4), Set.of("NOPE"), true, true));
        assertNull(d.getElement());
        assertEquals(50, d.getLevel());
        assertEquals(0, d.getExp());
        assertEquals(0, d.getSkillPoints());
        assertEquals(4, d.getSelectedSlot()); // tối đa 4 ô kỹ năng
        assertEquals(5, d.skillLevel("fire_fireball"));
        assertFalse(d.skillLevelsView().containsKey("ghost_skill"));
    }

    @Test
    void expBelowRequirement() {
        PlayerData d = run(new PlayerSnapshot(UUID.randomUUID(), "x", "ICE", 1, 999_999, 0, 1, 0, 0, 0, Map.of(), Set.of(), true, true));
        assertEquals(99, d.getExp()); // yêu cầu cấp 1 = 100
    }

    @Test
    void legacyDataWithOnlyActiveElementBecomesOwned() {
        PlayerData d = run(new PlayerSnapshot(UUID.randomUUID(), "x", "WIND", 5, 0, 0, 1, 0, 0, 0, Map.of(), Set.of(), true, true));
        assertTrue(d.hasUnlocked(Element.WIND));
        assertEquals(1, d.unlockedCount());
    }

    @Test
    void activeElementIsAlwaysOwnedEvenIfMissingFromList() {
        PlayerData d = run(new PlayerSnapshot(UUID.randomUUID(), "x", "LIGHT", 5, 0, 0, 1, 0, 0, 0, Map.of(),
                Set.of("FIRE", "nonsense"), true, true));
        assertTrue(d.hasUnlocked(Element.LIGHT) && d.hasUnlocked(Element.FIRE));
        assertEquals(2, d.unlockedCount());
    }
}
