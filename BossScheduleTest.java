package com.astrax.elementer;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BossScheduleTest {
    private final List<BossSchedule.Candidate> pool = List.of(
            new BossSchedule.Candidate("fire", 1, 0),
            new BossSchedule.Candidate("ice", 1, 0),
            new BossSchedule.Candidate("wind", 2, 0),
            new BossSchedule.Candidate("dark", 1, 0.01),
            new BossSchedule.Candidate("dragon", 1, 0.01));

    @Test
    void legendaryRangesAreExactlyTheirChances() {
        assertEquals("dark", BossSchedule.pick(pool, 0.0, 0.5));
        assertEquals("dark", BossSchedule.pick(pool, 0.0099, 0.5));
        assertEquals("dragon", BossSchedule.pick(pool, 0.01, 0.5));
        assertEquals("dragon", BossSchedule.pick(pool, 0.0199, 0.5));
        assertNotEquals("dragon", BossSchedule.pick(pool, 0.02, 0.5));
    }

    @Test
    void normalBossesFollowWeights() {
        // tổng trọng số 4: fire [0,1), ice [1,2), wind [2,4)
        assertEquals("fire", BossSchedule.pick(pool, 0.5, 0.0));
        assertEquals("fire", BossSchedule.pick(pool, 0.5, 0.2499));
        assertEquals("ice", BossSchedule.pick(pool, 0.5, 0.25));
        assertEquals("wind", BossSchedule.pick(pool, 0.5, 0.5));
        assertEquals("wind", BossSchedule.pick(pool, 0.5, 0.9999999));
    }

    @Test
    void simulatedDistributionMatchesOnePercentEach() {
        Random rnd = new Random(12345);
        Map<String, Integer> count = new HashMap<>();
        int n = 400_000;
        for (int i = 0; i < n; i++) count.merge(BossSchedule.pick(pool, rnd.nextDouble(), rnd.nextDouble()), 1, Integer::sum);
        assertEquals(0.01, count.get("dark") / (double) n, 0.002);
        assertEquals(0.01, count.get("dragon") / (double) n, 0.002);
        double normal = (count.get("fire") + count.get("ice") + count.get("wind")) / (double) n;
        assertEquals(0.98, normal, 0.005);
        assertTrue(count.get("wind") > count.get("fire") * 1.7); // trọng số 2 so với 1
    }

    @Test
    void zeroWeightAndEmptyPools() {
        assertNull(BossSchedule.pick(List.of(), 0.5, 0.5));
        assertNull(BossSchedule.pick(List.of(new BossSchedule.Candidate("x", 0, 0)), 0.5, 0.5));
        assertEquals("only", BossSchedule.pick(List.of(new BossSchedule.Candidate("only", 3, 0)), 0.9, 0.9));
        // chỉ có huyền thoại: roll cao hơn tổng tỉ lệ thì không có boss thường để chọn
        assertNull(BossSchedule.pick(List.of(new BossSchedule.Candidate("d", 1, 0.01)), 0.5, 0.5));
    }

    @Test
    void configuredChancesChangeTheOdds() {
        List<BossSchedule.Candidate> ten = List.of(new BossSchedule.Candidate("n", 1, 0), new BossSchedule.Candidate("l", 1, 0.10));
        assertEquals("l", BossSchedule.pick(ten, 0.099, 0.5));
        assertEquals("n", BossSchedule.pick(ten, 0.10, 0.5));
    }

    @Test
    void remainingTimeCountsDownFromLastSpawn() {
        long h2 = 2 * 60 * 60 * 1000L;
        assertEquals(Long.MAX_VALUE, BossSchedule.remainingMs(1000, 0, h2)); // chưa đặt điểm spawn
        assertEquals(h2, BossSchedule.remainingMs(5000, 5000, h2));
        assertEquals(h2 - 60_000, BossSchedule.remainingMs(65_000, 5000, h2));
        assertEquals(0, BossSchedule.remainingMs(5000 + h2, 5000, h2));
        assertEquals(0, BossSchedule.remainingMs(5000 + h2 + 99, 5000, h2));
    }
}
