package com.astrax.elementer;

import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntConsumer;

/** Các hàm dùng chung của kỹ năng: nhắm mục tiêu, gây đòn, áp hiệu ứng, tìm mục tiêu, lặp nhịp, hồi máu. */
public final class SkillSupport {
    /** Điểm nhắm và (nếu có) thực thể đang nhắm trúng. */
    public record Aim(Location point, LivingEntity entity) {}

    final ElementerCore plugin;
    final ElementConfig cfg;
    final CombatService combat;

    public SkillSupport(ElementerCore plugin, ElementConfig cfg, CombatService combat) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.combat = combat;
    }

    public double scale(SkillDef s, int skillLevel) {
        return 1.0 + s.perLevelBonus() * Math.max(0, skillLevel - 1);
    }

    public boolean stillValid(Player p, World w) {
        return p.isOnline() && !p.isDead() && p.getWorld().equals(w);
    }

    public Aim aim(Player p, double range) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        World w = p.getWorld();
        RayTraceResult blk = w.rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        double maxDist = blk == null ? range : blk.getHitPosition().distance(eye.toVector());
        RayTraceResult ent = w.rayTraceEntities(eye, dir, maxDist, 0.6,
                e -> e instanceof LivingEntity le && combat.isValidTarget(p, le));
        if (ent != null && ent.getHitEntity() instanceof LivingEntity le) {
            return new Aim(ent.getHitPosition().toLocation(w), le);
        }
        Vector end = blk != null ? blk.getHitPosition() : eye.toVector().add(dir.clone().multiply(range));
        return new Aim(end.toLocation(w), null);
    }

    /** Gây sát thương + hiệu ứng lên một mục tiêu. @return true nếu đòn trúng (không bị hủy) */
    public boolean hit(Player p, PlayerData d, SkillDef s, int sl, LivingEntity t, Location center, double ratio) {
        if (!combat.isValidTarget(p, t)) return false;
        double dmg = combat.computeDamage(d, s, sl, t) * ratio;
        if (dmg > 0 && !combat.damage(p, t, dmg)) return false;
        if (t.isDead()) return true;
        applyEffects(t, s, center, ratio);
        return true;
    }

    public void applyEffects(LivingEntity t, SkillDef s, Location center, double ratio) {
        double burn = s.fx("burn-seconds", 0);
        if (burn > 0) combat.ignite(t, burn);
        double slow = s.fx("slow-seconds", 0);
        if (slow > 0) combat.slow(t, slow, (int) s.fx("slow-amplifier", 1));
        double fr = s.fx("freeze-ticks", 0);
        if (fr > 0) combat.freeze(t, (int) fr);
        double root = s.fx("root-seconds", 0);
        if (root > 0) combat.root(t, root);

        Vector v = new Vector(0, 0, 0);
        double kb = s.fx("knockback", 0) * ratio;
        if (kb > 0) {
            Vector away = t.getLocation().toVector().subtract(center.toVector());
            away.setY(0);
            if (away.lengthSquared() > 1e-4) v.add(away.normalize().multiply(kb));
        }
        double lift = s.fx("lift", 0);
        if (lift > 0) v.setY(lift);
        double pull = s.fx("pull", 0);
        if (pull > 0) {
            Vector toCenter = center.toVector().subtract(t.getLocation().toVector());
            toCenter.setY(0);
            double len = toCenter.length();
            if (len > 1e-4) v.add(toCenter.normalize().multiply(Math.min(pull, len * 0.5)));
        }
        if (v.lengthSquared() > 0) combat.push(t, v);
    }

    /** Mục tiêu hợp lệ quanh một điểm, gần nhất trước, tối đa combat.max-targets-per-cast. */
    public List<LivingEntity> nearby(Player p, Location center, double radius, boolean hostileOnly) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : center.getWorld().getNearbyLivingEntities(center, radius)) {
            if (e.getLocation().distance(center) > radius) continue;
            if (!combat.isValidTarget(p, e)) continue;
            if (hostileOnly && !(e instanceof Enemy)) continue;
            out.add(e);
        }
        out.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(center)));
        int max = cfg.main().getInt("combat.max-targets-per-cast", 8);
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** Chạy tick ngay lập tức rồi lặp lại; tự dừng nếu người chơi thoát/chết/đổi thế giới. */
    public void repeat(Player p, int times, int interval, IntConsumer tick) {
        tick.accept(0);
        if (times <= 1) return;
        World w = p.getWorld();
        new BukkitRunnable() {
            int n = 1;

            @Override
            public void run() {
                if (n >= times || !stillValid(p, w)) {
                    cancel();
                    return;
                }
                tick.accept(n);
                n++;
            }
        }.runTaskTimer(plugin, Math.max(1, interval), Math.max(1, interval));
    }

    public void healOne(Player t, double amount, String particle) {
        if (amount <= 0 || t.getAttribute(Attribute.MAX_HEALTH) == null) return;
        t.heal(amount, EntityRegainHealthEvent.RegainReason.MAGIC);
        Fx.particle(t.getLocation().add(0, 1.5, 0), particle, 6, 0.4, 0.4, 0.4, 0);
    }

    /** Hồi máu cho người chơi trong bán kính (không có party: hồi cho mọi người chơi gần đó). */
    public void healPlayers(Player caster, Location center, double radius, double amount, boolean includeCaster) {
        double capped = Math.min(cfg.main().getDouble("combat.max-heal", 30.0), amount);
        for (Player o : center.getWorld().getPlayers()) {
            if (o.isDead() || o.getGameMode() == GameMode.SPECTATOR) continue;
            if (o.equals(caster) && !includeCaster) continue;
            if (o.getLocation().distance(center) > radius) continue;
            healOne(o, capped, "HEART");
        }
    }
}
