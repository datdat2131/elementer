package com.astrax.elementer;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Kiểm tra các file YAML mẫu đi kèm (không cần server): đủ 6 nguyên tố × 3 kỹ năng, giá trị hợp lệ, có trần. */
class BundledConfigTest {
    private static YamlConfiguration yaml(String name) throws Exception {
        try (Reader r = new InputStreamReader(BundledConfigTest.class.getResourceAsStream("/" + name), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(r);
        }
    }

    @Test
    void allSixElementsDefined() throws Exception {
        ConfigurationSection root = yaml("elements.yml").getConfigurationSection("elements");
        assertNotNull(root);
        for (Element e : Element.values()) {
            ConfigurationSection s = root.getConfigurationSection(e.name());
            assertNotNull(s, "thiếu " + e);
            assertFalse(s.getString("name", "").isBlank());
            assertFalse(s.getString("color", "").isBlank());
            assertFalse(s.getString("symbol", "").isBlank());
        }
    }

    @Test
    void everyElementHasValidAura() throws Exception {
        ConfigurationSection root = yaml("elements.yml").getConfigurationSection("elements");
        for (Element e : Element.values()) {
            ConfigurationSection a = root.getConfigurationSection(e.name() + ".aura");
            assertNotNull(a, "thiếu aura " + e);
            assertFalse(a.getString("particle", "").isBlank(), e.name());
            assertTrue(java.util.Set.of("RING", "DOUBLE_RING", "SPIRAL", "HELIX", "DRAGON").contains(a.getString("pattern")), e.name());
            assertTrue(a.getDouble("radius") > 0 && a.getInt("points") >= 4, e.name());
        }
    }

    @Test
    void unlockRulesAreSane() throws Exception {
        YamlConfiguration cfg = yaml("config.yml");
        assertEquals(3, cfg.getInt("unlock.max-owned"));
        java.util.List<java.util.Map<?, ?>> reqs = cfg.getMapList("unlock.requirements");
        assertTrue(reqs.size() >= 3);
        assertEquals(0, ((Number) reqs.get(0).get("points")).intValue());
        int prev = 0;
        for (java.util.Map<?, ?> r : reqs) {
            int lv = ((Number) r.get("level")).intValue();
            assertTrue(lv >= prev && lv <= 50, "yêu cầu cấp phải không giảm và ≤ 50");
            prev = lv;
        }
        assertEquals("PAPER", cfg.getString("voucher.material"));
    }

    @Test
    void premiumElementsAreVoucherOnlyAndDragonHasWings() throws Exception {
        ConfigurationSection root = yaml("elements.yml").getConfigurationSection("elements");
        assertEquals("VOUCHER", root.getString("DARK.obtain"));
        assertEquals("VOUCHER", root.getString("DRAGON.obtain"));
        for (Element e : Element.values()) {
            if (e != Element.DARK && e != Element.DRAGON) assertNull(root.getString(e.name() + ".obtain"), e.name());
        }
        assertNotNull(root.getConfigurationSection("DRAGON.wings"));
        assertEquals("DRAGON", root.getString("DRAGON.aura.pattern"));
    }

    @Test
    void nothingCountersDragonAndLightCountersDark() throws Exception {
        ConfigurationSection adv = yaml("elements.yml").getConfigurationSection("affinity.advantages");
        for (String a : adv.getKeys(false)) assertFalse(adv.getStringList(a).contains("DRAGON"), a);
        assertTrue(adv.getStringList("LIGHT").contains("DARK"));
        assertTrue(adv.getStringList("DRAGON").contains("DARK"));
    }

    @Test
    void eachElementHasThreeValidSkills() throws Exception {
        ConfigurationSection root = yaml("skills.yml").getConfigurationSection("skills");
        assertNotNull(root);
        Map<Element, Set<Integer>> slots = new EnumMap<>(Element.class);
        double cap = yaml("config.yml").getDouble("combat.max-skill-damage");
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            Element el = Element.parse(s.getString("element"));
            assertNotNull(el, id);
            SkillType type = SkillType.valueOf(s.getString("type"));
            assertTrue(slots.computeIfAbsent(el, k -> new HashSet<>()).add(s.getInt("slot")), "trùng slot " + id);
            assertTrue(s.getDouble("cooldown") >= 1.0, "hồi chiêu quá thấp " + id);
            assertTrue(s.getInt("max-level") >= 1);
            assertTrue(s.getDouble("damage") <= cap, "sát thương gốc vượt trần " + id);
            if (type == SkillType.SHIELD) assertTrue(s.getDouble("power") > 0 && s.getDouble("duration") > 0, id);
            if (type == SkillType.HEAL_AREA) assertTrue(s.getDouble("power") > 0, id);
            if (s.getBoolean("ultimate")) assertTrue(s.getDouble("cooldown") >= 60, "tuyệt kỹ cần hồi chiêu dài " + id);
        }
        for (Element e : Element.values()) {
            Set<Integer> expected = (e == Element.DARK || e == Element.DRAGON) ? Set.of(1, 2, 3, 4) : Set.of(1, 2, 3);
            assertEquals(expected, slots.get(e), "kỹ năng của " + e);
        }
        assertEquals(26, root.getKeys(false).size());
    }

