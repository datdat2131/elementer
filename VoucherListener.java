package com.astrax.elementer;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/** Chuột phải với vé nguyên tố trên tay để sở hữu nguyên tố. */
public final class VoucherListener implements Listener {
    private final VoucherService vouchers;
    private final ElementConfig cfg;

    public VoucherListener(VoucherService vouchers, ElementConfig cfg) {
        this.vouchers = vouchers;
        this.cfg = cfg;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (!e.getAction().isRightClick()) return;
        ItemStack item = e.getItem();
        if (vouchers.read(item) == null) return;
        e.setCancelled(true); // không dùng vé như vật phẩm thường
        Player p = e.getPlayer();
        if (!p.hasPermission("elementer.voucher.use")) {
            cfg.messages().send(p, "no-permission");
            return;
        }
        vouchers.redeem(p, item);
    }
}
