package com.astrax.elementer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Tiện ích dựng item và inventory cho GUI. */
final class GuiItems {
    private final ElementConfig cfg;

    GuiItems(ElementConfig cfg) {
        this.cfg = cfg;
    }

    ItemStack item(Material m, String name, List<String> lore, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Text.c(name));
        if (lore != null && !lore.isEmpty()) meta.lore(Text.lines(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        if (glow) meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    /** Tạo inventory đã lấp đầy kính xám, tiêu đề lấy từ messages.yml. */
    Inventory create(GuiHolder.Page page, int size, String titleKey) {
        GuiHolder h = new GuiHolder(page);
        Inventory inv = Bukkit.createInventory(h, size, cfg.messages().get(titleKey));
        h.inventory = inv;
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", null, false);
        for (int i = 0; i < size; i++) inv.setItem(i, filler);
        return inv;
    }

    /** Như {@link #create} nhưng gắn nguyên tố đang xem vào holder (trang thông tin boss). */
    Inventory create(GuiHolder.Page page, int size, String titleKey, Element element) {
        Inventory inv = create(page, size, titleKey);
        ((GuiHolder) inv.getHolder()).element = element;
        return inv;
    }

    String raw(String key) {
        return cfg.messages().raw(key);
    }

    static List<String> merge(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a);
        out.add(" ");
        out.addAll(b);
        return out;
    }
}
