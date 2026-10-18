package com.astrax.elementer;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Khống chế và hiệu ứng trạng thái, luôn có trần thời gian/độ lớn; trói có miễn nhiễm sau đó. */
public final class ControlService {
    private final ElementConfig cfg;
    private final Map<UUID, Long> rootImmunity = new HashMap<>();

    public ControlService(ElementConfig cfg) {
        this.cfg = cfg;
    }

    private double maxControl() { return cfg.main().getDouble("combat.max-control-seconds", 4.0); }

    /** Trói chặt (gần như bất động). @return false nếu đang miễn nhiễm hoặc thời gian <= 0 */
    public boolean root(LivingEntity t, double seconds) {
        double sec = Math.min(maxControl(), seconds);
        if (sec <= 0) return false;
        long now = System.currentTimeMillis();
        Long imm = rootImmunity.get(t.getUniqueId());
        if (imm != null && imm > now) return false;
        int ticks = (int) Math.round(sec * 20);
        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 6, false, true, true));
        t.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, ticks, 128, false, false, false));
        long immunity = (long) ((sec + cfg.main().getDouble("combat.control-immunity-seconds", 6.0)) * 1000);
        rootImmunity.put(t.getUniqueId(), now + immunity);
        return true;
    }

    public void slow(LivingEntity t, double seconds, int amplifier) {
        double sec = Math.min(maxControl(), seconds);
        if (sec <= 0) return;
        int amp = Math.max(0, Math.min(4, amplifier));
        t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) Math.round(sec * 20), amp, false, true, true));
    }

    /**
     * Hiệu ứng xấu có thời hạn (Wither, Weakness...), trần combat.max-debuff-seconds.
     * Mù (BLINDNESS) tính là khống chế nên dùng trần max-control-seconds.
     */
    public void debuff(LivingEntity t, PotionEffectType type, double seconds, int amplifier) {
        boolean control = PotionEffectType.BLINDNESS.equals(type);
        double cap = control ? maxControl() : cfg.main().getDouble("combat.max-debuff-seconds", 8.0);
        double sec = Math.min(cap, seconds);
        if (sec <= 0) return;
        t.addPotionEffect(new PotionEffect(type, (int) Math.round(sec * 20), Math.max(0, Math.min(3, amplifier)), false, true, true));
    }

    public void freeze(LivingEntity t, int ticks) {
        int capped = Math.min(Math.max(0, ticks), (int) (maxControl() * 20 * 2));
        if (capped > 0) t.setFreezeTicks(Math.max(t.getFreezeTicks(), capped));
    }

    public void ignite(LivingEntity t, double seconds) {
        double sec = Math.min(cfg.main().getDouble("combat.max-burn-seconds", 10.0), seconds);
        if (sec > 0) t.setFireTicks(Math.max(t.getFireTicks(), (int) Math.round(sec * 20)));
    }

    /** Đặt vận tốc với độ lớn bị chặn bởi combat.max-knockback. */
    public void push(LivingEntity t, Vector v) {
        if (Double.isNaN(v.getX()) || Double.isNaN(v.getY()) || Double.isNaN(v.getZ())) return;
        double max = cfg.main().getDouble("combat.max-knockback", 1.6);
        if (v.length() > max) v = v.normalize().multiply(max);
        t.setVelocity(v);
    }

    public void purge(long nowMs) { rootImmunity.values().removeIf(e -> e <= nowMs); }

    public void clear(UUID id) { rootImmunity.remove(id); }
}
