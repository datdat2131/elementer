package com.astrax.elementer;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

/**
 * Tinh Thể nguyên tố: nguyên liệu rơi từ boss. Đổi đủ số tinh thể (bosses.yml → exchange) để lấy một Vé Nguyên Tố
 * của đúng nguyên tố đó. Không đổi nếu đã sở hữu nguyên tố hoặc đã đủ số nguyên tố tối đa (vé sẽ không dùng được).
 */
public final class CrystalService {
    public enum ExchangeResult { OK, NO_DATA, NOT_EXCHANGEABLE, OWNED, MAX_REACHED, NOT_ENOUGH }

    private final ElementConfig cfg;
    private final BossConfig bossCfg;
    private final ElementService elements;
    private final VoucherService vouchers;
    private final NamespacedKey key;

    public CrystalService(ElementerCore plugin, ElementConfig cfg, BossConfig bossCfg, ElementService elements,
                          VoucherService vouchers) {
        this.cfg = cfg;
        this.bossCfg = bossCfg;
        this.elements = elements;
        this.vouchers = vouchers;
        this.key = new NamespacedKey(plugin, "crystal_element");
    }

    public ItemStack create(Element e, int amount) {
        ElementDef def = cfg.element(e);
        String colored = def == null ? e.name() : def.colored();
        ItemStack it = new ItemStack(bossCfg.crystalMaterial(e), Math.max(1, Math.min(64, amount)));
        ItemMeta m = it.getItemMeta();
        m.displayName(cfg.messages().get("crystal.item-name", "element", colored));
        m.lore(Text.lines(cfg.messages().formatted("crystal.item-lore", "element", colored)));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(key, PersistentDataType.STRING, e.name());
        it.setItemMeta(m);
        return it;
    }

    public Element read(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String v = it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return v == null ? null : Element.parse(v);
    }

    public int count(Player p, Element e) {
        int total = 0;
        for (ItemStack it : p.getInventory().getContents()) if (read(it) == e) total += it.getAmount();
        return total;
    }

    /** Lấy đúng {@code amount} tinh thể khỏi túi đồ; không lấy gì nếu không đủ. */
    public boolean remove(Player p, Element e, int amount) {
        if (count(p, e) < amount) return false;
        int left = amount;
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < c.length && left > 0; i++) {
            if (read(c[i]) != e) continue;
            int take = Math.min(left, c[i].getAmount());
            if (take == c[i].getAmount()) p.getInventory().setItem(i, null);
            else c[i].setAmount(c[i].getAmount() - take);
            left -= take;
        }
        return true;
    }

    /** Đưa tinh thể vào túi đồ (rơi xuống chân nếu đầy). */
    public void give(Player p, Element e, int amount) {
        int left = Math.max(0, amount);
        while (left > 0) {
            int n = Math.min(64, left);
            Map<Integer, ItemStack> over = p.getInventory().addItem(create(e, n));
            for (ItemStack rest : over.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
            left -= n;
        }
    }

    public ExchangeResult exchange(Player p, Element e) {
        PlayerData d = elements.get(p);
        if (d == null) return ExchangeResult.NO_DATA;
        Integer cost = bossCfg.exchangeCost(e);
        if (cost == null) return ExchangeResult.NOT_EXCHANGEABLE;
        if (d.hasUnlocked(e)) return ExchangeResult.OWNED;
        if (d.unlockedCount() >= elements.ownership().maxOwned()) return ExchangeResult.MAX_REACHED;
        if (count(p, e) < cost || !remove(p, e, cost)) return ExchangeResult.NOT_ENOUGH;
        vouchers.give(p, e, 1);
        return ExchangeResult.OK;
    }

    /** Đổi vé và thông báo cho người chơi. @return true nếu đổi thành công */
    public boolean exchangeAndNotify(Player p, Element e) {
        Messages m = cfg.messages();
        ElementDef def = cfg.element(e);
        String colored = def == null ? e.name() : def.colored();
        Integer cost = bossCfg.exchangeCost(e);
        switch (exchange(p, e)) {
            case OK -> {
                m.send(p, "exchange.success", "element", colored, "cost", cost);
                Fx.sound(p.getLocation(), "entity.player.levelup", 1f, 1.3f);
                return true;
            }
            case NOT_ENOUGH -> m.send(p, "exchange.not-enough", "element", colored, "have", count(p, e), "cost", cost);
            case NOT_EXCHANGEABLE -> m.send(p, "exchange.not-exchangeable", "element", colored);
            case OWNED -> m.send(p, "exchange.owned");
            case MAX_REACHED -> m.send(p, "exchange.max", "max", elements.ownership().maxOwned());
            case NO_DATA -> m.send(p, "data-loading");
        }
        return false;
    }
}
