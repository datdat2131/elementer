package com.astrax.elementer;

import org.bukkit.entity.Player;

import java.util.Locale;

/** Thực hiện mở khóa/chuyển nguyên tố và gửi thông báo cho người chơi (dùng chung cho lệnh và GUI). */
public final class OwnershipActions {
    private final ElementConfig cfg;
    private final ElementService elements;

    public OwnershipActions(ElementConfig cfg, ElementService elements) {
        this.cfg = cfg;
        this.elements = elements;
    }

    private String colored(Element e) {
        ElementDef def = cfg.element(e);
        return def == null ? e.name() : def.colored();
    }

    /** @return true nếu mở khóa thành công */
    public boolean unlock(Player p, Element e) {
        PlayerData d = elements.get(p);
        boolean first = d != null && d.unlockedCount() == 0;
        UnlockReq req = d == null ? null : cfg.unlockReq(d.unlockedCount());
        int pointsBefore = d == null ? 0 : d.getSkillPoints();
        Messages m = cfg.messages();
        switch (elements.ownership().unlock(p, e)) {
            case OK -> {
                m.send(p, first ? "awaken.success" : "unlock.success", "element", colored(e), "key", e.alias());
                Fx.sound(p.getLocation(), "entity.player.levelup", 1f, 0.8f);
                Fx.ring(p.getLocation(), 1.5, "END_ROD", 24);
                return true;
            }
            case LEVEL_TOO_LOW -> m.send(p, "unlock.level-too-low", "level", req.level());
            case NOT_ENOUGH_POINTS -> m.send(p, "unlock.not-enough-points", "points", req.points(), "have", pointsBefore);
            case MAX_REACHED -> m.send(p, "unlock.max-reached", "max", elements.ownership().maxOwned());
            case VOUCHER_ONLY -> m.send(p, "unlock.voucher-only", "element", colored(e));
            case OWNED -> m.send(p, "unlock.owned");
            case DISABLED -> m.send(p, "awaken.disabled");
            case NO_DATA -> m.send(p, "data-loading");
        }
        return false;
    }

    /** @return true nếu chuyển thành công */
    public boolean switchTo(Player p, Element e) {
        Messages m = cfg.messages();
        switch (elements.ownership().switchTo(p, e)) {
            case OK -> {
                m.send(p, "switch.success", "element", colored(e));
                Fx.sound(p.getLocation(), "entity.enderman.teleport", 0.7f, 1.4f);
                Fx.ring(p.getLocation(), 1.2, "END_ROD", 20);
                return true;
            }
            case NOT_OWNED -> m.send(p, "switch.not-owned");
            case SAME -> m.send(p, "switch.same");
            case COOLDOWN -> m.send(p, "switch.cooldown", "seconds",
                    String.format(Locale.ROOT, "%.0f", Math.ceil(elements.ownership().switchCooldownLeftMs(p) / 1000.0)));
            case NO_DATA -> m.send(p, "data-loading");
        }
        return false;
    }
}
