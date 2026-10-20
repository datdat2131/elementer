package com.astrax.elementer;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

/** Nạp config.yml, elements.yml, skills.yml, messages.yml và cung cấp truy cập đã kiểm tra. */
public final class ElementConfig {
    private final JavaPlugin plugin;
    private final Logger log;
    private final Messages messages = new Messages();

    private volatile FileConfiguration main;
    private volatile Map<Element, ElementDef> elements = Map.of();
    private volatile Map<String, SkillDef> skills = Map.of();
    private volatile Map<Element, List<SkillDef>> skillsByElement = Map.of();
    private volatile AffinityTable affinity = new AffinityTable(1.25, 0.85, Map.of());
    private volatile ExpCurve curve = new ExpCurve(100, 1.12, 50, 1);
    private volatile Map<EntityType, Element> mobElements = Map.of();
    private volatile Set<EntityType> undead = Set.of();
    private volatile Set<EntityType> protectedTypes = Set.of();
    private volatile Map<Material, Integer> miningExp = Map.of();
    private volatile Map<EntityType, Integer> mobExp = Map.of();
    private volatile Map<Integer, LevelReward> levelRewards = Map.of();
    private volatile Set<String> disabledWorlds = Set.of();

    public ElementConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    public void load() {
        for (String f : List.of("config.yml", "elements.yml", "skills.yml", "messages.yml")) {
            if (!new File(plugin.getDataFolder(), f).exists()) plugin.saveResource(f, false);
        }
        plugin.reloadConfig();
        this.main = plugin.getConfig();
        YamlConfiguration msgs = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
        int added = ConfigUpdates.mergeMissing(msgs, ConfigUpdates.bundled(plugin, "messages.yml"));
        if (added > 0) {
            log.warning("messages.yml của bạn thiếu " + added + " tin nhắn mới - đã dùng giá trị mặc định trong bộ nhớ."
                    + " Chạy /elementeradmin updateconfig để cập nhật file (tự sao lưu).");
        }
        messages.use(msgs);

        YamlConfiguration el = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "elements.yml"));
        YamlConfiguration sk = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "skills.yml"));
        ConfigUpdates.warnMissing(plugin, "elements.yml", el, "elements");
        ConfigUpdates.warnMissing(plugin, "skills.yml", sk, "skills");
        ElementParser ep = new ElementParser(log);
        SkillParser sp = new SkillParser(log);

        this.elements = ep.elements(el);
        this.affinity = ep.affinity(el);
        this.mobElements = ep.mobElements(el);
        this.undead = ep.entityTypes(el.getStringList("undead"), "undead");
        this.protectedTypes = ep.entityTypes(main.getStringList("combat.protected-types"), "combat.protected-types");
        this.skills = sp.parse(sk, this.elements);
        this.skillsByElement = sp.group(this.skills);
        this.curve = new ExpCurve(main.getLong("exp.base", 100), main.getDouble("exp.growth", 1.12),
                main.getInt("exp.max-level", 50), main.getInt("exp.skill-points-per-level", 1));
        this.miningExp = parseMining();
        this.mobExp = parseMobExp();
        this.levelRewards = parseRewards();
        this.disabledWorlds = new HashSet<>(main.getStringList("disabled-worlds"));
        Fx.setEnabled(main.getBoolean("effects.particles", true));

        for (Element e : Element.values()) {
            List<SkillDef> l = skillsByElement.get(e);
            if (elements.containsKey(e) && (l == null || l.isEmpty())) {
                log.warning("Nguyên tố " + e + " không có kỹ năng hợp lệ nào trong skills.yml.");
            }
        }
        log.info("Đã tải " + elements.size() + " nguyên tố, " + skills.size() + " kỹ năng.");
    }

    // ------------------------------------------------------------------ Nguồn EXP và phần thưởng

    private Map<Material, Integer> parseMining() {
        Map<Material, Integer> out = new HashMap<>();
        ConfigurationSection s = main.getConfigurationSection("exp.sources.mining.blocks");
        if (s == null) return out;
        for (String k : s.getKeys(false)) {
            Material m = Material.matchMaterial(k);
            if (m == null || !m.isBlock()) log.warning("exp.sources.mining.blocks: khối không hợp lệ '" + k + "'.");
            else out.put(m, Math.max(0, s.getInt(k)));
        }
        return out;
    }

    private Map<EntityType, Integer> parseMobExp() {
        Map<EntityType, Integer> out = new HashMap<>();
        ConfigurationSection s = main.getConfigurationSection("exp.sources.mob-kill.mobs");
        if (s == null) return out;
        for (String k : s.getKeys(false)) {
            EntityType t = ElementParser.entityType(k);
            if (t == null) log.warning("exp.sources.mob-kill.mobs: loại không hợp lệ '" + k + "'.");
            else out.put(t, Math.max(0, s.getInt(k)));
        }
        return out;
    }

    private Map<Integer, LevelReward> parseRewards() {
        Map<Integer, LevelReward> out = new TreeMap<>();
        ConfigurationSection s = main.getConfigurationSection("level-rewards");
        if (s == null) return out;
        for (String k : s.getKeys(false)) {
            int lvl;
            try {
                lvl = Integer.parseInt(k);
            } catch (NumberFormatException e) {
                log.warning("level-rewards: '" + k + "' không phải số cấp.");
                continue;
            }
            ConfigurationSection r = s.getConfigurationSection(k);
            if (r == null) continue;
            out.put(lvl, new LevelReward(lvl, r.getString("message", ""), List.copyOf(r.getStringList("commands"))));
        }
        return Collections.unmodifiableMap(out);
    }

    // ------------------------------------------------------------------ Truy cập

    public FileConfiguration main() { return main; }
    public Messages messages() { return messages; }
    public ExpCurve curve() { return curve; }
    public AffinityTable affinity() { return affinity; }

    public ElementDef element(Element e) { return e == null ? null : elements.get(e); }
    public Set<Element> enabledElements() { return elements.keySet(); }

    public SkillDef skill(String id) { return skills.get(id); }
    public Map<String, SkillDef> allSkills() { return skills; }
    public List<SkillDef> skillsOf(Element e) { return skillsByElement.getOrDefault(e, List.of()); }

    public SkillDef skill(Element e, int slot) {
        for (SkillDef d : skillsOf(e)) if (d.slot() == slot) return d;
        return null;
    }

    public Element mobElement(EntityType t) { return mobElements.get(t); }
    public boolean isUndead(EntityType t) { return undead.contains(t); }
    public boolean isProtected(EntityType t) { return protectedTypes.contains(t); }
    public Integer miningExp(Material m) { return miningExp.get(m); }
    public Integer mobExp(EntityType t) { return mobExp.get(t); }
    public Map<Integer, LevelReward> levelRewards() { return levelRewards; }
    public boolean isWorldDisabled(String world) { return disabledWorlds.contains(world); }
}
