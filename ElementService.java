package com.astrax.elementer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/** Dữ liệu người chơi online: tải/lưu bất đồng bộ, thức tỉnh, EXP, lên cấp và các thao tác quản trị. */
public final class ElementService {
    private final ElementerCore plugin;
    private final ElementConfig cfg;
    private final ElementStorage storage;
    private final CombatService combat;
    private final PassiveService passives;
    private final RewardService rewards;
    private final ElementInfo info;
    private final ElementOwnership ownership;
    private SkillService skills;

    private final Map<UUID, PlayerData> cache = new HashMap<>();
    private final Set<UUID> loading = new HashSet<>();
    private BukkitTask saveTask;

    public ElementService(ElementerCore plugin, ElementConfig cfg, ElementStorage storage, CombatService combat) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.storage = storage;
        this.combat = combat;
        this.passives = new PassiveService(plugin, cfg);
        this.rewards = new RewardService(plugin, cfg, storage);
        this.info = new ElementInfo(cfg);
        this.ownership = new ElementOwnership(cfg, this, combat);
    }

    public void bind(SkillService skills) { this.skills = skills; }

    public PlayerData get(Player p) { return cache.get(p.getUniqueId()); }
    public PlayerData get(UUID id) { return cache.get(id); }
    public ElementOwnership ownership() { return ownership; }

    // ---- hiển thị / nội tại (ủy quyền)
    public List<String> infoLines(PlayerData d) { return info.infoLines(d); }
    public List<String> statsLines(PlayerData d) { return info.statsLines(d); }
    public void applyPassives(Player p) { passives.apply(p, get(p)); }
    public void removePassives(Player p) { passives.remove(p); }

    public void reapplyAll() {
        for (Player p : Bukkit.getOnlinePlayers()) if (get(p) != null) applyPassives(p);
    }

    // ------------------------------------------------------------------ Tải / lưu

    public void start() {
        long period = Math.max(30, cfg.main().getLong("save-interval-seconds", 300)) * 20L;
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            saveDirty();
            long now = System.currentTimeMillis();
            combat.maintenance(now);
            skills.maintenance(now);
            ownership.purge(now);
        }, period, period);
    }

    public void load(Player p) {
        UUID id = p.getUniqueId();
        if (!loading.add(id)) return;
        String name = p.getName();
        storage.load(id).whenComplete((snap, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            loading.remove(id);
            Player online = Bukkit.getPlayer(id);
            if (online == null) return; // đã thoát trước khi tải xong: không đưa vào bộ nhớ
            if (err != null) {
                // KHÔNG tạo dữ liệu mới để tránh ghi đè dữ liệu tốt đang có trong DB.
                plugin.getLogger().log(Level.SEVERE, "Không tải được dữ liệu của " + name, err);
                cfg.messages().send(online, "data-error");
                return;
            }
            PlayerData d = snap == null
                    ? DataSanitizer.fresh(id, name)
                    : DataSanitizer.sanitize(snap, name, cfg.curve(), this::maxSkillLevel, plugin.getLogger());
            cache.put(id, d);
            applyPassives(online);
            if (d.getElement() == null) online.sendMessage(cfg.messages().prefixed("not-awakened"));
        }));
    }

    private Integer maxSkillLevel(String skillId) {
        SkillDef s = cfg.skill(skillId);
        return s == null ? null : s.maxLevel();
    }

    public void unload(Player p) {
        UUID id = p.getUniqueId();
        loading.remove(id);
        PlayerData d = cache.remove(id);
        combat.clear(id);
        skills.clear(id);
        ownership.clear(id);
        removePassives(p);
        if (d != null && d.isDirty()) {
            d.clearDirty();
            storage.save(d.snapshot()).exceptionally(ex -> {
                plugin.getLogger().log(Level.SEVERE, "Lưu dữ liệu khi thoát thất bại cho " + d.getName(), ex);
                return null;
            });
        }
    }

    public void saveDirty() {
        for (PlayerData d : cache.values()) {
            if (!d.isDirty()) continue;
            d.clearDirty();
            storage.save(d.snapshot()).exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, d::markDirty); // thử lại ở lần lưu sau
                plugin.getLogger().log(Level.SEVERE, "Lưu dữ liệu thất bại cho " + d.getName(), ex);
                return null;
            });
        }
    }

    /** Gọi khi tắt plugin: lưu và chờ xong để không mất dữ liệu. */
    public void shutdown() {
        if (saveTask != null) saveTask.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) removePassives(p);
        List<CompletableFuture<Void>> pending = new ArrayList<>();
        for (PlayerData d : cache.values()) {
            if (d.isDirty()) {
                d.clearDirty();
                pending.add(storage.save(d.snapshot()));
            }
        }
        for (CompletableFuture<Void> f : pending) {
            try {
                f.join();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Lưu dữ liệu khi tắt thất bại", e);
            }
        }
        cache.clear();
    }

    public CompletableFuture<List<TopEntry>> top(int limit) { return storage.top(limit); }

    // ------------------------------------------------------------------ EXP

    /**
     * Cộng EXP nguyên tố.
     * @param bypassCap true cho thao tác quản trị/nhiệm vụ không tính vào giới hạn ngày
     * @return lượng EXP thực nhận
     */
    public long addExp(Player p, long amount, boolean bypassCap) {
        PlayerData d = get(p);
        if (d == null || d.getElement() == null || amount <= 0) return 0;
        if (d.getLevel() >= cfg.curve().maxLevel()) return 0;
        long granted = Math.round(amount * cfg.main().getDouble("exp.multiplier", 1.0));
        if (granted <= 0) return 0;
        long today = System.currentTimeMillis() / 86_400_000L;
        if (d.getDailyDay() != today) {
            d.setDailyDay(today);
            d.setDailyExp(0);
        }
        long cap = cfg.main().getLong("exp.daily-cap", 0);
        if (!bypassCap && cap > 0) {
            long room = cap - d.getDailyExp();
            if (room <= 0) return 0;
            granted = Math.min(granted, room);
            d.setDailyExp(d.getDailyExp() + granted);
        }
        int before = d.getLevel();
        if (cfg.curve().addExp(d, granted) > 0) onLevelUp(p, d, before);
        return granted;
    }

    private void onLevelUp(Player p, PlayerData d, int before) {
        ElementDef def = cfg.element(d.getElement());
        String colored = def == null ? d.getElement().name() : def.colored();
        applyPassives(p);
        Fx.sound(p.getLocation(), "entity.player.levelup", 1f, 1f);
        p.sendMessage(cfg.messages().prefixed("level-up", "element", colored, "level", d.getLevel(),
                "points", d.getSkillPoints()));
        p.showTitle(Title.title(Component.empty(),
                cfg.messages().get("level-up", "element", colored, "level", d.getLevel(), "points", d.getSkillPoints()),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(400))));
        for (int lvl = before + 1; lvl <= d.getLevel(); lvl++) {
            LevelReward r = cfg.levelRewards().get(lvl);
            if (r != null) rewards.claimLevelReward(p, r);
        }
    }

    // ------------------------------------------------------------------ Thao tác quản trị

    public void setLevel(Player p, int level) {
        PlayerData d = get(p);
        if (d == null) return;
        d.setLevel(Math.max(1, Math.min(cfg.curve().maxLevel(), level)));
        d.setExp(0);
        applyPassives(p);
    }

    public void setExp(Player p, long exp) {
        PlayerData d = get(p);
        if (d == null) return;
        long req = cfg.curve().required(d.getLevel());
        d.setExp(req == 0 ? 0 : Math.min(Math.max(0, exp), req - 1));
    }

    /** Quản trị: đặt nguyên tố đang dùng (đồng thời mở khóa nó). */
    public void setElement(Player p, Element e) {
        PlayerData d = get(p);
        if (d == null || e == null) return;
        if (d.getAwakenedAt() == 0) d.setAwakenedAt(System.currentTimeMillis());
        ownership.activate(p, d, e);
    }

    public void reset(Player p) {
        PlayerData d = get(p);
        if (d == null) return;
        d.reset();
        combat.clear(p.getUniqueId());
        skills.clear(p.getUniqueId());
        removePassives(p);
    }
}
