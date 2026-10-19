package com.astrax.elementer;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Đọc bosses.yml: boss, kỹ năng boss, nguyên liệu (tinh thể), giá đổi vé và điểm hồi sinh. Giá trị sai bị cảnh báo và bỏ qua. */
public final class BossConfig {
    private final JavaPlugin plugin;
    private final ElementConfig cfg;
    private final Logger log;

    private volatile YamlConfiguration yml = new YamlConfiguration();
    private volatile Map<String, BossDef> bosses = Map.of();
    private volatile Map<Element, Material> crystalMaterial = Map.of();
    private volatile Map<Element, Integer> exchangeCost = Map.of();
    private volatile List<BossSpawnPoint> spawns = List.of();

    public BossConfig(JavaPlugin plugin, ElementConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.log = plugin.getLogger();
    }

    public void load() {
        File f = new File(plugin.getDataFolder(), "bosses.yml");
        if (!f.exists()) plugin.saveResource("bosses.yml", false);
        this.yml = YamlConfiguration.loadConfiguration(f);
        this.crystalMaterial = parseCrystals();
        this.exchangeCost = parseExchange();
        this.bosses = parseBosses();
        this.spawns = parseSpawns();
        log.info("Đã tải " + bosses.size() + " boss, " + exchangeCost.size() + " nguyên tố có thể đổi vé.");
    }

    // ------------------------------------------------------------------ Parse

    private Map<Element, Material> parseCrystals() {
        Map<Element, Material> out = new EnumMap<>(Element.class);
        ConfigurationSection s = yml.getConfigurationSection("crystals");
        if (s == null) return out;
        for (String k : s.getKeys(false)) {
            Element e = Element.parse(k);
            Material m = Material.matchMaterial(s.getString(k, ""));
            if (e == null || m == null || !m.isItem()) log.warning("bosses.yml crystals: mục không hợp lệ '" + k + "'.");
            else out.put(e, m);
        }
        return out;
    }

    private Map<Element, Integer> parseExchange() {
        Map<Element, Integer> out = new EnumMap<>(Element.class);
        ConfigurationSection s = yml.getConfigurationSection("exchange");
        if (s == null) return out;
        for (String k : s.getKeys(false)) {
            Element e = Element.parse(k);
            int cost = s.getInt(k, 0);
            if (e == null || cost < 1 || cfg.element(e) == null) log.warning("bosses.yml exchange: mục không hợp lệ '" + k + "'.");
            else out.put(e, cost);
        }
        return out;
    }

