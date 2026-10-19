package com.astrax.elementer;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Trang đổi Tinh Thể lấy Vé Nguyên Tố (nguyên liệu cần/còn thiếu, boss rơi) và trang thông tin boss (kỹ năng, vật phẩm rớt, lời thoại). */
final class ExchangePages {
    private static final String[] EVENTS = {"spawn", "cast", "half", "enrage", "kill", "death"};

    private final ElementConfig cfg;
    private final ElementService elements;
    private final GuiItems ui;
    private final CrystalService crystals;
    private final BossConfig bossCfg;

    ExchangePages(ElementConfig cfg, ElementService elements, GuiItems ui, CrystalService crystals, BossConfig bossCfg) {
        this.cfg = cfg;
        this.elements = elements;
        this.ui = ui;
        this.crystals = crystals;
        this.bossCfg = bossCfg;
    }

    private String fmt(String key, Object... kv) {
        return Messages.format(ui.raw(key), kv);
    }

    // ------------------------------------------------------------------ Trang đổi vé

    void exchange(Player p, PlayerData d) {
        Inventory inv = ui.create(GuiHolder.Page.EXCHANGE, 36, "gui.title-exchange");
        Element[] all = Element.values();
        int max = elements.ownership().maxOwned();
        for (int i = 0; i < all.length; i++) {
            ElementDef def = cfg.element(all[i]);
            Integer cost = bossCfg.exchangeCost(all[i]);
            if (def == null || cost == null) continue;
            int have = crystals.count(p, all[i]);
            boolean owned = d.hasUnlocked(all[i]);
            BossDef boss = bossCfg.bossFor(all[i]);
            List<String> lore = new ArrayList<>(def.description());
            lore.add(" ");
            lore.add(fmt("gui.lore.exchange-need", "cost", cost, "element", def.colored()));
            lore.add(have >= cost ? ui.raw("gui.lore.exchange-enough")
                    : fmt("gui.lore.exchange-have", "have", have, "missing", cost - have));
            if (boss != null) {
                lore.add(fmt("gui.lore.exchange-boss", "boss", boss.name()));
                lore.add(fmt("gui.lore.exchange-drop", "min", boss.crystalsMin(), "max", boss.crystalsMax(),
                        "element", def.colored()));
            }
            lore.add(" ");
            if (owned) lore.add(ui.raw("gui.lore.element-owned"));
            else if (d.unlockedCount() >= max) lore.add(fmt("gui.lore.element-max", "max", max));
            else lore.add(ui.raw("gui.lore.hint-exchange"));
            if (boss != null) lore.add(ui.raw("gui.lore.hint-boss"));
            inv.setItem(GuiHolder.ELEMENT_SLOTS[i], ui.item(bossCfg.crystalMaterial(all[i]), def.colored(), lore,
                    !owned && have >= cost));
        }
        inv.setItem(GuiHolder.ELEMENT_BACK_SLOT, ui.item(Material.ARROW, ui.raw("gui.back"), null, false));
        p.openInventory(inv);
    }

    // ------------------------------------------------------------------ Trang thông tin boss

    void boss(Player p, PlayerData d, Element e) {
        BossDef boss = bossCfg.bossFor(e);
        ElementDef def = cfg.element(e);
        if (boss == null || def == null) {
            exchange(p, d);
            return;
        }
        Inventory inv = ui.create(GuiHolder.Page.BOSS, 27, "gui.title-boss", e);
        inv.setItem(4, ui.item(icon(boss, def), boss.name(), overview(boss, def), true));
        inv.setItem(10, ui.item(Material.BOOK, ui.raw("gui.boss.abilities-title"), abilities(boss), false));
        inv.setItem(12, ui.item(Material.CHEST, ui.raw("gui.boss.drops-title"), drops(boss, def), false));
        inv.setItem(14, ui.item(Material.WRITABLE_BOOK, ui.raw("gui.boss.lines-title"), lines(boss), false));
        Integer cost = bossCfg.exchangeCost(e);
        List<String> ex = new ArrayList<>();
        if (cost != null) {
            ex.add(fmt("gui.lore.exchange-need", "cost", cost, "element", def.colored()));
            ex.add(fmt("gui.lore.crystal-count", "have", crystals.count(p, e), "cost", cost));
            ex.add(" ");
            ex.add(ui.raw("gui.lore.hint-exchange"));
        }
        inv.setItem(16, ui.item(bossCfg.crystalMaterial(e), ui.raw("gui.boss.exchange-title"), ex, false));
        inv.setItem(22, ui.item(Material.ARROW, ui.raw("gui.back"), null, false));
        p.openInventory(inv);
    }

    private Material icon(BossDef boss, ElementDef def) {
        Material egg = Material.matchMaterial(boss.type().name() + "_SPAWN_EGG");
        return egg == null ? def.icon() : egg;
    }

    private List<String> overview(BossDef b, ElementDef def) {
        List<String> l = new ArrayList<>();
        l.add(fmt("gui.boss.info-element", "element", def.colored()));
        l.add(fmt("gui.boss.info-hp", "hp", (int) b.health()));
        l.add(fmt("gui.boss.info-armor", "armor", (int) b.armor()));
        l.add(fmt("gui.boss.info-enrage", "percent", (int) Math.round(b.enrageBelow() * 100),
                "mult", Text.num(b.enrageMultiplier())));
        return l;
    }

    private List<String> abilities(BossDef b) {
        List<String> l = new ArrayList<>();
        for (SkillDef s : b.abilities()) {
            String dmg = s.damage() > 0 ? Text.num(s.damage()) : "-";
            l.add(fmt("gui.boss.ability-line", "name", s.name(), "type", fmt("gui.boss.type-" + s.type().name().toLowerCase(Locale.ROOT)),
                    "damage", dmg, "cd", Text.num(s.cooldown())));
        }
        return l;
    }

    private List<String> drops(BossDef b, ElementDef def) {
        List<String> l = new ArrayList<>();
        l.add(fmt("gui.boss.drops-crystal", "min", b.crystalsMin(), "max", b.crystalsMax(), "element", def.colored()));
        l.add(fmt("gui.boss.drops-exp", "exp", b.exp()));
        for (BossDrop d : b.drops()) {
            String amount = d.min() == d.max() ? String.valueOf(d.min()) : d.min() + "-" + d.max();
            l.add(fmt("gui.boss.drops-item", "amount", amount, "item", BossRewards.itemName(d),
                    "chance", (int) Math.round(d.chance() * 100)));
        }
        l.add(" ");
        double share = bossCfg.settings().getDouble("settings.min-damage-share", 0.05) * 100;
        l.add(fmt("gui.boss.drops-note", "share", (int) Math.round(share)));
        return l;
    }

    private List<String> lines(BossDef b) {
        List<String> l = new ArrayList<>();
        Map<String, List<String>> lines = b.lines();
        for (String ev : EVENTS) {
            List<String> pool = lines.get(ev);
            if (pool == null || pool.isEmpty()) continue;
            l.add(fmt("gui.boss.line-entry", "event", fmt("gui.boss.event-" + ev), "text", pool.get(0).replace("{player}", "...")));
        }
        return l;
    }
}
