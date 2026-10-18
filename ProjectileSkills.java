package com.astrax.elementer;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Kỹ năng dạng tia/đạn: Hỏa Cầu, Băng Tiễn, Lưỡi Gió. */
public final class ProjectileSkills {
    private final SkillSupport sup;

    public ProjectileSkills(SkillSupport sup) {
        this.sup = sup;
    }

    public boolean cast(Player p, PlayerData d, SkillDef s, int sl) {
        int pierce = (int) s.fx("pierce", 0);
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World w = p.getWorld();
        RayTraceResult blk = w.rayTraceBlocks(eye, dir, s.range(), FluidCollisionMode.NEVER, true);
        double maxDist = blk == null ? s.range() : blk.getHitPosition().distance(eye.toVector());
        Set<UUID> seen = new HashSet<>();
        List<LivingEntity> victims = new ArrayList<>();
        Location impact = eye.clone().add(dir.clone().multiply(maxDist));
        for (int i = 0; i <= pierce; i++) {
            RayTraceResult r = w.rayTraceEntities(eye, dir, maxDist, 0.5,
                    e -> e instanceof LivingEntity le && !seen.contains(e.getUniqueId()) && sup.combat.isValidTarget(p, le));
            if (r == null || !(r.getHitEntity() instanceof LivingEntity le)) break;
            seen.add(le.getUniqueId());
            victims.add(le);
            if (pierce == 0) impact = r.getHitPosition().toLocation(w);
        }
        double drawDist = pierce == 0 ? Math.min(maxDist, impact.distance(eye)) : maxDist;
        for (double t = 1; t <= drawDist; t += 0.6) {
            Fx.particle(eye.clone().add(dir.clone().multiply(t)), s.particle(), 1, 0.03, 0.03, 0.03, 0);
        }
        for (LivingEntity v : victims) {
            sup.hit(p, d, s, sl, v, p.getLocation(), 1.0);
        }
        boolean exploded = pierce == 0 && (!victims.isEmpty() || blk != null);
        if (exploded && s.radius() > 0) {
            double ratio = s.fx("splash-damage-ratio", 0.5);
            for (LivingEntity e : sup.nearby(p, impact, s.radius(), false)) {
                if (seen.contains(e.getUniqueId())) continue;
                sup.hit(p, d, s, sl, e, impact, ratio);
            }
            Fx.ring(impact, s.radius(), s.particle(), 24);
            Fx.particle(impact, "EXPLOSION", 1, 0, 0, 0, 0);
            Fx.sound(impact, "entity.generic.explode", 0.8f, 1.2f);
        }
        return true;
    }
}
