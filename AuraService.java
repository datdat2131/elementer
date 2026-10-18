package com.astrax.elementer;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Hào quang dưới chân theo nguyên tố đang dùng (cấu hình trong elements.yml). Chạy một tác vụ lặp duy nhất,
 * bỏ qua người chơi tàng hình/chết/spectator/đang lướt/đang bơi, và có thể ẩn khi ngồi xổm.
 */
public final class AuraService {
    private final ElementerCore plugin;
    private final ElementConfig cfg;
    private final ElementService elements;
    private BukkitTask task;
    private long tick;

    public AuraService(ElementerCore plugin, ElementConfig cfg, ElementService elements) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.elements = elements;
    }

    public void start() {
        stop();
        int interval = Math.max(2, Math.min(40, cfg.main().getInt("aura.interval-ticks", 6)));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::run, interval, interval);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    private void run() {
        if (!cfg.main().getBoolean("aura.enabled", true)) return;
        tick++;
        boolean hideSneak = cfg.main().getBoolean("aura.hide-when-sneaking", true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = elements.get(p);
            if (d == null || d.getElement() == null) continue;
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.isGliding() || p.isSwimming()) continue;
            if (p.hasPotionEffect(PotionEffectType.INVISIBILITY) || (hideSneak && p.isSneaking())) continue;
            if (cfg.isWorldDisabled(p.getWorld().getName())) continue;
            ElementDef def = cfg.element(d.getElement());
            if (def == null) continue;
            AuraDef a = def.aura();
            if (a != null && d.isAuraEnabled() && d.getLevel() >= a.unlockLevel()) AuraDrawer.draw(p.getLocation(), a, tick);
            WingsDef w = def.wings();
            if (w != null && d.isWingsEnabled() && d.getLevel() >= w.unlockLevel()) WingsRenderer.draw(p, w, tick);
        }
    }

    /**
     * Bật/tắt hào quang của người chơi và gửi thông báo.
     * @param desired true = bật, false = tắt, null = đảo trạng thái
     */
    public void setEnabled(Player p, Boolean desired) {
        Messages m = cfg.messages();
        PlayerData d = elements.get(p);
        if (d == null) {
            m.send(p, "data-loading");
            return;
        }
        if (d.getElement() == null) {
            m.send(p, "not-awakened");
            return;
        }
        ElementDef def = cfg.element(d.getElement());
        AuraDef a = def == null ? null : def.aura();
        if (a == null) {
            m.send(p, "aura.none");
            return;
        }
        if (d.getLevel() < a.unlockLevel()) {
            m.send(p, "aura.locked", "level", a.unlockLevel());
            return;
        }
        boolean now = desired == null ? !d.isAuraEnabled() : desired;
        d.setAuraEnabled(now);
        m.send(p, now ? "aura.on" : "aura.off");
    }

    /** Bật/tắt cánh (chỉ nguyên tố có cánh). @param desired true = bật, false = tắt, null = đảo trạng thái */
    public void setWings(Player p, Boolean desired) {
        Messages m = cfg.messages();
        PlayerData d = elements.get(p);
        if (d == null) {
            m.send(p, "data-loading");
            return;
        }
        ElementDef def = d.getElement() == null ? null : cfg.element(d.getElement());
        WingsDef w = def == null ? null : def.wings();
        if (w == null) {
            m.send(p, "wings.none");
            return;
        }
        if (d.getLevel() < w.unlockLevel()) {
            m.send(p, "wings.locked", "level", w.unlockLevel());
            return;
        }
        boolean now = desired == null ? !d.isWingsEnabled() : desired;
        d.setWingsEnabled(now);
        m.send(p, now ? "wings.on" : "wings.off");
    }
}
