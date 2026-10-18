package com.astrax.elementer;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CooldownTrackerTest {
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @Test
    void skillCooldownCountsDownAndExpires() {
        CooldownTracker c = new CooldownTracker();
        c.start(a, "s", 5000, 1000);
        assertEquals(5000, c.remaining(a, "s", 1000));
        assertEquals(2000, c.remaining(a, "s", 4000));
        assertEquals(0, c.remaining(a, "s", 6000));
    }

    @Test
    void cooldownsAreIsolatedPerPlayerAndSkill() {
        CooldownTracker c = new CooldownTracker();
        c.start(a, "s1", 5000, 0);
        assertEquals(0, c.remaining(b, "s1", 1));
        assertEquals(0, c.remaining(a, "s2", 1));
    }

    @Test
    void globalCooldownAndClear() {
        CooldownTracker c = new CooldownTracker();
        c.startGlobal(a, 700, 0);
        c.start(a, "s", 5000, 0);
        assertEquals(700, c.globalRemaining(a, 0));
        c.clear(a);
        assertEquals(0, c.globalRemaining(a, 0));
        assertEquals(0, c.remaining(a, "s", 0));
    }

    @Test
    void purgeKeepsActiveEntries() {
        CooldownTracker c = new CooldownTracker();
        c.start(a, "old", 100, 0);
        c.start(a, "new", 10_000, 0);
        c.purgeExpired(5000);
        assertEquals(0, c.remaining(a, "old", 5000));
        assertEquals(5000, c.remaining(a, "new", 5000));
    }
}