    @Test
    void controlDurationsRespectConfiguredCaps() throws Exception {
        double maxControl = yaml("config.yml").getDouble("combat.max-control-seconds");
        ConfigurationSection root = yaml("skills.yml").getConfigurationSection("skills");
        for (String id : root.getKeys(false)) {
            ConfigurationSection fx = root.getConfigurationSection(id + ".effects");
            if (fx == null) continue;
            assertTrue(fx.getDouble("root-seconds", 0) <= maxControl, id);
            assertTrue(fx.getDouble("slow-seconds", 0) <= maxControl, id);
        }
    }

    @Test
    void affinityGraphHasNoSelfOrMutualAdvantage() throws Exception {
        ConfigurationSection adv = yaml("elements.yml").getConfigurationSection("affinity.advantages");
        assertNotNull(adv);
        for (String a : adv.getKeys(false)) {
            for (String b : adv.getStringList(a)) {
                assertNotEquals(a, b);
                assertFalse(adv.getStringList(b).contains(a), a + " và " + b + " khắc lẫn nhau");
            }
        }
    }

    @Test
    void messagesHaveEveryKeyTheCodeUses() throws Exception {
        YamlConfiguration m = yaml("messages.yml");
        for (String k : new String[]{"prefix", "no-permission", "data-loading", "not-awakened", "awaken.success",
                "level-up", "cast.cooldown", "cast.no-target", "upgrade.success", "top.line", "admin.reloaded",
                "gui.title-main", "gui.lore.skill-damage", "gui.lore.hint-upgrade", "info", "help", "voucher.item-name",
                "voucher.redeemed", "voucher.invalid", "voucher.max", "unlock.max-reached", "unlock.voucher-only",
                "wings.on", "wings.off", "admin.given-voucher", "gui.lore.element-voucher", "boss.reward", "boss.spawned",
                "crystal.item-name", "boss.reward-item", "gui.lore.exchange-need", "gui.lore.exchange-have",
                "gui.lore.exchange-enough", "gui.boss.abilities-title", "gui.boss.drops-item", "gui.boss.line-entry", "gui.boss.event-spawn", "gui.title-boss", "exchange.success", "exchange.not-enough", "admin.boss-header", "gui.title-exchange"}) {
            assertTrue(m.contains(k), "thiếu khóa " + k);
        }
    }

    @Test
    void darkHasPullSkillAndLifesteal() throws Exception {
        ConfigurationSection root = yaml("skills.yml").getConfigurationSection("skills");
        boolean pull = false, steal = false;
        for (String id : root.getKeys(false)) {
            if (!"DARK".equals(root.getString(id + ".element"))) continue;
            if (root.getDouble(id + ".effects.teleport-pull") > 0) pull = true;
            if (root.getDouble(id + ".effects.lifesteal") > 0) steal = true;
            assertTrue(root.getDouble(id + ".effects.lifesteal", 0) <= 0.5, id);
        }
        assertTrue(pull && steal);
    }

