package com.astrax.elementer;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Boss nguyên tố: triệu hồi (thủ công hoặc theo điểm hồi sinh), AI chọn mục tiêu + tung kỹ năng, thanh máu,
 * nổi điên khi còn ít máu, ghi nhận sát thương và trao thưởng khi chết. Boss không lưu xuống đĩa (restart là biến mất).
 */
public final class BossService {
    private final ElementerCore plugin;
    private final ElementConfig cfg;
    private final BossConfig bossCfg;
    private final BossAbilities abilities;
    private final BossRewards rewards;
    private final NamespacedKey bossKey;
    private final Map<UUID, ActiveBoss> active = new HashMap<>();
    private long[] spawnReadyAt = new long[0];
    private BukkitTask aiTask;
    private BukkitTask spawnTask;

    public BossService(ElementerCore plugin, ElementConfig cfg, BossConfig bossCfg, ElementService elements,
                       CombatService combat, CrystalService crystals) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.bossCfg = bossCfg;
        this.abilities = new BossAbilities(plugin, cfg, bossCfg, elements, combat);
        this.rewards = new BossRewards(cfg, bossCfg, elements, crystals);
        this.bossKey = new NamespacedKey(plugin, "boss_id");
    }

    // ------------------------------------------------------------------ Vòng đời

    public void start() {
        stop();
        spawnReadyAt = new long[bossCfg.spawns().size()];
        int interval = Math.max(5, Math.min(40, bossCfg.settings().getInt("settings.tick-interval", 10)));
        aiTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkSpawns, 200L, 600L);
    }

    public void stop() {
        if (aiTask != null) aiTask.cancel();
        if (spawnTask != null) spawnTask.cancel();
        aiTask = null;
        spawnTask = null;
    }

    /** Gỡ toàn bộ boss đang sống (khi tắt plugin/reload). */
    public void removeAll() {
        for (ActiveBoss b : new ArrayList<>(active.values())) cleanup(b, false);
        active.clear();
    }

    public ActiveBoss get(UUID entityId) { return active.get(entityId); }

    public void recordDamage(UUID bossId, Player p, double amount) {
        ActiveBoss b = active.get(bossId);
        if (b != null) b.addDamage(p.getUniqueId(), Math.min(amount, b.entity.getHealth()));
    }

    // ------------------------------------------------------------------ Triệu hồi

    /** @return boss mới, hoặc null nếu vượt settings.max-active */
    public ActiveBoss spawn(BossDef def, Location loc, int spawnIndex) {
        if (active.size() >= bossCfg.settings().getInt("settings.max-active", 5)) return null;
        Entity e = loc.getWorld().spawnEntity(loc, def.type());
        if (!(e instanceof LivingEntity le)) {
            e.remove();
            return null;
        }
        le.setPersistent(false);
        le.setRemoveWhenFarAway(false);
        le.setCanPickupItems(false);
        le.setCustomNameVisible(true);
        le.customName(Text.c(def.name()));
        le.setGlowing(true);
        setBase(le, "max_health", def.health());
        setBase(le, "attack_damage", def.attackDamage());
        setBase(le, "armor", def.armor());
        setBase(le, "scale", def.scale());
        setBase(le, "movement_speed", def.speed());
        setBase(le, "knockback_resistance", 0.8);
        le.setHealth(Math.min(def.health(), le.getAttribute(Attribute.MAX_HEALTH).getValue()));
        le.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false));
        le.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, def.id());
        BossBar bar = BossBar.bossBar(Text.c(def.name()), 1f, barColor(def.element()), BossBar.Overlay.NOTCHED_10);
        ActiveBoss b = new ActiveBoss(def, le, bar, spawnIndex);
        active.put(le.getUniqueId(), b);
        Fx.particle(loc.clone().add(0, 1, 0), "EXPLOSION_EMITTER", 1, 0, 0, 0, 0);
        Fx.sound(loc, "entity.wither.spawn", 1f, 1f);
        if (bossCfg.settings().getBoolean("settings.announce", true)) {
            for (Player p : loc.getWorld().getPlayers()) {
                p.sendMessage(cfg.messages().prefixed("boss.spawned", "boss", def.name(),
                        "x", loc.getBlockX(), "y", loc.getBlockY(), "z", loc.getBlockZ()));
            }
        }
        return b;
    }

    private static void setBase(LivingEntity le, String key, double value) {
        Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
        AttributeInstance i = a == null ? null : le.getAttribute(a);
        if (i != null) i.setBaseValue(value);
    }

    private static BossBar.Color barColor(Element e) {
        return switch (e) {
            case FIRE, DRAGON -> BossBar.Color.RED;
            case ICE -> BossBar.Color.BLUE;
            case LIGHTNING -> BossBar.Color.YELLOW;
            case EARTH -> BossBar.Color.GREEN;
            case WIND -> BossBar.Color.WHITE;
            case LIGHT -> BossBar.Color.PINK;
            case DARK -> BossBar.Color.PURPLE;
        };
    }

    // ------------------------------------------------------------------ AI

    private void tick() {
        long now = System.currentTimeMillis();
        for (ActiveBoss b : new ArrayList<>(active.values())) {
            LivingEntity e = b.entity;
            if (!e.isValid()) {
                if (!e.isDead()) cleanup(b, false); // biến mất (chunk unload/bị gỡ), không phải chết
                continue;
            }
            checkEnrage(b);
            updateBar(b);
            Player target = nearestPlayer(b);
            if (target == null) continue;
            if (e instanceof Mob m) m.setTarget(target);
            abilities.tryCast(b, target, now);
        }
    }

    private Player nearestPlayer(ActiveBoss b) {
        World w = b.entity.getWorld();
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (Player p : w.getNearbyPlayers(b.entity.getLocation(), b.def.detectRange())) {
            if (!BossAbilities.validPlayer(p, w)) continue;
            double d = p.getLocation().distanceSquared(b.entity.getLocation());
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private void checkEnrage(ActiveBoss b) {
        if (b.enraged || b.def.enrageBelow() <= 0) return;
        double max = b.entity.getAttribute(Attribute.MAX_HEALTH).getValue();
        if (b.entity.getHealth() / max > b.def.enrageBelow()) return;
        b.enraged = true;
        setBase(b.entity, "attack_damage", b.def.attackDamage() * b.def.enrageMultiplier());
        setBase(b.entity, "movement_speed", Math.min(0.6, b.def.speed() * 1.2));
        Fx.ring(b.entity.getLocation(), 3.0, "FLAME", 32);
        Fx.sound(b.entity.getLocation(), "entity.ender_dragon.growl", 1.5f, 0.8f);
        for (Player p : b.entity.getWorld().getNearbyPlayers(b.entity.getLocation(), 48)) {
            p.sendMessage(cfg.messages().prefixed("boss.enraged", "boss", b.def.name()));
        }
    }

    private void updateBar(ActiveBoss b) {
        double max = b.entity.getAttribute(Attribute.MAX_HEALTH).getValue();
        double hp = Math.max(0, b.entity.getHealth());
        b.bar.progress((float) Math.max(0, Math.min(1, hp / max)));
        b.bar.name(Text.c(b.def.name() + " &7" + (int) Math.ceil(hp) + "/" + (int) max));
        double range = bossCfg.settings().getDouble("settings.bar-range", 48);
        World w = b.entity.getWorld();
        List<UUID> stale = new ArrayList<>();
        for (UUID id : b.viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.getWorld().equals(w) || p.getLocation().distance(b.entity.getLocation()) > range * 1.2) {
                if (p != null) p.hideBossBar(b.bar);
                stale.add(id);
            }
        }
        b.viewers.removeAll(stale);
        for (Player p : w.getNearbyPlayers(b.entity.getLocation(), range)) {
            if (b.viewers.add(p.getUniqueId())) p.showBossBar(b.bar);
        }
    }

    public void hideBars(Player p) {
        for (ActiveBoss b : active.values()) {
            if (b.viewers.remove(p.getUniqueId())) p.hideBossBar(b.bar);
        }
    }

    private void cleanup(ActiveBoss b, boolean died) {
        for (UUID id : b.viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(b.bar);
        }
        b.viewers.clear();
        active.remove(b.entity.getUniqueId());
        if (b.spawnIndex >= 0 && b.spawnIndex < spawnReadyAt.length && died) {
            spawnReadyAt[b.spawnIndex] = System.currentTimeMillis()
                    + bossCfg.spawns().get(b.spawnIndex).respawnMinutes() * 60_000L;
        }
        if (!died && b.entity.isValid()) b.entity.remove();
    }

    // ------------------------------------------------------------------ Chết / thưởng

    public void onDeath(EntityDeathEvent e) {
        ActiveBoss b = active.get(e.getEntity().getUniqueId());
        if (b == null) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        rewards.give(b);
        cleanup(b, true);
    }

    // ------------------------------------------------------------------ Điểm hồi sinh tự động

    private void checkSpawns() {
        List<BossSpawnPoint> points = bossCfg.spawns();
        long now = System.currentTimeMillis();
        for (int i = 0; i < points.size() && i < spawnReadyAt.length; i++) {
            if (spawnReadyAt[i] > now || aliveAt(i)) continue;
            BossSpawnPoint sp = points.get(i);
            World w = Bukkit.getWorld(sp.world());
            BossDef def = bossCfg.boss(sp.bossId());
            if (w == null || def == null) continue;
            Location loc = new Location(w, sp.x(), sp.y(), sp.z());
            if (!w.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue; // không ép tải chunk
            double near = bossCfg.settings().getDouble("settings.spawn-when-player-within", 96);
            if (w.getNearbyPlayers(loc, near).isEmpty()) continue;
            spawn(def, loc, i);
        }
    }

    private boolean aliveAt(int index) {
        for (ActiveBoss b : active.values()) if (b.spawnIndex == index) return true;
        return false;
    }

    // ------------------------------------------------------------------ Quản trị

    public List<String> describe() {
        List<String> out = new ArrayList<>();
        for (ActiveBoss b : active.values()) {
            Location l = b.entity.getLocation();
            out.add(String.format(Locale.ROOT, "%s (%s) HP %.0f/%.0f tại %s %d %d %d", b.def.id(), b.def.element(),
                    b.entity.getHealth(), b.entity.getAttribute(Attribute.MAX_HEALTH).getValue(),
                    l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ()));
        }
        return out;
    }

    /** @return số boss đã gỡ (null/"all" = tất cả) */
    public int kill(String idOrAll) {
        int n = 0;
        for (ActiveBoss b : new ArrayList<>(active.values())) {
            if (idOrAll == null || idOrAll.equalsIgnoreCase("all") || b.def.id().equalsIgnoreCase(idOrAll)) {
                cleanup(b, false);
                n++;
            }
        }
        return n;
    }
}
