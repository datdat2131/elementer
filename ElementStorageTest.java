package com.astrax.elementer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ElementStorageTest {
    @TempDir Path dir;
    private ElementStorage storage;
    private String url;

    @BeforeEach
    void setUp() {
        url = "jdbc:sqlite:" + dir.resolve("t.db");
        storage = new ElementStorage(url, Logger.getLogger("test"));
        storage.init().join();
    }

    @AfterEach
    void tearDown() { storage.close(); }

    private PlayerSnapshot snap(UUID id, String name, String el, int level, long exp) {
        return new PlayerSnapshot(id, name, el, level, exp, 4, 2, 100, 20000, 12345L,
                Map.of("fire_fireball", 3, "fire_blaze", 2), Set.of("FIRE", "WIND"), false, false);
    }

    @Test
    void saveThenLoadRoundTrip() {
        UUID id = UUID.randomUUID();
        storage.save(snap(id, "Alex", "FIRE", 12, 345)).join();
        PlayerSnapshot s = storage.load(id).join();
        assertNotNull(s);
        assertEquals("FIRE", s.element());
        assertEquals(12, s.level());
        assertEquals(345, s.exp());
        assertEquals(4, s.skillPoints());
        assertEquals(2, s.selectedSlot());
        assertEquals(3, s.skills().get("fire_fireball"));
        assertEquals(2, s.skills().get("fire_blaze"));
        assertEquals(java.util.Set.of("FIRE", "WIND"), s.unlocked());
        assertFalse(s.auraEnabled());
        assertFalse(s.wingsEnabled());
    }

    @Test
    void saveTwiceUpdatesInsteadOfDuplicating() {
        UUID id = UUID.randomUUID();
        storage.save(snap(id, "Alex", "FIRE", 1, 0)).join();
        storage.save(snap(id, "Alex", "ICE", 7, 9)).join();
        assertEquals("ICE", storage.load(id).join().element());
        assertEquals(1, storage.top(10).join().size());
    }

    @Test
    void loadMissingReturnsNull() {
        assertNull(storage.load(UUID.randomUUID()).join());
    }

    @Test
    void rewardCanOnlyBeClaimedOnce() {
        UUID id = UUID.randomUUID();
        assertTrue(storage.claimReward(id, "level:10").join());
        assertFalse(storage.claimReward(id, "level:10").join());
        assertTrue(storage.claimReward(id, "level:25").join());
        assertTrue(storage.claimReward(UUID.randomUUID(), "level:10").join());
    }

    @Test
    void concurrentClaimsGrantExactlyOnce() {
        UUID id = UUID.randomUUID();
        List<CompletableFuture<Boolean>> fs = new ArrayList<>();
        for (int i = 0; i < 50; i++) fs.add(storage.claimReward(id, "level:50"));
        long granted = fs.stream().filter(f -> f.join()).count();
        assertEquals(1, granted);
    }

    @Test
    void unclaimAllowsClaimAgain() {
        UUID id = UUID.randomUUID();
        assertTrue(storage.claimReward(id, "k").join());
        storage.unclaimReward(id, "k").join();
        assertTrue(storage.claimReward(id, "k").join());
    }

    @Test
    void topSortedByLevelThenExpAndSkipsUnawakened() {
        storage.save(snap(UUID.randomUUID(), "Low", "FIRE", 5, 10)).join();
        storage.save(snap(UUID.randomUUID(), "High", "ICE", 20, 1)).join();
        storage.save(snap(UUID.randomUUID(), "HighMoreExp", "WIND", 20, 500)).join();
        storage.save(snap(UUID.randomUUID(), "None", null, 50, 0)).join();
        List<TopEntry> top = storage.top(10).join();
        assertEquals(3, top.size());
        assertEquals("HighMoreExp", top.get(0).name());
        assertEquals("High", top.get(1).name());
        assertEquals("Low", top.get(2).name());
        assertEquals(1, storage.top(1).join().size());
    }

    @Test
    void corruptSkillStringDoesNotBreakLoad() throws Exception {
        UUID id = UUID.randomUUID();
        storage.save(snap(id, "Alex", "FIRE", 3, 0)).join();
        try (Connection c = DriverManager.getConnection(url);
             PreparedStatement ps = c.prepareStatement("UPDATE players SET skills = ? WHERE uuid = ?")) {
            ps.setString(1, "garbage;=5;a=;b=x;ok=2;;");
            ps.setString(2, id.toString());
            ps.executeUpdate();
        }
        PlayerSnapshot s = storage.load(id).join();
        assertEquals(Map.of("ok", 2), s.skills());
    }

    @Test
    void parseSkillsEdgeCases() {
        assertTrue(SkillCodec.parse(null).isEmpty());
        assertTrue(SkillCodec.parse("  ").isEmpty());
        assertEquals(Map.of("a", 1), SkillCodec.parse("a=1"));
        assertEquals("", SkillCodec.serialize(Map.of()));
        assertEquals(Set.of("A", "B"), SkillCodec.parseSet(" A, ,B,"));
        assertEquals("A,B", SkillCodec.serializeSet(Set.of("B", "A")));
    }

    @Test
    void oldDatabaseWithoutNewColumnsIsMigrated() throws Exception {
        storage.close();
        Path old = dir.resolve("old.db");
        String oldUrl = "jdbc:sqlite:" + old;
        UUID id = UUID.randomUUID();
        try (Connection c = DriverManager.getConnection(oldUrl); java.sql.Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE players (uuid TEXT PRIMARY KEY, name TEXT, element TEXT, level INTEGER NOT NULL DEFAULT 1,"
                    + "exp INTEGER NOT NULL DEFAULT 0, skill_points INTEGER NOT NULL DEFAULT 0, selected_slot INTEGER NOT NULL DEFAULT 1,"
                    + "daily_exp INTEGER NOT NULL DEFAULT 0, daily_day INTEGER NOT NULL DEFAULT 0, awakened_at INTEGER NOT NULL DEFAULT 0,"
                    + "skills TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL DEFAULT 0)");
            st.executeUpdate("INSERT INTO players (uuid, name, element, level) VALUES ('" + id + "', 'Old', 'ICE', 9)");
        }
        storage = new ElementStorage(oldUrl, Logger.getLogger("test"));
        storage.init().join();
        PlayerSnapshot s = storage.load(id).join();
        assertEquals("ICE", s.element());
        assertEquals(9, s.level());
        assertTrue(s.unlocked().isEmpty());
        assertTrue(s.auraEnabled());
        assertTrue(s.wingsEnabled());
    }

    @Test
    void voucherCanOnlyBeRedeemedOnceGlobally() {
        String id = UUID.randomUUID().toString();
        assertTrue(storage.claimVoucher(id, UUID.randomUUID()).join());
        assertFalse(storage.claimVoucher(id, UUID.randomUUID()).join()); // bản sao của người khác cũng bị từ chối
        assertTrue(storage.claimVoucher(UUID.randomUUID().toString(), UUID.randomUUID()).join());
    }

    @Test
    void concurrentVoucherClaimsGrantExactlyOnce() {
        String id = UUID.randomUUID().toString();
        List<CompletableFuture<Boolean>> fs = new ArrayList<>();
        for (int i = 0; i < 50; i++) fs.add(storage.claimVoucher(id, UUID.randomUUID()));
        assertEquals(1, fs.stream().filter(f -> f.join()).count());
    }

    @Test
    void unclaimedVoucherCanBeRedeemedAgain() {
        String id = UUID.randomUUID().toString();
        UUID p = UUID.randomUUID();
        assertTrue(storage.claimVoucher(id, p).join());
        storage.unclaimVoucher(id).join();
        assertTrue(storage.claimVoucher(id, p).join());
    }
}
