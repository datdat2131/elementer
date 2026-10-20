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
        PlayerData d = run(new PlayerSnapshot(id, "Old", "FIRE", 12, 40, 3, 2, 10, 5, 99, Map.of("fire_fireball", 3), Set.of("FIRE", "ICE"), false, false, Map.of()));
        assertEquals(Element.FIRE, d.getElement());
        assertEquals(12, d.getLevel());
        assertEquals(40, d.getExp());
        assertEquals(3, d.getSkillPoints());
        assertEquals(2, d.getSelectedSlot());
        assertEquals(3, d.skillLevel("fire_fireball"));
        assertTrue(d.hasUnlocked(Element.FIRE) && d.hasUnlocked(Element.ICE));
        assertEquals(1, d.getLevel(Element.ICE)); // dữ liệu cũ: cấp chung chỉ gán cho nguyên tố đang dùng
        assertEquals(2, d.unlockedCount());
        assertFalse(d.isAuraEnabled());
        assertFalse(d.isWingsEnabled());
        assertEquals("Name", d.getName());
        assertFalse(d.isDirty());
    }

    @Test
    void invalidValuesAreRepaired() {
        PlayerData d = run(new PlayerSnapshot(UUID.randomUUID(), "x", "FIRE", 999, -5, -3, 77, 0, 0, 0,
                Map.of("fire_fireball", 99, "ghost_skill", 4), Set.of("NOPE"), true, true, Map.of()));
        assertEquals(Element.FIRE, d.getElement());
        assertEquals(50, d.getLevel());
        assertEquals(0, d.getExp());
        assertEquals(0, d.getSkillPoints());
        assertEquals(4, d.getSelectedSlot()); // tối đa 4 ô kỹ năng
        assertEquals(5, d.skillLevel("fire_fireball"));
        assertFalse(d.skillLevelsView().containsKey("ghost_skill"));
    }

    @Test
    void expBelowRequirement() {
        PlayerData d = run(new PlayerSnapshot(UUID.randomUUID(), "x", "ICE", 1, 999_999, 0, 1, 0, 0, 0, Map.of(), Set.of(), true, true, Map.of()));
        assertEquals(99, d.getExp()); // yêu cầu cấp 1 = 100
    }

    @Test
    void legacyDataWithOnlyActiveElementBecomesOwned() {
        PlayerData d = run(new PlayerSnapshot(UUID.randomUUID(), "x", "WIND", 5, 0, 0, 1, 0, 0, 0, Map.of(), Set.of(), true, true, Map.of()));
        assertTrue(d.hasUnlocked(Element.WIND));
        assertEquals(1, d.unlockedCount());
    }

    @Test
    void activeElementIsAlwaysOwnedEvenIfMissingFromList() {
        PlayerData d = run(new PlayerSnapshot(UUID.randomUUID(), "x", "LIGHT", 5, 0, 0, 1, 0, 0, 0, Map.of(),
                Set.of("FIRE", "nonsense"), true, true, Map.of()));
        assertTrue(d.hasUnlocked(Element.LIGHT) && d.hasUnlocked(Element.FIRE));
        assertEquals(2, d.unlockedCount());
    }

    @Test
    void unknownActiveElementLeavesPlayerUnawakened() {
        PlayerData d = run(new PlayerSnapshot(UUID.randomUUID(), "x", "NOPE", 30, 5, 2, 1, 0, 0, 0, Map.of(), Set.of(), true, true, Map.of()));
        assertNull(d.getElement());
        assertEquals(1, d.getLevel());
        assertEquals(0, d.getSkillPoints());
    }

    @Test
    void perElementProgressIsRestoredSeparately() {
        PlayerData d = run(new PlayerSnapshot(UUID.randomUUID(), "x", "ICE", 7, 12, 3, 1, 0, 0, 0, Map.of(),
                Set.of("FIRE", "ICE"), true, true,
                Map.of("FIRE", new PlayerSnapshot.ProgressEntry(20, 50, 6), "ICE", new PlayerSnapshot.ProgressEntry(4, 10, 1))));
        assertEquals(4, d.getLevel());                 // đang dùng ICE
        assertEquals(20, d.getLevel(Element.FIRE));
        assertEquals(50, d.getExp(Element.FIRE));
        assertEquals(6, d.getSkillPoints(Element.FIRE));
        assertEquals(1, d.getSkillPoints());
    }

    @Test
    void perElementProgressIsClamped() {
        PlayerData d = run(new PlayerSnapshot(UUID.randomUUID(), "x", "FIRE", 1, 0, 0, 1, 0, 0, 0, Map.of(),
                Set.of("FIRE", "WIND"), true, true,
                Map.of("FIRE", new PlayerSnapshot.ProgressEntry(0, -4, -2), "WIND", new PlayerSnapshot.ProgressEntry(999, 77, 3))));
        assertEquals(1, d.getLevel(Element.FIRE));
        assertEquals(0, d.getExp(Element.FIRE));
        assertEquals(50, d.getLevel(Element.WIND));
        assertEquals(0, d.getExp(Element.WIND));
    }
}