    @Test
    void bossesAreWellFormedAndBalanced() throws Exception {
        YamlConfiguration y = yaml("bosses.yml");
        double maxDamage = y.getDouble("settings.max-damage");
        double maxControl = yaml("config.yml").getDouble("combat.max-control-seconds");
        ConfigurationSection bosses = y.getConfigurationSection("bosses");
        assertEquals(6, bosses.getKeys(false).size());
        Set<Element> covered = new HashSet<>();
        for (String id : bosses.getKeys(false)) {
            ConfigurationSection b = bosses.getConfigurationSection(id);
            Element el = Element.parse(b.getString("element"));
            assertNotNull(el, id);
            covered.add(el);
            assertNotEquals(Element.DARK, el, "boss không được cho nguyên tố nạp");
            assertNotEquals(Element.DRAGON, el, "boss không được cho nguyên tố nạp");
            assertTrue(b.getDouble("health") >= 20 && b.getDouble("scale") <= 4, id);
            assertTrue(b.getInt("rewards.crystals-min") <= b.getInt("rewards.crystals-max"), id);
            for (String ev : new String[]{"spawn", "cast", "enrage", "kill", "death"}) {
                assertFalse(b.getStringList("lines." + ev).isEmpty(), id + " thiếu lời thoại " + ev);
            }
            java.util.List<java.util.Map<?, ?>> items = b.getMapList("rewards.items");
            assertFalse(items.isEmpty(), id + " cần vật phẩm rớt");
            for (java.util.Map<?, ?> it : items) {
                int mn = ((Number) it.get("min")).intValue();
                int mx = ((Number) it.get("max")).intValue();
                double ch = ((Number) it.get("chance")).doubleValue();
                assertTrue(mn >= 1 && mn <= mx && mx <= 64, id + " số lượng rớt");
                assertTrue(ch > 0 && ch <= 1, id + " xác suất rớt");
                assertNotNull(it.get("material"), id);
            }
            ConfigurationSection ab = b.getConfigurationSection("abilities");
            assertTrue(ab.getKeys(false).size() >= 3, id + " cần ≥ 3 kỹ năng");
            for (String k : ab.getKeys(false)) {
                ConfigurationSection a = ab.getConfigurationSection(k);
                assertTrue(Set.of("PROJECTILE", "AOE_SELF", "AOE_TARGET", "HEAL_SELF").contains(a.getString("type")), id + "." + k);
                assertTrue(a.getDouble("damage") <= maxDamage, id + "." + k + " vượt trần");
                assertTrue(a.getDouble("cooldown") >= 1, id + "." + k);
                if (a.getString("type").startsWith("AOE")) {
                    assertTrue(a.getDouble("effects.delay-ticks") >= 10, id + "." + k + " đòn vùng phải có báo hiệu");
                }
                for (String c : new String[]{"root-seconds", "slow-seconds", "blind-seconds"}) {
                    assertTrue(a.getDouble("effects." + c) <= maxControl, id + "." + k + "." + c);
                }
            }
        }
        assertEquals(Set.of(Element.FIRE, Element.ICE, Element.LIGHTNING, Element.EARTH, Element.WIND, Element.LIGHT), covered);
    }

    @Test
    void exchangeCoversBaseElementsOnlyAndCrystalsAreValid() throws Exception {
        YamlConfiguration y = yaml("bosses.yml");
        ConfigurationSection ex = y.getConfigurationSection("exchange");
        assertNull(ex.get("DARK"));
        assertNull(ex.get("DRAGON"));
        for (String k : ex.getKeys(false)) {
            assertNotNull(Element.parse(k), k);
            assertTrue(ex.getInt(k) >= 1);
            assertNotNull(y.getString("crystals." + k), "thiếu vật phẩm tinh thể cho " + k);
        }
        assertEquals(6, ex.getKeys(false).size());
    }
}
