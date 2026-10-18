package com.astrax.elementer;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sở hữu nhiều nguyên tố (tối đa unlock.max-owned): mở khóa (nguyên tố đầu miễn phí, các nguyên tố sau tốn cấp +
 * điểm kỹ năng theo unlock.requirements; nguyên tố giới hạn chỉ nhận bằng vé) và chuyển nguyên tố đang dùng (có hồi chiêu chuyển đổi). Cấp độ, EXP và điểm kỹ năng
 * dùng chung cho mọi nguyên tố; cấp từng kỹ năng được lưu riêng theo kỹ năng.
 */
public final class ElementOwnership {
    public enum UnlockResult { OK, NO_DATA, DISABLED, OWNED, LEVEL_TOO_LOW, NOT_ENOUGH_POINTS, MAX_REACHED, VOUCHER_ONLY }

    public enum SwitchResult { OK, NO_DATA, NOT_OWNED, SAME, COOLDOWN }

    private final ElementConfig cfg;
    private final ElementService elements;
    private final CombatService combat;
    private final Map<UUID, Long> switchReadyAt = new HashMap<>();

    public ElementOwnership(ElementConfig cfg, ElementService elements, CombatService combat) {
        this.cfg = cfg;
        this.elements = elements;
        this.combat = combat;
    }

    /** Số nguyên tố tối đa một người chơi được sở hữu. */
    public int maxOwned() {
        return Math.max(1, cfg.main().getInt("unlock.max-owned", 3));
    }

    /** Trao nguyên tố bằng vé: bỏ qua yêu cầu cấp/điểm nhưng vẫn tuân giới hạn số nguyên tố sở hữu. */
    public UnlockResult grant(Player p, Element e) {
        PlayerData d = elements.get(p);
        if (d == null) return UnlockResult.NO_DATA;
        if (cfg.element(e) == null) return UnlockResult.DISABLED;
        if (d.hasUnlocked(e)) return UnlockResult.OWNED;
        if (d.unlockedCount() >= maxOwned()) return UnlockResult.MAX_REACHED;
        boolean first = d.unlockedCount() == 0 || d.getElement() == null;
        d.unlock(e);
        if (first) {
            activate(p, d, e);
            d.setAwakenedAt(System.currentTimeMillis());
        }
        return UnlockResult.OK;
    }

    /** Mở khóa một nguyên tố. Nguyên tố đầu tiên được kích hoạt ngay; các nguyên tố sau cần /elementer switch. */
    public UnlockResult unlock(Player p, Element e) {
        PlayerData d = elements.get(p);
        if (d == null) return UnlockResult.NO_DATA;
        if (cfg.element(e) == null) return UnlockResult.DISABLED;
        if (d.hasUnlocked(e)) return UnlockResult.OWNED;
        if (cfg.element(e).voucherOnly()) return UnlockResult.VOUCHER_ONLY;
        if (d.unlockedCount() >= maxOwned()) return UnlockResult.MAX_REACHED;
        boolean first = d.unlockedCount() == 0;
        if (!first) {
            UnlockReq req = cfg.unlockReq(d.unlockedCount());
            if (d.getLevel() < req.level()) return UnlockResult.LEVEL_TOO_LOW;
            if (d.getSkillPoints() < req.points()) return UnlockResult.NOT_ENOUGH_POINTS;
            d.setSkillPoints(d.getSkillPoints() - req.points());
        }
        d.unlock(e);
        if (first || d.getElement() == null) {
            activate(p, d, e);
            d.setAwakenedAt(System.currentTimeMillis());
        }
        return UnlockResult.OK;
    }

    public SwitchResult switchTo(Player p, Element e) {
        PlayerData d = elements.get(p);
        if (d == null) return SwitchResult.NO_DATA;
        if (!d.hasUnlocked(e) || cfg.element(e) == null) return SwitchResult.NOT_OWNED;
        if (d.getElement() == e) return SwitchResult.SAME;
        long now = System.currentTimeMillis();
        Long ready = switchReadyAt.get(p.getUniqueId());
        if (ready != null && ready > now) return SwitchResult.COOLDOWN;
        activate(p, d, e);
        long cd = (long) (cfg.main().getDouble("switch.cooldown-seconds", 30) * 1000);
        if (cd > 0) switchReadyAt.put(p.getUniqueId(), now + cd);
        return SwitchResult.OK;
    }

    public long switchCooldownLeftMs(Player p) {
        Long ready = switchReadyAt.get(p.getUniqueId());
        return ready == null ? 0 : Math.max(0, ready - System.currentTimeMillis());
    }

    /** Đặt nguyên tố đang dùng: reset ô kỹ năng, bỏ lá chắn, áp dụng lại nội tại. */
    public void activate(Player p, PlayerData d, Element e) {
        d.unlock(e);
        d.setElement(e);
        d.setSelectedSlot(1);
        combat.clearShield(p.getUniqueId());
        elements.applyPassives(p);
    }

    public void purge(long nowMs) { switchReadyAt.values().removeIf(t -> t <= nowMs); }

    public void clear(UUID id) { switchReadyAt.remove(id); }
}
