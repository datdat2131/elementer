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
    private final OwnershipActions actions;
    private final AuraService aura;
    private final CrystalService crystals;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public ElementGUI(ElementerCore plugin, ElementConfig cfg, ElementService elements, SkillService skills,
                      OwnershipActions actions, AuraService aura, CrystalService crystals, BossConfig bossCfg) {
        this.cfg = cfg;
        this.elements = elements;
        this.skills = skills;
        this.actions = actions;
        this.aura = aura;
        this.crystals = crystals;
        GuiItems ui = new GuiItems(cfg);
        this.info = new InfoPages(plugin, cfg, elements, ui, crystals, bossCfg);
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
            case STATS -> {
                if (slot == 22) openMain(p);
            }
            case AFFINITY -> {
                if (slot == GuiHolder.ELEMENT_BACK_SLOT) openMain(p);
            }
            case TOP -> {
                if (slot == 31) openMain(p);
            }
            case EXCHANGE -> clickExchange(p, slot);
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
            case 18 -> openExchange(p);
            case 20 -> openElements(p);
            case 26 -> {
                aura.setWings(p, null);
                openMain(p);
            }
            case 24 -> {
                aura.setEnabled(p, null);
                openMain(p);
            }
            case 22 -> p.closeInventory();
            default -> { }
        }
    }

    private void openElements(Player p) {
        PlayerData d = elements.get(p);
        if (d == null) cfg.messages().send(p, "data-loading");
        else info.awaken(p, d);
    }

    private void openExchange(Player p) {
        PlayerData d = elements.get(p);
        if (d == null) cfg.messages().send(p, "data-loading");
        else info.exchange(p, d);
    }

    private void clickExchange(Player p, int slot) {
        if (slot == GuiHolder.ELEMENT_BACK_SLOT) {
            openMain(p);
            return;
        }
        Element[] all = Element.values();
        for (int i = 0; i < GuiHolder.ELEMENT_SLOTS.length; i++) {
            if (GuiHolder.ELEMENT_SLOTS[i] == slot && cfg.element(all[i]) != null) {
                crystals.exchangeAndNotify(p, all[i]);
                openExchange(p);
                return;
            }
        }
    }

    private void clickAwaken(Player p, int slot) {
        PlayerData d = elements.get(p);
        if (d == null) return;
        if (slot == GuiHolder.ELEMENT_BACK_SLOT) {
            if (d.getElement() == null) p.closeInventory();
            else openMain(p);
            return;
        }
        Element chosen = null;
        Element[] all = Element.values();
        for (int i = 0; i < GuiHolder.ELEMENT_SLOTS.length; i++) if (GuiHolder.ELEMENT_SLOTS[i] == slot) chosen = all[i];
        ElementDef def = chosen == null ? null : cfg.element(chosen);
        if (def == null) return;

        if (d.hasUnlocked(chosen)) { // đã sở hữu: chuyển sang dùng
            if (actions.switchTo(p, chosen)) openElements(p);
            return;
        }
        // chưa sở hữu: bấm lần đầu để xem điều kiện/xác nhận, lần hai để mở khóa
        long now = System.currentTimeMillis();
        pending.values().removeIf(x -> x.expiresAt() < now);
        Pending pd = pending.get(p.getUniqueId());
        if (pd == null || pd.element() != chosen) {
            long ms = cfg.main().getLong("awaken.confirm-seconds", 8) * 1000L;
            pending.put(p.getUniqueId(), new Pending(chosen, now + ms));
            if (d.unlockedCount() == 0) {
                cfg.messages().send(p, "awaken.confirm", "element", def.colored());
            } else {
                UnlockReq r = cfg.unlockReq(d.unlockedCount());
                cfg.messages().send(p, "unlock.confirm", "element", def.colored(), "level", r.level(), "points", r.points());
            }
            return;
        }
        pending.remove(p.getUniqueId());
        if (actions.unlock(p, chosen)) openElements(p);
    }

    private void clickSkills(Player p, int slot, boolean right) {
        if (slot == 22) {
            openMain(p);
            return;
        }
        PlayerData d = elements.get(p);
        if (d == null || d.getElement() == null) return;
        int[] slots = GuiHolder.skillSlots(cfg.skillsOf(d.getElement()).size());
        int idx = -1;
        for (int i = 0; i < slots.length; i++) if (slots[i] == slot) idx = i + 1;
        if (idx < 0) return;
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
