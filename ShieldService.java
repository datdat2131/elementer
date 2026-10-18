package com.astrax.elementer;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Lá chắn hấp thụ sát thương có thời hạn và trần giá trị. Chỉ dùng trên main thread. */
public final class ShieldService {
    private record Shield(double amount, long expiresAt) {}

    private final ElementConfig cfg;
    private final Map<UUID, Shield> shields = new HashMap<>();

    public ShieldService(ElementConfig cfg) {
        this.cfg = cfg;
    }

    public void add(Player p, double amount, double seconds) {
        double maxAmount = cfg.main().getDouble("combat.max-shield", 40.0);
        double maxSec = cfg.main().getDouble("combat.max-shield-seconds", 15.0);
        double a = Math.min(maxAmount, Math.max(0, amount));
        double sec = Math.min(maxSec, Math.max(0, seconds));
        if (a <= 0 || sec <= 0) return;
        shields.put(p.getUniqueId(), new Shield(a, System.currentTimeMillis() + (long) (sec * 1000)));
    }

    public double remaining(UUID id) {
        Shield s = shields.get(id);
        if (s == null) return 0;
        if (s.expiresAt() <= System.currentTimeMillis()) {
            shields.remove(id);
            return 0;
        }
        return s.amount();
    }

    /** Hấp thụ sát thương bằng lá chắn. @return sát thương còn lại sau khi hấp thụ */
    public double absorb(Player p, double damage) {
        UUID id = p.getUniqueId();
        double have = remaining(id);
        if (have <= 0 || damage <= 0) return damage;
        double absorbed = Math.min(have, damage);
        double left = have - absorbed;
        if (left <= 0.01) shields.remove(id);
        else shields.put(id, new Shield(left, shields.get(id).expiresAt()));
        return damage - absorbed;
    }

    public void clear(UUID id) { shields.remove(id); }

    public void purge(long nowMs) { shields.values().removeIf(s -> s.expiresAt() <= nowMs); }
}
