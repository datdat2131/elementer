package com.astrax.elementer;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Đánh dấu inventory thuộc GUI của plugin và cho biết đang ở trang nào. */
final class GuiHolder implements InventoryHolder {
    enum Page { MAIN, AWAKEN, SKILLS, STATS, AFFINITY, TOP }

    static final int[] ELEMENT_SLOTS = {10, 11, 12, 14, 15, 16};
    static final int[] SKILL_SLOTS = {11, 13, 15};

    final Page page;
    Inventory inventory;

    GuiHolder(Page page) {
        this.page = page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
