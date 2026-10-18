package com.astrax.elementer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Điểm vào của GUI: mở trang và xử lý click. Việc dựng giao diện nằm ở InfoPages/SkillPage/GuiItems. */
public final class ElementGUI implements Listener {
    private record Pending(Element element, long expiresAt) {}

    private final ElementConfig cfg;
    private final ElementService elements;
    private final SkillService skills;
    private final InfoPages info;
    private final SkillPage skillPage;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public ElementGUI(ElementerCore plugin, ElementConfig cfg, ElementService elements, SkillService skills) {
        this.cfg = cfg;
        this.elements = elements;
        this.skills = skills;
        GuiItems ui = new GuiItems(cfg);
        this.info = new InfoPages(plugin, cfg, elements, ui);
        this.skillPage = new SkillPage(cfg, skills, ui);
    }

    // ------------------------------------------------------------------ Mở trang

    public void openMain(Player p) {
        PlayerData d = elements.get(p);
        if (d == null) {
            cfg.messages().send(p, "data-loading");
            return;
        }
        if (d.getElement() == null) info.awaken(p, d);
        else info.main(p, d);
    }

    private void openSkills(Player p) {
        PlayerData d = elements.get(p);
        if (!skillPage.open(p, d)) openMain(p);
    }

    private void openIfAwakened(Player p, java.util.function.BiConsumer<Player, PlayerData> page) {
        PlayerData d = elements.get(p);
        if (d == null || d.getElement() == null) openMain(p);
        else page.accept(p, d);
    }

    // ------------------------------------------------------------------ Click

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof GuiHolder h)) return;
        e.setCancelled(true); // không cho lấy/đặt/di chuyển item trong GUI (kể cả shift-click, phím số)
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(top)) return;
        int slot = e.getRawSlot();
        switch (h.page) {
            case MAIN -> clickMain(p, slot);
            case AWAKEN -> clickAwaken(p, slot);
            case SKILLS -> clickSkills(p, slot, e.isRightClick());
            case STATS, AFFINITY -> {
                if (slot == 22) openMain(p);
            }
            case TOP -> {
                if (slot == 31) openMain(p);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof GuiHolder) e.setCancelled(true);
    }

    private void clickMain(Player p, int slot) {
        switch (slot) {
            case 10 -> openIfAwakened(p, info::stats);
            case 12 -> openSkills(p);
            case 14 -> info.affinity(p);
            case 16 -> info.top(p);
            case 22 -> p.closeInventory();
            default -> { }
        }
    }

    private void clickAwaken(Player p, int slot) {
        if (slot == 22) {
            p.closeInventory();
            return;
        }
        Element chosen = null;
        Element[] all = Element.values();
        for (int i = 0; i < GuiHolder.ELEMENT_SLOTS.length; i++) if (GuiHolder.ELEMENT_SLOTS[i] == slot) chosen = all[i];
        ElementDef def = chosen == null ? null : cfg.element(chosen);
        if (def == null) return;
        long now = System.currentTimeMillis();
        pending.values().removeIf(x -> x.expiresAt() < now);
        Pending pd = pending.get(p.getUniqueId());
        if (pd == null || pd.element() != chosen) { // lần bấm đầu: yêu cầu xác nhận
            long ms = cfg.main().getLong("awaken.confirm-seconds", 8) * 1000L;
            pending.put(p.getUniqueId(), new Pending(chosen, now + ms));
            cfg.messages().send(p, "awaken.confirm", "element", def.colored());
            return;
        }
        pending.remove(p.getUniqueId());
        switch (elements.awaken(p, chosen)) {
            case OK -> {
                cfg.messages().send(p, "awaken.success", "element", def.colored());
                Fx.sound(p.getLocation(), "entity.player.levelup", 1f, 0.8f);
                Fx.ring(p.getLocation(), 1.5, "END_ROD", 24);
                openMain(p);
            }
            case ALREADY -> cfg.messages().send(p, "awaken.already");
            case DISABLED -> cfg.messages().send(p, "awaken.disabled");
            case NO_DATA -> cfg.messages().send(p, "data-loading");
        }
    }

    private void clickSkills(Player p, int slot, boolean right) {
        if (slot == 22) {
            openMain(p);
            return;
        }
        int idx = -1;
        for (int i = 0; i < GuiHolder.SKILL_SLOTS.length; i++) if (GuiHolder.SKILL_SLOTS[i] == slot) idx = i + 1;
        PlayerData d = elements.get(p);
        if (idx < 0 || d == null || d.getElement() == null) return;
        SkillDef s = cfg.skill(d.getElement(), idx);
        if (s == null) return;
        if (right) {
            if (d.getLevel() < s.unlockLevel()) {
                cfg.messages().send(p, "cast.locked", "unlock", s.unlockLevel());
                return;
            }
            d.setSelectedSlot(idx);
            cfg.messages().send(p, "cast.selected", "slot", idx, "name", s.name());
            openSkills(p);
            return;
        }
        switch (skills.upgrade(p, s.id())) {
            case OK -> {
                cfg.messages().send(p, "upgrade.success", "name", s.name(), "level", skills.levelOf(d, s));
                Fx.sound(p.getLocation(), "entity.experience_orb.pickup", 1f, 1.2f);
            }
            case LOCKED -> cfg.messages().send(p, "upgrade.locked");
            case MAX -> cfg.messages().send(p, "upgrade.max");
            case NO_POINTS -> cfg.messages().send(p, "upgrade.no-points", "cost", skills.upgradeCost(d, s),
                    "points", d.getSkillPoints());
            default -> { }
        }
        openSkills(p);
    }
}
