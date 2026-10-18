package com.astrax.elementer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Kỹ năng vùng và đánh mục tiêu: Bùng Cháy, Sương Giá, Địa Chấn, Lốc Xoáy, Thánh Quang, Thiên Hỏa, Băng Ngục, Thiên Lôi, Sét Đánh. */
public final class AreaSkills {
    private final SkillSupport sup;

    public AreaSkills(SkillSupport sup) {
        this.sup = sup;
    }

    /** Một nhịp sát thương vùng tại {@code center}. */
    public void tick(Player p, PlayerData d, SkillDef s, int sl, Location center) {
        boolean hostileOnly = s.fx("hostile-only", 0) > 0;
        boolean bolts = s.fx("lightning-visual", 0) > 0;
        List<LivingEntity> targets = sup.nearby(p, center, s.radius(), hostileOnly);
        boolean struck = false;
        for (LivingEntity t : targets) {
            if (sup.hit(p, d, s, sl, t, center, 1.0) && bolts) {
                center.getWorld().strikeLightningEffect(t.getLocation());
                struck = true;
            }
        }
        if (bolts && !struck) {
            double a = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double r = ThreadLocalRandom.current().nextDouble(0, Math.max(0.5, s.radius()));
            center.getWorld().strikeLightningEffect(center.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r));
        }
        double heal = s.fx("heal-allies", 0);
        if (heal > 0) sup.healPlayers(p, center, s.radius(), heal * sup.scale(s, sl), true);
        Fx.ring(center, s.radius(), s.particle(), 32);
        Fx.particle(center.clone().add(0, 1, 0), s.particle(), 20, s.radius() / 3, 0.5, s.radius() / 3, 0.02);
        if (s.ultimate()) Fx.particle(center, "EXPLOSION_EMITTER", 1, 0, 0, 0, 0);
    }

    public boolean castSelf(Player p, PlayerData d, SkillDef s, int sl) {
        Location center = p.getLocation().clone();
        sup.repeat(p, (int) s.fx("repeat", 1), (int) s.fx("interval-ticks", 10), i -> tick(p, d, s, sl, center));
        return true;
    }

    public boolean castTarget(Player p, PlayerData d, SkillDef s, int sl) {
        Location center = sup.aim(p, s.range()).point();
        int delay = (int) s.fx("delay-ticks", 0);
        Fx.ring(center, s.radius(), s.particle(), 32); // báo hiệu vùng sắp bị đánh
        Fx.sound(center, s.sound(), 0.6f, 0.8f);
        World w = p.getWorld();
        Runnable go = () -> {
            if (!sup.stillValid(p, w)) return;
            sup.repeat(p, (int) s.fx("repeat", 1), (int) s.fx("interval-ticks", 10), i -> tick(p, d, s, sl, center));
        };
        if (delay <= 0) go.run();
        else Bukkit.getScheduler().runTaskLater(sup.plugin, go, delay);
        return true;
    }

    public boolean castStrike(Player p, PlayerData d, SkillDef s, int sl) {
        SkillSupport.Aim a = sup.aim(p, s.range());
        if (a.entity() == null) return false;
        LivingEntity t = a.entity();
        if (!sup.hit(p, d, s, sl, t, p.getLocation(), 1.0)) return false;
        if (s.fx("lightning-visual", 0) > 0) t.getWorld().strikeLightningEffect(t.getLocation());
        Fx.particle(t.getLocation().add(0, 1, 0), s.particle(), 30, 0.4, 0.8, 0.4, 0.1);
        return true;
    }
}
