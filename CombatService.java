package com.astrax.elementer;

import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Cửa ngõ chiến đấu: kiểm tra mục tiêu hợp lệ, tính và gây sát thương (tương khắc, PvP, trần).
 * Lá chắn và khống chế nằm ở ShieldService/ControlService, được ủy quyền qua các hàm bên dưới.
 */
public final class CombatService {
    private final ElementConfig cfg;
    private final ShieldService shields;
    private final ControlService control;
    private ElementService elements;
    private boolean internalDamage;
    private boolean damageApplied;

    public CombatService(ElementConfig cfg) {
        this.cfg = cfg;
        this.shields = new ShieldService(cfg);
        this.control = new ControlService(cfg);
    }

    public void bind(ElementService elements) {
        this.elements = elements;
    }

    // ------------------------------------------------------------------ Mục tiêu

    /** Kiểm tra mục tiêu có thể bị kỹ năng của {@code caster} tác động hay không. */
    public boolean isValidTarget(Player caster, LivingEntity t) {
        if (t == null || t.equals(caster) || t.isDead() || !t.isValid()) return false;
        if (!t.getWorld().equals(caster.getWorld())) return false;
        if (t.isInvulnerable() || cfg.isProtected(t.getType())) return false;
        if (t instanceof Player p) {
            if (!cfg.main().getBoolean("combat.pvp-enabled", true)) return false;
            if (!t.getWorld().getPVP()) return false;
            GameMode gm = p.getGameMode();
            if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return false;
        }
        if (t instanceof Tameable tm && tm.isTamed() && caster.equals(tm.getOwner())) return false;
        return true;
    }

    public Element targetElement(LivingEntity t) {
        if (t instanceof Player p) {
            PlayerData d = elements.get(p);
            return d == null ? null : d.getElement();
        }
        return cfg.mobElement(t.getType());
    }

    // ------------------------------------------------------------------ Sát thương

    public double computeDamage(PlayerData caster, SkillDef s, int skillLevel, LivingEntity target) {
        if (caster.getElement() == null || s.damage() <= 0) return 0;
        ElementDef def = cfg.element(caster.getElement());
        if (def == null) return 0;
        double cap = cfg.main().getDouble("combat.max-skill-damage", 40.0);
        double aff = DamageCalculator.clamp(cfg.affinity().multiplier(caster.getElement(), targetElement(target)),
                cfg.main().getDouble("combat.affinity-min", 0.5), cfg.main().getDouble("combat.affinity-max", 1.5));
        double extra = 1.0;
        if (target instanceof Player) extra *= cfg.main().getDouble("combat.pvp-damage-multiplier", 0.6);
        if (cfg.isUndead(target.getType())) {
            extra *= 1.0 + def.undeadBonus();
            extra *= s.fx("undead-multiplier", 1.0);
        }
        return DamageCalculator.skillDamage(s.damage(), skillLevel, s.perLevelBonus(), caster.getLevel(),
                def.damagePerLevel(), aff, extra, cap);
    }

    /**
     * Gây sát thương qua hệ thống sự kiện chuẩn của server (để plugin bảo vệ vùng có thể hủy).
     * @return true nếu sự kiện sát thương đã diễn ra và không bị hủy
     */
    public boolean damage(Player caster, LivingEntity target, double amount) {
        if (amount <= 0) return false;
        damageApplied = false;
        internalDamage = true;
        try {
            target.damage(amount, caster);
        } finally {
            internalDamage = false;
        }
        return damageApplied;
    }

    /** True trong lúc kỹ năng đang gây sát thương (listener dùng để bỏ qua proc đánh thường). */
    public boolean isInternalDamage() { return internalDamage; }

    /** Listener gọi khi sự kiện sát thương do kỹ năng KHÔNG bị hủy. */
    public void markApplied() { damageApplied = true; }

    // ------------------------------------------------------------------ Ủy quyền: khiên

    public void addShield(Player p, double amount, double seconds) { shields.add(p, amount, seconds); }
    public double shieldRemaining(UUID id) { return shields.remaining(id); }
    public double absorb(Player p, double damage) { return shields.absorb(p, damage); }
    public void clearShield(UUID id) { shields.clear(id); }

    // ------------------------------------------------------------------ Ủy quyền: khống chế

    public boolean root(LivingEntity t, double seconds) { return control.root(t, seconds); }
    public void slow(LivingEntity t, double seconds, int amplifier) { control.slow(t, seconds, amplifier); }
    public void freeze(LivingEntity t, int ticks) { control.freeze(t, ticks); }
    public void ignite(LivingEntity t, double seconds) { control.ignite(t, seconds); }
    public void push(LivingEntity t, Vector v) { control.push(t, v); }

    // ------------------------------------------------------------------ Dọn dẹp

    public void maintenance(long nowMs) {
        shields.purge(nowMs);
        control.purge(nowMs);
    }

    public void clear(UUID id) {
        shields.clear(id);
        control.clear(id);
    }
}
