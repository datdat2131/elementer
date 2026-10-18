package com.astrax.elementer;

import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Dựng các dòng chữ thông tin/chỉ số từ messages.yml (dùng cho lệnh và GUI). */
public final class ElementInfo {
    private final ElementConfig cfg;

    public ElementInfo(ElementConfig cfg) {
        this.cfg = cfg;
    }

    public List<String> infoLines(PlayerData d) {
        ElementDef def = cfg.element(d.getElement());
        long req = cfg.curve().required(d.getLevel());
        long cap = cfg.main().getLong("exp.daily-cap", 0);
        return cfg.messages().formatted("info", "element", def == null ? "?" : def.colored(),
                "level", d.getLevel(), "max", cfg.curve().maxLevel(), "exp", d.getExp(),
                "required", req == 0 ? "MAX" : String.valueOf(req), "points", d.getSkillPoints(),
                "daily", d.getDailyExp(), "cap", cap <= 0 ? "∞" : String.valueOf(cap));
    }

    public List<String> statsLines(PlayerData d) {
        List<String> out = new ArrayList<>();
        ElementDef def = cfg.element(d.getElement());
        if (def == null) return out;
        Messages m = cfg.messages();
        out.add(Messages.format(m.raw("stats-header"), "element", def.colored()));
        out.add(Messages.format(m.raw("stats-damage"), "percent",
                Text.num(def.damagePerLevel() * Math.max(0, d.getLevel() - 1) * 100)));
        for (AttrDef a : def.attributes()) {
            out.add(Messages.format(m.raw("stats-attribute"), "attribute", a.attribute(),
                    "value", String.format(Locale.ROOT, "%.3f", a.amount(d.getLevel()))));
        }
        for (Map.Entry<DamageCause, Double> e : def.damageReduction().entrySet()) {
            out.add(Messages.format(m.raw("stats-reduction"), "cause", e.getKey().name(),
                    "percent", Text.num(e.getValue() * 100)));
        }
        return out;
    }
}
