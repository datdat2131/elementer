package com.astrax.elementer;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Nội tại nguyên tố dưới dạng attribute modifier tạm thời (mất khi hồi sinh/thoát nên được áp dụng lại khi cần). */
public final class PassiveService {
    private final ElementerCore plugin;
    private final ElementConfig cfg;
    private final Set<String> warnedAttributes = new HashSet<>();

    public PassiveService(ElementerCore plugin, ElementConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    public void apply(Player p, PlayerData d) {
        remove(p);
        if (d == null || d.getElement() == null) return;
        ElementDef def = cfg.element(d.getElement());
        if (def == null) return;
        for (AttrDef a : def.attributes()) {
            Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(a.attribute()));
            if (attr == null) {
                if (warnedAttributes.add(a.attribute())) {
                    plugin.getLogger().warning("Attribute không tồn tại trong elements.yml: " + a.attribute());
                }
                continue;
            }
            AttributeInstance inst = p.getAttribute(attr);
            double amount = a.amount(d.getLevel());
            if (inst == null || amount == 0) continue;
            inst.addTransientModifier(new AttributeModifier(new NamespacedKey(plugin, "passive_" + a.attribute()),
                    amount, a.operation()));
        }
    }

    public void remove(Player p) {
        String ns = plugin.getName().toLowerCase(Locale.ROOT);
        for (Attribute attr : Registry.ATTRIBUTE) {
            AttributeInstance inst = p.getAttribute(attr);
            if (inst == null) continue;
            for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) {
                if (m.getKey().getNamespace().equals(ns)) inst.removeModifier(m);
            }
        }
        AttributeInstance mh = p.getAttribute(Attribute.MAX_HEALTH);
        if (mh != null && p.getHealth() > mh.getValue()) p.setHealth(mh.getValue());
    }
}
