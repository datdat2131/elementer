package com.astrax.elementer;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Đọc skills.yml. Kỹ năng sai (nguyên tố/type/slot không hợp lệ hoặc trùng slot) bị cảnh báo và bỏ qua. */
final class SkillParser {
    private final Logger log;

    SkillParser(Logger log) {
        this.log = log;
    }

    Map<String, SkillDef> parse(YamlConfiguration y, Map<Element, ElementDef> defs) {
        Map<String, SkillDef> out = new LinkedHashMap<>();
        ConfigurationSection root = y.getConfigurationSection("skills");
        if (root == null) {
            log.warning("skills.yml không có mục 'skills'.");
            return out;
        }
        Set<String> usedSlots = new HashSet<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            SkillDef def = one(id, s, defs, usedSlots);
            if (def != null) out.put(id, def);
        }
        return out;
    }

    private SkillDef one(String id, ConfigurationSection s, Map<Element, ElementDef> defs, Set<String> usedSlots) {
        Element el = Element.parse(s.getString("element"));
        if (el == null || !defs.containsKey(el)) {
            log.warning("Kỹ năng '" + id + "': nguyên tố không hợp lệ hoặc đang tắt - bỏ qua.");
            return null;
        }
        SkillType type;
        try {
            type = SkillType.valueOf(s.getString("type", "").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            log.warning("Kỹ năng '" + id + "': type không hợp lệ - bỏ qua.");
            return null;
        }
        int slot = s.getInt("slot", 0);
        if (slot < 1 || slot > 3) {
            log.warning("Kỹ năng '" + id + "': slot phải là 1-3 - bỏ qua.");
            return null;
        }
        if (!usedSlots.add(el + ":" + slot)) {
            log.warning("Kỹ năng '" + id + "': trùng slot " + slot + " của " + el + " - bỏ qua.");
            return null;
        }
        Material icon = Material.matchMaterial(s.getString("icon", "PAPER"));
        if (icon == null) icon = Material.PAPER;
        return new SkillDef(id, el, slot, s.getString("name", id), icon, s.getStringList("description"), type,
                s.getBoolean("ultimate", false), Math.max(1, s.getInt("unlock-level", 1)),
                Math.max(0, s.getDouble("cooldown", 10)), Math.max(0, s.getDouble("damage", 0)),
                Math.max(0, s.getDouble("range", 20)), Math.max(0, s.getDouble("radius", 0)),
                Math.max(0, s.getDouble("power", 0)), Math.max(0, s.getDouble("duration", 0)),
                Math.max(1, s.getInt("max-level", 5)), Math.max(0, s.getDouble("per-level-bonus", 0.1)),
                Math.max(1, s.getInt("upgrade-cost", 1)), s.getString("particle", "CRIT"),
                s.getString("sound", ""), effects(id, s));
    }

    private Map<String, Double> effects(String id, ConfigurationSection s) {
        Map<String, Double> fx = new HashMap<>();
        ConfigurationSection fs = s.getConfigurationSection("effects");
        if (fs != null) {
            for (String k : fs.getKeys(false)) {
                if (fs.isInt(k) || fs.isDouble(k) || fs.isLong(k)) fx.put(k, fs.getDouble(k));
                else log.warning("Kỹ năng '" + id + "': effects." + k + " phải là số.");
            }
        }
        return Map.copyOf(fx);
    }

    Map<Element, List<SkillDef>> group(Map<String, SkillDef> all) {
        Map<Element, List<SkillDef>> out = new EnumMap<>(Element.class);
        for (SkillDef d : all.values()) out.computeIfAbsent(d.element(), k -> new ArrayList<>()).add(d);
        for (List<SkillDef> l : out.values()) l.sort(Comparator.comparingInt(SkillDef::slot));
        return out;
    }
}
