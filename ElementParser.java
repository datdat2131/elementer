package com.astrax.elementer;

import org.bukkit.Material;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Đọc elements.yml: định nghĩa nguyên tố, tương khắc, nguyên tố của quái, danh sách xác sống. Giá trị sai bị cảnh báo và bỏ qua. */
final class ElementParser {
    private final Logger log;

    ElementParser(Logger log) {
        this.log = log;
    }

    static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }

    static EntityType entityType(String n) {
        try {
            return EntityType.valueOf(n.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    Set<EntityType> entityTypes(List<String> names, String where) {
        Set<EntityType> out = new HashSet<>();
        for (String n : names) {
            EntityType t = entityType(n);
            if (t == null) log.warning(where + ": loại thực thể không hợp lệ '" + n + "'.");
            else out.add(t);
        }
        return out;
    }

    Map<Element, ElementDef> elements(YamlConfiguration y) {
        Map<Element, ElementDef> out = new EnumMap<>(Element.class);
        ConfigurationSection root = y.getConfigurationSection("elements");
        for (Element e : Element.values()) {
            ConfigurationSection s = root == null ? null : root.getConfigurationSection(e.name());
            if (s == null) {
                log.warning("elements.yml thiếu nguyên tố " + e + " - nguyên tố này bị tắt.");
                continue;
            }
            Material icon = Material.matchMaterial(s.getString("icon", "PAPER"));
            if (icon == null) {
                log.warning("Icon không hợp lệ cho " + e + ", dùng PAPER.");
                icon = Material.PAPER;
            }
            out.put(e, new ElementDef(e, s.getString("name", e.name()), s.getString("symbol", ""),
                    s.getString("color", "&f"), icon, s.getStringList("description"),
                    Math.max(0, s.getDouble("damage-per-level", 0.02)), Math.max(0, s.getDouble("undead-bonus", 0)),
                    attributes(s, e), reductions(s, e), proc(s, e)));
        }
        return out;
    }

    private List<AttrDef> attributes(ConfigurationSection s, Element e) {
        List<AttrDef> attrs = new ArrayList<>();
        for (Map<?, ?> m : s.getMapList("attributes")) {
            Object a = m.get("attribute");
            if (a == null) continue;
            String opName = m.get("operation") == null ? "ADD_NUMBER" : String.valueOf(m.get("operation"));
            AttributeModifier.Operation op;
            try {
                op = AttributeModifier.Operation.valueOf(opName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                log.warning("Operation không hợp lệ '" + opName + "' ở " + e + ".");
                continue;
            }
            attrs.add(new AttrDef(String.valueOf(a).toLowerCase(Locale.ROOT), op, num(m.get("base")), num(m.get("per-level"))));
        }
        return List.copyOf(attrs);
    }

    private Map<DamageCause, Double> reductions(ConfigurationSection s, Element e) {
        Map<DamageCause, Double> red = new EnumMap<>(DamageCause.class);
        ConfigurationSection rs = s.getConfigurationSection("damage-reduction");
        if (rs == null) return red;
        for (String k : rs.getKeys(false)) {
            try {
                red.put(DamageCause.valueOf(k.toUpperCase(Locale.ROOT)), Math.max(0, Math.min(1, rs.getDouble(k))));
            } catch (IllegalArgumentException ex) {
                log.warning("DamageCause không hợp lệ '" + k + "' ở " + e + ".");
            }
        }
        return red;
    }

    private ProcDef proc(ConfigurationSection s, Element e) {
        ConfigurationSection ps = s.getConfigurationSection("proc");
        if (ps == null) return null;
        String type = ps.getString("type", "").toUpperCase(Locale.ROOT);
        if (!type.equals("IGNITE") && !type.equals("SLOW")) {
            log.warning("proc.type không hợp lệ ở " + e + " (dùng IGNITE hoặc SLOW).");
            return null;
        }
        return new ProcDef(type, ps.getDouble("chance"), ps.getDouble("chance-per-level"), ps.getDouble("seconds", 2));
    }

    AffinityTable affinity(YamlConfiguration y) {
        double strong = y.getDouble("affinity.strong-multiplier", 1.25);
        double weak = y.getDouble("affinity.weak-multiplier", 0.85);
        Map<Element, Set<Element>> adv = new EnumMap<>(Element.class);
        ConfigurationSection s = y.getConfigurationSection("affinity.advantages");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                Element a = Element.parse(k);
                if (a == null) {
                    log.warning("affinity: nguyên tố không hợp lệ '" + k + "'.");
                    continue;
                }
                Set<Element> set = EnumSet.noneOf(Element.class);
                for (String v : s.getStringList(k)) {
                    Element b = Element.parse(v);
                    if (b == null || b == a) log.warning("affinity: mục không hợp lệ '" + v + "' trong " + k + ".");
                    else set.add(b);
                }
                adv.put(a, set);
            }
        }
        return new AffinityTable(strong, weak, adv);
    }

    Map<EntityType, Element> mobElements(YamlConfiguration y) {
        Map<EntityType, Element> out = new HashMap<>();
        ConfigurationSection s = y.getConfigurationSection("mob-elements");
        if (s == null) return out;
        for (String k : s.getKeys(false)) {
            Element e = Element.parse(s.getString(k));
            EntityType t = entityType(k);
            if (e == null || t == null) log.warning("mob-elements: mục không hợp lệ '" + k + "'.");
            else out.put(t, e);
        }
        return out;
    }
}
