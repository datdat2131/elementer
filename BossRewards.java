package com.astrax.elementer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Chia thưởng khi boss chết: chỉ người gây đủ tỉ lệ sát thương (và đang online, cùng thế giới) mới nhận tinh thể + EXP. */
final class BossRewards {
    private final ElementConfig cfg;
    private final BossConfig bossCfg;
    private final ElementService elements;
    private final CrystalService crystals;

    BossRewards(ElementConfig cfg, BossConfig bossCfg, ElementService elements, CrystalService crystals) {
        this.cfg = cfg;
        this.bossCfg = bossCfg;
        this.elements = elements;
        this.crystals = crystals;
    }

    void give(ActiveBoss b) {
        double total = b.totalDamage();
        if (total <= 0) return; // chết do môi trường: không ai được thưởng
        double minShare = bossCfg.settings().getDouble("settings.min-damage-share", 0.05);
        World w = b.entity.getWorld();
        ElementDef def = cfg.element(b.def.element());
        String element = def == null ? b.def.element().name() : def.colored();
        Messages m = cfg.messages();

        UUID top = null;
        double topDmg = 0;
        for (Map.Entry<UUID, Double> e : b.damage.entrySet()) {
            if (e.getValue() > topDmg) {
                topDmg = e.getValue();
                top = e.getKey();
            }
        }
        Player topPlayer = top == null ? null : Bukkit.getPlayer(top);
        String topName = topPlayer == null ? "?" : topPlayer.getName();
        for (Player p : w.getPlayers()) {
            p.sendMessage(m.prefixed("boss.killed", "boss", b.def.name(), "player", topName));
        }
        Fx.particle(b.entity.getLocation().add(0, 1, 0), "TOTEM_OF_UNDYING", 80, 1.0, 1.2, 1.0, 0.4);

        for (Map.Entry<UUID, Double> e : b.damage.entrySet()) {
            if (e.getValue() / total < minShare) continue;
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !p.getWorld().equals(w)) continue;
            int n = b.def.crystalsMin() + ThreadLocalRandom.current().nextInt(b.def.crystalsMax() - b.def.crystalsMin() + 1);
            if (n > 0) crystals.give(p, b.def.element(), n);
            long exp = elements.addExp(p, b.def.exp(), true);
            m.send(p, "boss.reward", "boss", b.def.name(), "crystals", n, "element", element, "exp", exp);
            giveDrops(p, b, m);
        }
    }

    /** Vật phẩm rớt thêm: tung xác suất riêng cho từng người, đưa thẳng vào túi đồ (đầy thì rơi xuống chân). */
    private void giveDrops(Player p, ActiveBoss b, Messages m) {
        for (BossDrop d : b.def.drops()) {
            if (ThreadLocalRandom.current().nextDouble() >= d.chance()) continue;
            int n = d.min() + ThreadLocalRandom.current().nextInt(d.max() - d.min() + 1);
            for (ItemStack rest : p.getInventory().addItem(new ItemStack(d.material(), n)).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), rest);
            }
            m.send(p, "boss.reward-item", "amount", n, "item", itemName(d));
        }
    }

    static String itemName(BossDrop d) {
        if (d.name() != null && !d.name().isBlank()) return d.name();
        return prettify(d.material());
    }

    /** BLAZE_ROD -> "Blaze Rod". */
    static String prettify(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String part : m.name().toLowerCase(java.util.Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
