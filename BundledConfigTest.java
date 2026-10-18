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
            assertEquals(Set.of(1, 2, 3), slots.get(e), "kỹ năng của " + e);
        }
        assertEquals(18, root.getKeys(false).size());
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
                "gui.title-main", "gui.lore.skill-damage", "gui.lore.hint-upgrade", "info", "help"}) {
            assertTrue(m.contains(k), "thiếu khóa " + k);
        }
    }
}