    private Map<String, BossDef> parseBosses() {
        Map<String, BossDef> out = new LinkedHashMap<>();
        ConfigurationSection root = yml.getConfigurationSection("bosses");
        if (root == null) return out;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            BossDef def = s == null ? null : one(id, s);
            if (def != null) out.put(id.toLowerCase(Locale.ROOT), def);
        }
        return out;
    }

    private BossDef one(String id, ConfigurationSection s) {
        Element el = Element.parse(s.getString("element"));
        if (el == null || cfg.element(el) == null) {
            log.warning("Boss '" + id + "': nguyên tố không hợp lệ hoặc đang tắt - bỏ qua.");
            return null;
        }
        EntityType type;
        try {
            type = EntityType.valueOf(s.getString("entity", "").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("Boss '" + id + "': entity không hợp lệ - bỏ qua.");
            return null;
        }
        if (type.getEntityClass() == null || !org.bukkit.entity.LivingEntity.class.isAssignableFrom(type.getEntityClass())
                || !type.isSpawnable()) {
            log.warning("Boss '" + id + "': entity phải là sinh vật spawn được - bỏ qua.");
            return null;
        }
        List<SkillDef> abilities = abilities(id, el, s.getConfigurationSection("abilities"));
        if (abilities.isEmpty()) {
            log.warning("Boss '" + id + "': không có kỹ năng hợp lệ - bỏ qua.");
            return null;
        }
        int cmin = Math.max(0, s.getInt("rewards.crystals-min", 4));
        return new BossDef(id.toLowerCase(Locale.ROOT), el, s.getString("name", id), type,
                Math.max(20, Math.min(10000, s.getDouble("health", 500))), Math.max(0, s.getDouble("attack-damage", 8)),
                Math.max(0, s.getDouble("armor", 6)), Math.max(0.5, Math.min(4, s.getDouble("scale", 1.5))),
                Math.max(0.1, Math.min(0.6, s.getDouble("speed", 0.26))), Math.max(8, Math.min(64, s.getDouble("detect-range", 24))),
                Math.max(0, Math.min(0.9, s.getDouble("enrage-below", 0.4))), Math.max(1, Math.min(2, s.getDouble("enrage-multiplier", 1.3))),
                cmin, Math.max(cmin, s.getInt("rewards.crystals-max", 8)), Math.max(0, s.getLong("rewards.exp", 500)), abilities,
                lines(s.getConfigurationSection("lines")), drops(id, s.getMapList("rewards.items")));
    }

    private List<BossDrop> drops(String bossId, List<Map<?, ?>> raw) {
        List<BossDrop> out = new ArrayList<>();
        for (Map<?, ?> m : raw) {
            Material mat = Material.matchMaterial(String.valueOf(m.get("material")));
            if (mat == null || !mat.isItem()) {
                log.warning("Boss '" + bossId + "' rewards.items: vật phẩm không hợp lệ '" + m.get("material") + "'.");
                continue;
            }
            int min = Math.max(1, Math.min(64, (int) num(m.get("min") == null ? 1 : m.get("min"))));
            int max = Math.max(min, Math.min(64, (int) num(m.get("max") == null ? min : m.get("max"))));
            double chance = Math.max(0, Math.min(1, m.get("chance") == null ? 1.0 : num(m.get("chance"))));
            Object nm = m.get("name");
            out.add(new BossDrop(mat, min, max, chance, nm == null ? null : String.valueOf(nm)));
        }
        return List.copyOf(out);
    }

    /** Lời thoại theo sự kiện: spawn, cast, enrage, half, kill, death. */
    private Map<String, List<String>> lines(ConfigurationSection s) {
        Map<String, List<String>> out = new HashMap<>();
        if (s == null) return out;
        for (String k : s.getKeys(false)) {
            List<String> l = s.getStringList(k);
            if (!l.isEmpty()) out.put(k.toLowerCase(Locale.ROOT), List.copyOf(l));
        }
        return out;
    }

    private List<SkillDef> abilities(String bossId, Element el, ConfigurationSection s) {
        List<SkillDef> out = new ArrayList<>();
        if (s == null) return out;
        int n = 0;
        for (String key : s.getKeys(false)) {
            ConfigurationSection a = s.getConfigurationSection(key);
            if (a == null) continue;
            String t = a.getString("type", "").toUpperCase(Locale.ROOT);
            SkillType type;
            switch (t) {
                case "PROJECTILE" -> type = SkillType.PROJECTILE;
                case "AOE_SELF" -> type = SkillType.AOE_SELF;
                case "AOE_TARGET" -> type = SkillType.AOE_TARGET;
                case "HEAL_SELF" -> type = SkillType.HEAL_AREA; // hồi máu bản thân: power = % máu tối đa (0-1)
                default -> {
                    log.warning("Boss '" + bossId + "' kỹ năng '" + key + "': type không hợp lệ (PROJECTILE, AOE_SELF, AOE_TARGET, HEAL_SELF).");
                    continue;
                }
            }
            Map<String, Double> fx = new HashMap<>();
            ConfigurationSection fs = a.getConfigurationSection("effects");
            if (fs != null) {
                for (String k : fs.getKeys(false)) {
                    if (fs.isInt(k) || fs.isDouble(k) || fs.isLong(k)) fx.put(k, fs.getDouble(k));
                }
            }
            n++;
            out.add(new SkillDef("boss:" + bossId + ":" + key, el, n, a.getString("name", key), Material.PAPER, List.of(), type, false, 1,
                    Math.max(1, a.getDouble("cooldown", 10)), Math.max(0, a.getDouble("damage", 0)),
                    Math.max(0, a.getDouble("range", 20)), Math.max(0, a.getDouble("radius", 0)),
                    Math.max(0, Math.min(1, a.getDouble("power", 0.1))), 0, 1, 0, 1,
                    a.getString("particle", "CRIT"), a.getString("sound", ""), Map.copyOf(fx)));
        }
        return out;
    }

    private List<BossSpawnPoint> parseSpawns() {
        List<BossSpawnPoint> out = new ArrayList<>();
        for (Map<?, ?> m : yml.getMapList("spawns")) {
            String boss = String.valueOf(m.get("boss")).toLowerCase(Locale.ROOT);
            if (!bosses.containsKey(boss) || m.get("world") == null) {
                log.warning("bosses.yml spawns: mục không hợp lệ (boss/world): " + m);
                continue;
            }
            out.add(new BossSpawnPoint(boss, String.valueOf(m.get("world")), num(m.get("x")), num(m.get("y")), num(m.get("z")),
                    Math.max(1, (int) num(m.get("respawn-minutes") == null ? 60 : m.get("respawn-minutes")))));
        }
        return out;
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }

    // ------------------------------------------------------------------ Truy cập

    public YamlConfiguration settings() { return yml; }
    public BossDef boss(String id) { return id == null ? null : bosses.get(id.toLowerCase(Locale.ROOT)); }
    public Map<String, BossDef> bosses() { return bosses; }
    public List<BossSpawnPoint> spawns() { return spawns; }

    /** Boss đầu tiên rơi Tinh Thể của nguyên tố này (null nếu chưa có). */
    public BossDef bossFor(Element e) {
        for (BossDef b : bosses.values()) if (b.element() == e) return b;
        return null;
    }

    public Material crystalMaterial(Element e) { return crystalMaterial.getOrDefault(e, Material.AMETHYST_SHARD); }

    /** Số tinh thể cần đổi một vé, hoặc null nếu nguyên tố này không đổi được bằng tinh thể. */
    public Integer exchangeCost(Element e) { return exchangeCost.get(e); }
}
