package com.astrax.elementer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Spawn boss định kỳ tại điểm do quản trị đặt (/elementeradmin boss setspawn). Cấu hình ở mục spawner trong bosses.yml
 * (chu kỳ, bật/tắt, tự biến mất); tỉ lệ ra boss ở spawn-weight/spawn-chance của từng boss. Vị trí và mốc spawn gần nhất
 * lưu trong spawner-state.yml nên restart không làm mất giờ đếm.
 */
public final class BossSpawner {
    private final ElementerCore plugin;
    private final BossConfig bossCfg;
    private final BossService bosses;
    private final File stateFile;
    private YamlConfiguration state = new YamlConfiguration();
    private BukkitTask task;
    private UUID current;

    public BossSpawner(ElementerCore plugin, BossConfig bossCfg, BossService bosses) {
        this.plugin = plugin;
        this.bossCfg = bossCfg;
        this.bosses = bosses;
        this.stateFile = new File(plugin.getDataFolder(), "spawner-state.yml");
    }

    public void load() {
        this.state = YamlConfiguration.loadConfiguration(stateFile);
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 200L, 400L); // kiểm tra mỗi 20 giây
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    // ------------------------------------------------------------------ Trạng thái

    public boolean enabled() { return bossCfg.settings().getBoolean("spawner.enabled", true); }

    public long intervalMs() {
        return Math.max(1, bossCfg.settings().getLong("spawner.interval-minutes", 120)) * 60_000L;
    }

    /** Điểm spawn đã đặt, hoặc null nếu chưa đặt/thế giới không tồn tại. */
    public Location location() {
        String w = state.getString("location.world");
        World world = w == null ? null : Bukkit.getWorld(w);
        if (world == null) return null;
        return new Location(world, state.getDouble("location.x"), state.getDouble("location.y"),
                state.getDouble("location.z"), (float) state.getDouble("location.yaw"), 0f);
    }

    public boolean hasLocation() { return state.getString("location.world") != null; }

    public void setLocation(Location l) {
        state.set("location.world", l.getWorld().getName());
        state.set("location.x", l.getX());
        state.set("location.y", l.getY());
        state.set("location.z", l.getZ());
        state.set("location.yaw", (double) l.getYaw());
        state.set("last-spawn", System.currentTimeMillis()); // boss đầu tiên sau đúng một chu kỳ
        save();
    }

    public void clearLocation() {
        state.set("location", null);
        state.set("last-spawn", null);
        save();
    }

    public long remainingMs() {
        return BossSchedule.remainingMs(System.currentTimeMillis(), state.getLong("last-spawn", 0), intervalMs());
    }

    private void save() {
        try {
            state.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Không lưu được spawner-state.yml", e);
        }
    }

    // ------------------------------------------------------------------ Spawn

    private void tick() {
        if (!enabled() || !hasLocation() || remainingMs() > 0) return;
        if (bossCfg.settings().getBoolean("spawner.skip-if-alive", true) && current != null && bosses.isAlive(current)) return;
        Location loc = location();
        if (loc == null) return;
        BossDef def = roll();
        if (def == null) return;
        spawnAt(def, loc);
    }

    /** Spawn ngay theo điểm đã đặt (lệnh spawnnow); {@code forced} = null thì tung xúc xắc như bình thường. */
    public ActiveBoss spawnNow(BossDef forced) {
        Location loc = location();
        if (loc == null) return null;
        BossDef def = forced != null ? forced : roll();
        return def == null ? null : spawnAt(def, loc);
    }

    private ActiveBoss spawnAt(BossDef def, Location loc) {
        ActiveBoss b = bosses.spawnTimed(def, loc);
        if (b != null) {
            current = b.entity.getUniqueId();
            state.set("last-spawn", System.currentTimeMillis());
            save();
        }
        return b;
    }

    /** Tung xúc xắc theo spawn-chance (huyền thoại) và spawn-weight (boss thường) của từng boss. */
    BossDef roll() {
        List<BossSchedule.Candidate> list = new ArrayList<>();
        for (BossDef d : bossCfg.bosses().values()) list.add(new BossSchedule.Candidate(d.id(), d.spawnWeight(), d.spawnChance()));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        String id = BossSchedule.pick(list, r.nextDouble(), r.nextDouble());
        return id == null ? null : bossCfg.boss(id);
    }
}
