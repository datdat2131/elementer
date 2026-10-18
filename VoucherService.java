package com.astrax.elementer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Vé nguyên tố (tờ giấy): chuột phải để sở hữu nguyên tố. Mỗi vé mang một mã duy nhất; mã được ghi nhận MỘT LẦN
 * trong DB (toàn cục) trước khi trao, nên vé bị nhân bản vẫn chỉ dùng được một lần. Vé không bị trừ nếu không trao được.
 */
public final class VoucherService {
    /** Nội dung đọc từ một vật phẩm vé. */
    public record Voucher(Element element, String id) {}

    private final ElementerCore plugin;
    private final ElementConfig cfg;
    private final ElementService elements;
    private final ElementStorage storage;
    private final NamespacedKey keyElement;
    private final NamespacedKey keyId;
    private final Set<UUID> redeeming = new HashSet<>();

    public VoucherService(ElementerCore plugin, ElementConfig cfg, ElementService elements, ElementStorage storage) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.elements = elements;
        this.storage = storage;
        this.keyElement = new NamespacedKey(plugin, "voucher_element");
        this.keyId = new NamespacedKey(plugin, "voucher_id");
    }

    // ------------------------------------------------------------------ Tạo / đọc vé

    public ItemStack create(Element e) {
        ElementDef def = cfg.element(e);
        String colored = def == null ? e.name() : def.colored();
        Material mat = Material.matchMaterial(cfg.main().getString("voucher.material", "PAPER"));
        ItemStack it = new ItemStack(mat == null ? Material.PAPER : mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(cfg.messages().get("voucher.item-name", "element", colored));
        m.lore(Text.lines(cfg.messages().formatted("voucher.item-lore", "element", colored)));
        if (cfg.main().getBoolean("voucher.glow", true)) m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(keyElement, PersistentDataType.STRING, e.name());
        m.getPersistentDataContainer().set(keyId, PersistentDataType.STRING, UUID.randomUUID().toString());
        it.setItemMeta(m);
        return it;
    }

    public Voucher read(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        var pdc = it.getItemMeta().getPersistentDataContainer();
        String el = pdc.get(keyElement, PersistentDataType.STRING);
        String id = pdc.get(keyId, PersistentDataType.STRING);
        Element e = el == null ? null : Element.parse(el);
        return e == null || id == null || id.isBlank() ? null : new Voucher(e, id);
    }

    /** Đưa vé vào túi đồ (rơi xuống đất nếu đầy). @return số vé đã trao */
    public int give(Player p, Element e, int amount) {
        int n = Math.max(1, Math.min(10, amount));
        for (int i = 0; i < n; i++) {
            Map<Integer, ItemStack> left = p.getInventory().addItem(create(e));
            for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
        return n;
    }

    // ------------------------------------------------------------------ Dùng vé

    public void redeem(Player p, ItemStack item) {
        Voucher v = read(item);
        if (v == null) return;
        Messages m = cfg.messages();
        PlayerData d = elements.get(p);
        if (d == null) {
            m.send(p, "data-loading");
            return;
        }
        ElementDef def = cfg.element(v.element());
        if (def == null) {
            m.send(p, "voucher.disabled");
            return;
        }
        if (d.hasUnlocked(v.element())) {
            m.send(p, "voucher.owned");
            return;
        }
        int max = elements.ownership().maxOwned();
        if (d.unlockedCount() >= max) {
            m.send(p, "voucher.max", "max", max);
            return;
        }
        UUID id = p.getUniqueId();
        if (!redeeming.add(id)) return; // đang xử lý một vé khác của người này
        storage.claimVoucher(v.id(), id).whenComplete((first, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            redeeming.remove(id);
            if (err != null) {
                plugin.getLogger().log(Level.SEVERE, "Không kiểm tra được vé " + v.id(), err);
                Player online = Bukkit.getPlayer(id);
                if (online != null) m.send(online, "voucher.error");
                return;
            }
            Player online = Bukkit.getPlayer(id);
            if (!first) { // vé đã được dùng ở nơi khác (bản sao): xóa khỏi túi
                if (online != null) {
                    int slot = findSlot(online, v.id());
                    if (slot >= 0) online.getInventory().setItem(slot, null);
                    m.send(online, "voucher.invalid");
                }
                return;
            }
            finish(online, v);
        }));
    }

    /** Chạy trên main thread sau khi DB đã ghi nhận vé. Nếu không trao được thì hoàn tác ghi nhận, vé còn nguyên. */
    private void finish(Player online, Voucher v) {
        Messages m = cfg.messages();
        int slot = online == null ? -1 : findSlot(online, v.id());
        if (online == null || slot < 0) {
            storage.unclaimVoucher(v.id());
            return;
        }
        ElementOwnership.UnlockResult r = elements.ownership().grant(online, v.element());
        if (r != ElementOwnership.UnlockResult.OK) {
            storage.unclaimVoucher(v.id());
            m.send(online, r == ElementOwnership.UnlockResult.MAX_REACHED ? "voucher.max" : "voucher.owned",
                    "max", elements.ownership().maxOwned());
            return;
        }
        PlayerInventory inv = online.getInventory();
        ItemStack stack = inv.getItem(slot);
        if (stack != null && stack.getAmount() > 1) stack.setAmount(stack.getAmount() - 1);
        else inv.setItem(slot, null);
        ElementDef def = cfg.element(v.element());
        m.send(online, "voucher.redeemed", "element", def == null ? v.element().name() : def.colored(),
                "key", v.element().alias());
        Fx.sound(online.getLocation(), "ui.toast.challenge_complete", 1f, 1f);
        Fx.ring(online.getLocation(), 1.6, "END_ROD", 28);
        Fx.particle(online.getLocation().add(0, 1, 0), "TOTEM_OF_UNDYING", 40, 0.5, 0.8, 0.5, 0.3);
    }

    private int findSlot(Player p, String voucherId) {
        ItemStack[] c = p.getInventory().getContents();
        for (int i = 0; i < c.length; i++) {
            Voucher v = read(c[i]);
            if (v != null && v.id().equals(voucherId)) return i;
        }
        return -1;
    }
}
