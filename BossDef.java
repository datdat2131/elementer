package com.astrax.elementer;

import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Map;

/** Định nghĩa một boss nguyên tố (đọc từ bosses.yml). */
public record BossDef(
        String id,
        Element element,
        String name,
        EntityType type,
        double health,
        double attackDamage,
        double armor,
        double scale,
        double speed,
        double detectRange,
        double enrageBelow,
        double enrageMultiplier,
        int crystalsMin,
        int crystalsMax,
        long exp,
        List<SkillDef> abilities,
        Map<String, List<String>> lines,
        List<BossDrop> drops) {
}
