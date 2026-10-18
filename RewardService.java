package com.astrax.elementer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.logging.Level;

/** Trao phần thưởng mốc cấp đúng một lần: DB ghi nhận trước, chỉ trao khi ghi nhận thành công. */
public final class RewardService {
    private final ElementerCore plugin;
    private final ElementConfig cfg;
    private final ElementStorage storage;

    public RewardService(ElementerCore plugin, ElementConfig cfg, ElementStorage storage) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.storage = storage;
    }

    public void claimLevelReward(Player p, LevelReward r) {
        UUID id = p.getUniqueId();
        String key = "level:" + r.level();
        storage.claimReward(id, key).whenComplete((first, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err != null) {
                plugin.getLogger().log(Level.SEVERE, "Không kiểm tra được phần thưởng " + key + " của " + p.getName(), err);
                return;
            }
            if (!first) return; // đã nhận trước đó
            Player online = Bukkit.getPlayer(id);
            if (online == null) {
                storage.unclaimReward(id, key); // chưa trao được, hoàn tác để lần sau còn nhận
                return;
            }
            for (String cmd : r.commands()) {
                String line = cmd.replace("%player%", online.getName()).replace("%level%", String.valueOf(r.level()));
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
            }
            if (!r.message().isBlank()) online.sendMessage(cfg.messages().prefixed("reward-given", "level", r.level()));
        }));
    }
}
