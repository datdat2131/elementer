package com.astrax.elementer;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Đánh dấu inventory thuộc GUI của plugin và cho biết đang ở trang nào. */
final class GuiHolder implements InventoryHolder {
    enum Page { MAIN, AWAKEN, SKILLS, STATS, AFFINITY, TOP }

    /** 8 nguyên tố: hai hàng bốn ô (trang 36 ô, nút quay lại ở ô 31). */
    static final int[] ELEMENT_SLOTS = {10, 12, 14, 16, 19, 21, 23, 25};
    static final int ELEMENT_BACK_SLOT = 31;

    /** Vị trí các ô kỹ năng: 4 kỹ năng trải đều, 3 kỹ năng căn giữa. */
    static int[] skillSlots(int count) {
        return count >= 4 ? new int[]{10, 12, 14, 16} : new int[]{11, 13, 15};
    }

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
