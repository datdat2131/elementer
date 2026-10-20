package com.astrax.elementer;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerDataTest {
    private final ExpCurve curve = new ExpCurve(100, 1.12, 50, 1);

    private PlayerData data() {
        return new PlayerData(UUID.randomUUID(), "t");
    }

    @Test
    void eachElementKeepsItsOwnLevelExpAndPoints() {
        PlayerData d = data();
        d.unlock(Element.FIRE);
        d.setElement(Element.FIRE);
        curve.addExp(d, 1000);
        int fireLevel = d.getLevel();
        int firePoints = d.getSkillPoints();
        assertTrue(fireLevel > 1);

        d.unlock(Element.ICE);
        d.setElement(Element.ICE); // nguyên tố mới: cày lại từ cấp 1
        assertEquals(1, d.getLevel());
        assertEquals(0, d.getExp());
        assertEquals(0, d.getSkillPoints());
        curve.addExp(d, 150);
        assertEquals(2, d.getLevel());

        d.setElement(Element.FIRE); // quay lại: tiến trình cũ còn nguyên
        assertEquals(fireLevel, d.getLevel());
        assertEquals(firePoints, d.getSkillPoints());
        assertEquals(2, d.getLevel(Element.ICE));
    }

    @Test
    void noElementMeansLevelOneAndSettersAreIgnored() {
        PlayerData d = data();
        d.setLevel(30);
        d.setExp(99);
        d.setSkillPoints(5);
        assertEquals(1, d.getLevel());
        assertEquals(0, d.getExp());
        assertEquals(0, d.getSkillPoints());
        assertEquals(1, d.getLevel(Element.WIND));
    }

    @Test
    void snapshotContainsEveryElementProgressAndActiveMirror() {
        PlayerData d = data();
        d.unlock(Element.FIRE);
        d.unlock(Element.WIND);
        d.setProgress(Element.FIRE, 20, 5, 2);
        d.setProgress(Element.WIND, 7, 1, 0);
        d.setElement(Element.WIND);
        PlayerSnapshot s = d.snapshot();
        assertEquals("WIND", s.element());
        assertEquals(7, s.level());
        assertEquals(new PlayerSnapshot.ProgressEntry(20, 5, 2), s.progress().get("FIRE"));
        assertEquals(new PlayerSnapshot.ProgressEntry(7, 1, 0), s.progress().get("WIND"));
    }

    @Test
    void resetClearsAllProgressAndOwnership() {
        PlayerData d = data();
        d.unlock(Element.EARTH);
        d.setElement(Element.EARTH);
        d.setProgress(Element.EARTH, 30, 0, 4);
        d.reset();
        assertNull(d.getElement());
        assertEquals(0, d.unlockedCount());
        assertEquals(1, d.getLevel(Element.EARTH));
        assertTrue(d.snapshot().progress().isEmpty());
    }

    @Test
    void settersClampAndMarkDirty() {
        PlayerData d = data();
        d.setElement(Element.LIGHT);
        d.clearDirty();
        d.setLevel(-3);
        assertEquals(1, d.getLevel());
        d.setSkillPoints(-5);
        assertEquals(0, d.getSkillPoints());
        assertTrue(d.isDirty());
    }
}
