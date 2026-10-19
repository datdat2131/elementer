package com.astrax.elementer;

import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Kỹ năng nguyên tố của boss: chọn kỹ năng phù hợp khoảng cách/hồi chiêu và thực thi (có báo hiệu trước các đòn vùng). */
final class BossAbilities {
    private static final int MAX_VICTIMS = 12;

    private final ElementerCore plugin;
    private final ElementConfig cfg;
    private final BossConfig bossCfg;
    private final ElementService elements;
    private final SkillSupport sup;

    BossAbilities(ElementerCore plugin, ElementConfig cfg, BossConfig bossCfg, ElementService elements, CombatService combat) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.bossCfg = bossCfg;
        this.elements = elements;
        this.sup = new SkillSupport(plugin, cfg, combat);
    }

    static boolean validPlayer(Player p, World w) {
        GameMode gm = p.getGameMode();
        return p.isOnline() && !p.isDead() && p.getWorld().equals(w) && !p.isInvulnerable()
                && (gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE);
    }

    // ------------------------------------------------------------------ Chọn và tung kỹ năng

    /** @return true nếu đã tung một kỹ năng */
    boolean tryCast(ActiveBoss b, Player target, long now) {
        if (now < b.nextCastAt) return false;
        double dist = b.entity.getLocation().distance(target.getLocation());
        List<SkillDef> order = new ArrayList<>(b.def.abilities());
        Collections.shuffle(order);
        for (SkillDef s : order) {
            if (b.readyAt.getOrDefault(s.id(), 0L) > now || !usable(b, s, dist)) continue;
            cast(b, s, target);
            double speedup = b.enraged ? 0.7 : 1.0;
            b.readyAt.put(s.id(), now + (long) (s.cooldown() * 1000 * speedup));
            b.nextCastAt = now + (long) (bossCfg.settings().getDouble("settings.cast-interval-seconds", 2.5) * 1000);
            return true;
        }
        return false;
    }

    private boolean usable(ActiveBoss b, SkillDef s, double dist) {
        return switch (s.type()) {
            case PROJECTILE, AOE_TARGET -> dist <= s.range();
            case AOE_SELF -> dist <= s.radius() + 2;
            case HEAL_AREA -> b.entity.getHealth() < b.entity.getAttribute(Attribute.MAX_HEALTH).getValue() * 0.6;
            default -> false;
        };
    }

    private void cast(ActiveBoss b, SkillDef s, Player target) {
        switch (s.type()) {
            case PROJECTILE -> projectile(b, s, target);
            case AOE_SELF -> area(b, s, b.entity.getLocation().clone());
            case AOE_TARGET -> area(b, s, target.getLocation().clone());
            case HEAL_AREA -> heal(b, s);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ Thực thi

    private void projectile(ActiveBoss b, SkillDef s, Player target) {
        LivingEntity boss = b.entity;
        World w = boss.getWorld();
        Location eye = boss.getEyeLocation();
        Vector dir = target.getLocation().add(0, 1.0, 0).toVector().subtract(eye.toVector());
        if (dir.lengthSquared() < 1e-6) return;
        dir.normalize();
        RayTraceResult blk = w.rayTraceBlocks(eye, dir, s.range(), FluidCollisionMode.NEVER, true);
        double max = blk == null ? s.range() : blk.getHitPosition().distance(eye.toVector());
        RayTraceResult r = w.rayTraceEntities(eye, dir, max, 0.7, e -> e instanceof Player p && validPlayer(p, w));
        double drawTo = max;
        if (r != null && r.getHitEntity() instanceof Player p) {
            drawTo = Math.min(max, r.getHitPosition().distance(eye.toVector()));
            hurt(b, s, p, eye);
        }
        for (double t = 1; t <= drawTo; t += 0.6) {
            Fx.particle(eye.clone().add(dir.clone().multiply(t)), s.particle(), 2, 0.05, 0.05, 0.05, 0);
        }
        Fx.sound(eye, s.sound(), 1.2f, 0.9f);
    }

    /** Đòn vùng có báo hiệu: vẽ vòng cảnh báo, sau delay-ticks mới gây sát thương; có thể lặp nhiều nhịp. */
    private void area(ActiveBoss b, SkillDef s, Location center) {
        int delay = Math.max(1, (int) s.fx("delay-ticks", 20));
        int repeat = Math.max(1, Math.min(8, (int) s.fx("repeat", 1)));
        int interval = Math.max(1, (int) s.fx("interval-ticks", 10));
        Fx.ring(center, s.radius(), s.particle(), 32);
        Fx.sound(center, s.sound(), 1.2f, 0.7f);
        new BukkitRunnable() {
            int n = 0;

            @Override
            public void run() {
                if (!b.entity.isValid() || b.entity.isDead()) {
                    cancel();
                    return;
                }
                areaTick(b, s, center);
                if (++n >= repeat) cancel();
            }
        }.runTaskTimer(plugin, delay, interval);
    }

    private void areaTick(ActiveBoss b, SkillDef s, Location center) {
        World w = center.getWorld();
        int hit = 0;
        for (Player p : w.getNearbyPlayers(center, s.radius())) {
            if (hit >= MAX_VICTIMS) break;
            if (!validPlayer(p, w) || p.getLocation().distance(center) > s.radius()) continue;
            hurt(b, s, p, center);
            if (s.fx("lightning-visual", 0) > 0) w.strikeLightningEffect(p.getLocation());
            hit++;
        }
        Fx.ring(center, s.radius(), s.particle(), 32);
        Fx.particle(center.clone().add(0, 1, 0), s.particle(), 25, s.radius() / 3, 0.5, s.radius() / 3, 0.02);
    }

    private void heal(ActiveBoss b, SkillDef s) {
        LivingEntity boss = b.entity;
        double max = boss.getAttribute(Attribute.MAX_HEALTH).getValue();
        boss.setHealth(Math.min(max, boss.getHealth() + max * s.power()));
        Fx.ring(boss.getLocation(), 2.0, "HEART", 20);
        Fx.sound(boss.getLocation(), s.sound(), 1f, 1.2f);
    }

    // ------------------------------------------------------------------ Sát thương

    /** sát thương = damage × tương khắc(nguyên tố boss → nguyên tố người chơi) × hệ số nổi điên × hệ số chung, có trần. */
    private double damageTo(ActiveBoss b, SkillDef s, Player p) {
        PlayerData pd = elements.get(p);
        Element pe = pd == null ? null : pd.getElement();
        double aff = DamageCalculator.clamp(cfg.affinity().multiplier(b.def.element(), pe),
                cfg.main().getDouble("combat.affinity-min", 0.5), cfg.main().getDouble("combat.affinity-max", 1.5));
        double extra = (b.enraged ? b.def.enrageMultiplier() : 1.0)
                * bossCfg.settings().getDouble("settings.damage-multiplier", 1.0);
        return DamageCalculator.skillDamage(s.damage(), 1, 0, 1, 0, aff, extra,
                bossCfg.settings().getDouble("settings.max-damage", 30.0));
    }

    private void hurt(ActiveBoss b, SkillDef s, Player p, Location center) {
        double dmg = damageTo(b, s, p);
        if (dmg > 0) p.damage(dmg, b.entity);
        if (!p.isDead()) sup.applyEffects(b.entity, p, s, center, 1.0);
    }
}
