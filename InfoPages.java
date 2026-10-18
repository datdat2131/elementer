package com.astrax.elementer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/** Các trang chỉ xem/chọn: menu chính, thức tỉnh, chỉ số, tương khắc, bảng xếp hạng. */
final class InfoPages {
    private final ElementerCore plugin;
    private final ElementConfig cfg;
    private final ElementService elements;
    private final GuiItems ui;

    InfoPages(ElementerCore plugin, ElementConfig cfg, ElementService elements, GuiItems ui) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.elements = elements;
        this.ui = ui;
    }

    void main(Player p, PlayerData d) {
        ElementDef def = cfg.element(d.getElement());
        if (def == null) return;
        Inventory inv = ui.create(GuiHolder.Page.MAIN, 27, "gui.title-main");
        inv.setItem(4, ui.item(def.icon(), def.colored(), GuiItems.merge(def.description(), elements.infoLines(d)), true));
        inv.setItem(10, ui.item(Material.BOOK, ui.raw("gui.stats-button"), null, false));
        inv.setItem(12, ui.item(Material.NETHER_STAR, ui.raw("gui.skills-button"),
                List.of(Messages.format(ui.raw("gui.points-button"), "points", d.getSkillPoints())), false));
        inv.setItem(14, ui.item(Material.COMPASS, ui.raw("gui.affinity-button"), null, false));
        inv.setItem(16, ui.item(Material.GOLD_INGOT, ui.raw("gui.top-button"), null, false));
        inv.setItem(20, ui.item(Material.ENDER_EYE, ui.raw("gui.elements-button"), null, false));
        String state = ui.raw(d.isAuraEnabled() ? "gui.aura-on" : "gui.aura-off");
        List<String> auraLore = new ArrayList<>();
        if (def.aura() != null) auraLore.add(Messages.format(ui.raw("gui.lore.aura-info"), "level", def.aura().unlockLevel()));
        auraLore.add(ui.raw("gui.lore.hint-aura"));
        inv.setItem(24, ui.item(Material.FIREWORK_STAR, Messages.format(ui.raw("gui.aura-button"), "state", state), auraLore, d.isAuraEnabled()));
        if (def.wings() != null) {
            String ws = ui.raw(d.isWingsEnabled() ? "gui.aura-on" : "gui.aura-off");
            inv.setItem(26, ui.item(Material.ELYTRA, Messages.format(ui.raw("gui.wings-button"), "state", ws),
                    List.of(ui.raw("gui.lore.hint-aura")), d.isWingsEnabled()));
        }
        inv.setItem(22, ui.item(Material.BARRIER, ui.raw("gui.close"), null, false));
        p.openInventory(inv);
    }

    /** Trang nguyên tố: xem trạng thái, chuyển nguyên tố đã sở hữu, mở khóa nguyên tố mới. */
    void awaken(Player p, PlayerData d) {
        Inventory inv = ui.create(GuiHolder.Page.AWAKEN, 36, "gui.title-awaken");
        Element[] all = Element.values();
        for (int i = 0; i < all.length; i++) {
            ElementDef def = cfg.element(all[i]);
            if (def == null) continue;
            boolean active = d.getElement() == all[i];
            boolean owned = d.hasUnlocked(all[i]);
            List<String> lore = new ArrayList<>(def.description());
            lore.add(" ");
            if (active) {
                lore.add(ui.raw("gui.lore.element-active"));
            } else if (owned) {
                lore.add(ui.raw("gui.lore.element-owned"));
                lore.add(ui.raw("gui.lore.hint-switch"));
            } else {
                lore.add(ui.raw("gui.lore.element-locked"));
                if (def.voucherOnly()) {
                    lore.add(ui.raw("gui.lore.element-voucher"));
                } else {
                    if (d.unlockedCount() > 0) {
                        UnlockReq r = cfg.unlockReq(d.unlockedCount());
                        lore.add(Messages.format(ui.raw("gui.lore.element-requirement"), "level", r.level(), "points", r.points()));
                    }
                    lore.add(ui.raw("gui.lore.hint-unlock"));
                }
                int max = elements.ownership().maxOwned();
                if (d.unlockedCount() >= max) lore.add(Messages.format(ui.raw("gui.lore.element-max"), "max", max));
            }
            if (def.voucherOnly() && (active || owned)) lore.add(ui.raw("gui.lore.element-limited"));
            if (def.aura() != null) {
                lore.add(Messages.format(ui.raw("gui.lore.aura-info"), "level", def.aura().unlockLevel()));
            }
            inv.setItem(GuiHolder.ELEMENT_SLOTS[i], ui.item(def.icon(), def.colored(), lore, active));
        }
        boolean hasElement = d.getElement() != null;
        inv.setItem(GuiHolder.ELEMENT_BACK_SLOT, ui.item(hasElement ? Material.ARROW : Material.BARRIER,
                ui.raw(hasElement ? "gui.back" : "gui.close"), null, false));
        p.openInventory(inv);
    }

    void stats(Player p, PlayerData d) {
        ElementDef def = cfg.element(d.getElement());
        if (def == null) return;
        Inventory inv = ui.create(GuiHolder.Page.STATS, 27, "gui.title-stats");
        List<String> lore = elements.statsLines(d);
        String title = lore.isEmpty() ? def.colored() : lore.remove(0);
        inv.setItem(13, ui.item(def.icon(), title, lore, true));
        inv.setItem(22, ui.item(Material.ARROW, ui.raw("gui.back"), null, false));
        p.openInventory(inv);
    }

    void affinity(Player p) {
        Inventory inv = ui.create(GuiHolder.Page.AFFINITY, 36, "gui.title-affinity");
        Element[] all = Element.values();
        for (int i = 0; i < all.length; i++) {
            ElementDef def = cfg.element(all[i]);
            if (def == null) continue;
            List<String> strong = new ArrayList<>();
            List<String> weak = new ArrayList<>();
            for (Element o : all) {
                ElementDef od = cfg.element(o);
                if (o == all[i] || od == null) continue;
                double m = cfg.affinity().multiplier(all[i], o);
                if (m > 1.0) strong.add(od.colored());
                else if (m < 1.0) weak.add(od.colored());
            }
            List<String> lore = new ArrayList<>();
            if (strong.isEmpty() && weak.isEmpty()) lore.add(ui.raw("gui.lore.affinity-neutral"));
            if (!strong.isEmpty()) lore.add(Messages.format(ui.raw("gui.lore.affinity-strong"),
                    "list", String.join("&7, ", strong), "mult", cfg.affinity().strong()));
            if (!weak.isEmpty()) lore.add(Messages.format(ui.raw("gui.lore.affinity-weak"),
                    "list", String.join("&7, ", weak), "mult", cfg.affinity().weak()));
            inv.setItem(GuiHolder.ELEMENT_SLOTS[i], ui.item(def.icon(), def.colored(), lore, false));
        }
        inv.setItem(GuiHolder.ELEMENT_BACK_SLOT, ui.item(Material.ARROW, ui.raw("gui.back"), null, false));
        p.openInventory(inv);
    }

    /** Tải bảng xếp hạng ở thread DB rồi mở GUI trên main thread. */
    void top(Player p) {
        elements.top(10).whenComplete((list, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (err != null) {
                plugin.getLogger().log(Level.WARNING, "Không tải được bảng xếp hạng", err);
                return;
            }
            Inventory inv = ui.create(GuiHolder.Page.TOP, 36, "gui.title-top");
            int rank = 1;
            for (TopEntry t : list) {
                Element e = Element.parse(t.element());
                ElementDef def = e == null ? null : cfg.element(e);
                String line = Messages.format(ui.raw("top.line"), "rank", rank, "name", t.name() == null ? "?" : t.name(),
                        "element", def == null ? "?" : def.colored(), "level", t.level(), "exp", t.exp());
                inv.setItem(8 + rank, ui.item(def == null ? Material.PAPER : def.icon(), line, null, rank == 1));
                rank++;
            }
            inv.setItem(31, ui.item(Material.ARROW, ui.raw("gui.back"), null, false));
            p.openInventory(inv);
        }));
    }
}
