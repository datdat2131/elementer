package com.astrax.elementer;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Ghi nhận sát thương lên boss, xử lý boss chết, ẩn thanh máu khi người chơi thoát. */
public final class BossListener implements Listener {
    private final BossService bosses;

    public BossListener(BossService bosses) {
        this.bosses = bosses;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (bosses.get(e.getEntity().getUniqueId()) == null) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p) attacker = p;
        if (attacker != null) bosses.recordDamage(e.getEntity().getUniqueId(), attacker, e.getFinalDamage());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent e) {
        bosses.onDeath(e);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        bosses.hideBars(e.getPlayer());
    }
}
