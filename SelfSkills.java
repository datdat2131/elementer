package com.astrax.elementer;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.UUID;

/** Kỹ năng tác động lên bản thân: lướt (Lôi Bộ, Phong Bộ), khiên (Thạch Giáp, Pháo Đài, Quang Thuẫn), hồi máu (Ánh Sáng Phục Hồi). */
public final class SelfSkills {
    private final SkillSupport sup;
    private final AreaSkills area;

    public SelfSkills(SkillSupport sup, AreaSkills area) {
        this.sup = sup;
        this.area = area;
    }

    private double levelScale(PlayerData d, SkillDef s, int sl) {
        ElementDef def = sup.cfg.element(d.getElement());
        double elementBonus = def == null ? 0 : def.damagePerLevel();
        return sup.scale(s, sl) * (1.0 + elementBonus * Math.max(0, d.getLevel() - 1));
    }

    public boolean dash(Player p, PlayerData d, SkillDef s, int sl) {
        Vector dir = p.getLocation().getDirection();
        dir.setY(Math.max(-0.2, Math.min(0.35, dir.getY())));
        if (dir.lengthSquared() < 1e-6) return false;
        double power = Math.min(sup.cfg.main().getDouble("combat.max-dash-power", 2.2), s.power() * sup.scale(s, sl));
        Fx.particle(p.getLocation().add(0, 1, 0), s.particle(), 25, 0.4, 0.6, 0.4, 0.05);
        p.setFallDistance(0);
        p.setVelocity(dir.normalize().multiply(power));
        double sf = s.fx("slow-falling-seconds", 0);
        if (sf > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, (int) (sf * 20), 0, false, false, true));
        if (s.damage() > 0 && s.radius() > 0) {
            World w = p.getWorld();
            Bukkit.getScheduler().runTaskLater(sup.plugin, () -> {
                if (sup.stillValid(p, w)) area.tick(p, d, s, sl, p.getLocation().clone());
            }, 5L);
        }
        return true;
    }

    public boolean shield(Player p, PlayerData d, SkillDef s, int sl) {
        sup.combat.addShield(p, s.power() * levelScale(d, s, sl), s.duration());
        double res = s.fx("resistance-seconds", 0);
        if (res > 0) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, (int) (res * 20),
                    (int) s.fx("resistance-amplifier", 0), false, true, true));
        }
        double selfSlow = s.fx("self-slow-amplifier", -1);
        if (selfSlow >= 0) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) (s.duration() * 20),
                    (int) selfSlow, false, false, true));
        }
        Fx.ring(p.getLocation(), 1.2, s.particle(), 20);
        UUID id = p.getUniqueId();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || sup.combat.shieldRemaining(id) <= 0) {
                    cancel();
                    return;
                }
                Fx.particle(p.getLocation().add(0, 1, 0), s.particle(), 4, 0.5, 0.8, 0.5, 0.01);
            }
        }.runTaskTimer(sup.plugin, 10L, 10L);
        return true;
    }

    public boolean heal(Player p, PlayerData d, SkillDef s, int sl) {
        double amount = Math.min(sup.cfg.main().getDouble("combat.max-heal", 30.0), s.power() * levelScale(d, s, sl));
        sup.healOne(p, amount, s.particle());
        if (s.fx("heal-others", 0) > 0) sup.healPlayers(p, p.getLocation(), s.radius(), amount, false);
        Fx.ring(p.getLocation(), s.radius(), s.particle(), 32);
        return true;
    }
}
