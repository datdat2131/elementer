package com.astrax.elementer;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Trạng thái một boss đang sống: sát thương từng người, hồi chiêu kỹ năng, thanh máu, trạng thái nổi điên. */
final class ActiveBoss {
    final BossDef def;
    final LivingEntity entity;
    final BossBar bar;
    final int spawnIndex; // -1 nếu do quản trị triệu hồi
    final boolean timed;  // boss do bộ spawn định kỳ tạo (có thể tự biến mất, giữ chunk)
    final long spawnedAt = System.currentTimeMillis();
    boolean chunkTicket;
    int chunkX;
    int chunkZ;
    final Map<UUID, Double> damage = new HashMap<>();
    final Map<String, Long> readyAt = new HashMap<>();
    final Set<UUID> viewers = new HashSet<>();
    long nextCastAt;
    boolean enraged;
    boolean saidHalf;
    long nextCastLineAt;

    ActiveBoss(BossDef def, LivingEntity entity, BossBar bar, int spawnIndex, boolean timed) {
        this.def = def;
        this.entity = entity;
        this.bar = bar;
        this.spawnIndex = spawnIndex;
        this.timed = timed;
    }

    void addDamage(UUID player, double amount) {
        if (amount > 0) damage.merge(player, amount, Double::sum);
    }

    double totalDamage() {
        double t = 0;
        for (double d : damage.values()) t += d;
        return t;
    }
}
