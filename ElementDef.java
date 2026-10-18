package com.astrax.elementer;

import org.bukkit.Material;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.List;
import java.util.Map;

public record ElementDef(
        Element element,
        String name,
        String symbol,
        String color,
        Material icon,
        List<String> description,
        double damagePerLevel,
        double undeadBonus,
        List<AttrDef> attributes,
        Map<DamageCause, Double> damageReduction,
        ProcDef proc) {

    /** Tên có màu + biểu tượng, ví dụ "&c🔥 Hỏa". */
    public String colored() {
        return color + symbol + " " + name;
    }
}
