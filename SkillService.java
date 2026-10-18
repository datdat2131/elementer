package com.astrax.elementer;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

/** Kiểm tra điều kiện tung chiêu, hồi chiêu, nâng cấp; giao việc thực thi cho các lớp trong gói skill. Chỉ dùng trên main thread. */
public final class SkillService {
    public enum CastResult {
        OK, NO_DATA, NO_ELEMENT, NO_SKILL, LOCKED, COOLDOWN, GLOBAL_COOLDOWN, NO_TARGET, WORLD_DISABLED, INVALID_STATE
    }

    public enum UpgradeResult { OK, NO_DATA, NO_SKILL, LOCKED, MAX, NO_POINTS }

    private final ElementConfig cfg;
    private final ElementService elements;
    private final CooldownTracker cooldowns = new CooldownTracker();
    private final ProjectileSkills projectiles;
    private final AreaSkills areas;
    private final SelfSkills selves;

    public SkillService(ElementerCore plugin, ElementConfig cfg, ElementService elements, CombatService combat) {
        this.cfg = cfg;
        this.elements = elements;
        SkillSupport support = new SkillSupport(plugin, cfg, combat);
        this.projectiles = new ProjectileSkills(support);
        this.areas = new AreaSkills(support);
        this.selves = new SelfSkills(support, areas);
    }

    public void clear(UUID id) { cooldowns.clear(id); }

    public void maintenance(long nowMs) { cooldowns.purgeExpired(nowMs); }

    public long remainingMs(Player p, SkillDef s) {
        return cooldowns.remaining(p.getUniqueId(), s.id(), System.currentTimeMillis());
    }

    // ------------------------------------------------------------------ Tung kỹ năng

    public CastResult cast(Player p, int slot) {
        if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) return CastResult.INVALID_STATE;
        if (cfg.isWorldDisabled(p.getWorld().getName())) return CastResult.WORLD_DISABLED;
        PlayerData d = elements.get(p);
        if (d == null) return CastResult.NO_DATA;
        if (d.getElement() == null) return CastResult.NO_ELEMENT;
        SkillDef s = cfg.skill(d.getElement(), slot);
        if (s == null) return CastResult.NO_SKILL;
        if (d.getLevel() < s.unlockLevel()) return CastResult.LOCKED;
        long now = System.currentTimeMillis();
        UUID id = p.getUniqueId();
        if (cooldowns.globalRemaining(id, now) > 0) return CastResult.GLOBAL_COOLDOWN;
        if (cooldowns.remaining(id, s.id(), now) > 0) return CastResult.COOLDOWN;
        int sl = levelOf(d, s);
        Fx.sound(p.getLocation(), s.sound(), 1f, 1f);
        if (!execute(p, d, s, sl)) return CastResult.NO_TARGET;
        cooldowns.start(id, s.id(), cooldownMs(s, sl), now);
        cooldowns.startGlobal(id, cfg.main().getLong("combat.global-cooldown-ms", 700), now);
        return CastResult.OK;
    }

    private boolean execute(Player p, PlayerData d, SkillDef s, int sl) {
        return switch (s.type()) {
            case PROJECTILE -> projectiles.cast(p, d, s, sl);
            case AOE_SELF -> areas.castSelf(p, d, s, sl);
            case AOE_TARGET -> areas.castTarget(p, d, s, sl);
            case STRIKE_TARGET -> areas.castStrike(p, d, s, sl);
            case DASH -> selves.dash(p, d, s, sl);
            case SHIELD -> selves.shield(p, d, s, sl);
            case HEAL_AREA -> selves.heal(p, d, s, sl);
        };
    }

    /** Tung kỹ năng và báo kết quả bằng action bar. */
    public void castAndNotify(Player p, int slot) {
        CastResult r = cast(p, slot);
        var m = cfg.messages();
        PlayerData d = elements.get(p);
        SkillDef s = d == null || d.getElement() == null ? null : cfg.skill(d.getElement(), slot);
        switch (r) {
            case OK -> { }
            case NO_DATA -> m.actionBar(p, "data-loading");
            case NO_ELEMENT -> m.actionBar(p, "not-awakened");
            case NO_SKILL -> m.actionBar(p, "cast.no-skill");
            case LOCKED -> m.actionBar(p, "cast.locked", "unlock", s == null ? "?" : s.unlockLevel());
            case COOLDOWN -> m.actionBar(p, "cast.cooldown", "name", s == null ? "" : s.name(),
                    "seconds", String.format(Locale.ROOT, "%.1f", s == null ? 0 : remainingMs(p, s) / 1000.0));
            case GLOBAL_COOLDOWN -> m.actionBar(p, "cast.global-cooldown");
            case NO_TARGET -> m.actionBar(p, "cast.no-target");
            case WORLD_DISABLED -> m.actionBar(p, "cast.world-disabled");
            case INVALID_STATE -> m.actionBar(p, "cast.invalid-state");
        }
    }

    public int levelOf(PlayerData d, SkillDef s) {
        return Math.max(1, Math.min(s.maxLevel(), d.skillLevel(s.id())));
    }

    public long cooldownMs(SkillDef s, int skillLevel) {
        double red = Math.min(cfg.main().getDouble("combat.max-cooldown-reduction", 0.3),
                cfg.main().getDouble("combat.cooldown-reduction-per-level", 0.03) * Math.max(0, skillLevel - 1));
        double sec = Math.max(cfg.main().getDouble("combat.min-cooldown-seconds", 1.0), s.cooldown() * (1.0 - red));
        return (long) (sec * 1000);
    }

    // ------------------------------------------------------------------ Nâng cấp

    public int upgradeCost(PlayerData d, SkillDef s) {
        return s.upgradeCost() * levelOf(d, s);
    }

    public UpgradeResult upgrade(Player p, String skillId) {
        PlayerData d = elements.get(p);
        if (d == null || d.getElement() == null) return UpgradeResult.NO_DATA;
        SkillDef s = cfg.skill(skillId);
        if (s == null || s.element() != d.getElement()) return UpgradeResult.NO_SKILL;
        if (d.getLevel() < s.unlockLevel()) return UpgradeResult.LOCKED;
        int cur = levelOf(d, s);
        if (cur >= s.maxLevel()) return UpgradeResult.MAX;
        int cost = upgradeCost(d, s);
        if (d.getSkillPoints() < cost) return UpgradeResult.NO_POINTS;
        d.setSkillPoints(d.getSkillPoints() - cost);
        d.setSkillLevel(s.id(), cur + 1);
        return UpgradeResult.OK;
    }
}
