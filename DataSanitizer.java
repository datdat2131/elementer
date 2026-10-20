package com.astrax.elementer;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Kiểm tra và sửa dữ liệu đọc từ DB (nguyên tố lạ, cấp âm, kỹ năng không tồn tại...) trước khi dùng. */
public final class DataSanitizer {
    private DataSanitizer() {}

    /**
     * @param maxSkillLevel trả về cấp tối đa của kỹ năng, hoặc null nếu kỹ năng không tồn tại
     */
    public static PlayerData sanitize(PlayerSnapshot s, String currentName, ExpCurve curve,
                                      Function<String, Integer> maxSkillLevel, Logger log) {
        PlayerData d = new PlayerData(s.uuid(), s.name());
        d.setName(currentName);
        Element el = Element.parse(s.element());
        if (s.element() != null && el == null) {
            log.log(Level.WARNING, "Dữ liệu của {0} có nguyên tố không hợp lệ ''{1}'' - đã đặt lại về chưa thức tỉnh.",
                    new Object[]{s.uuid(), s.element()});
        }
        d.setElement(el);
        Set<Element> owned = EnumSet.noneOf(Element.class);
        if (s.unlocked() != null) {
            for (String n : s.unlocked()) {
                Element u = Element.parse(n);
                if (u == null) log.log(Level.WARNING, "Bỏ qua nguyên tố đã mở khóa không hợp lệ ''{0}'' của {1}.",
                        new Object[]{n, s.uuid()});
                else owned.add(u);
            }
        }
        if (el != null) owned.add(el); // dữ liệu cũ (một nguyên tố) và đảm bảo nguyên tố đang dùng luôn được sở hữu
        for (Element u : owned) d.unlock(u);
        d.setAuraEnabled(s.auraEnabled());
        d.setWingsEnabled(s.wingsEnabled());
        d.setSelectedSlot(s.selectedSlot());
        for (Element u : owned) restoreProgress(d, s, u, el, curve, log);
        d.setDailyExp(s.dailyExp());
        d.setDailyDay(s.dailyDay());
        d.setAwakenedAt(s.awakenedAt());
        if (s.skills() != null) {
            for (Map.Entry<String, Integer> e : s.skills().entrySet()) {
                Integer max = maxSkillLevel.apply(e.getKey());
                if (max == null) {
                    log.log(Level.WARNING, "Bỏ qua kỹ năng không tồn tại ''{0}'' trong dữ liệu của {1}.",
                            new Object[]{e.getKey(), s.uuid()});
                    continue;
                }
                d.setSkillLevel(e.getKey(), (int) DataSanitizerHelper.clamp(e.getValue() == null ? 1 : e.getValue(), 1, max));
            }
        }
        d.clearDirty();
        return d;
    }

    /** Khôi phục tiến trình riêng của một nguyên tố (dữ liệu cũ dùng chung một cấp được gán cho nguyên tố đang dùng). */
    private static void restoreProgress(PlayerData d, PlayerSnapshot s, Element e, Element active, ExpCurve curve, Logger log) {
        PlayerSnapshot.ProgressEntry pe = s.progress() == null ? null : s.progress().get(e.name());
        int level = 1;
        long exp = 0;
        int points = 0;
        if (pe != null) {
            level = pe.level();
            exp = pe.exp();
            points = pe.skillPoints();
        } else if (e == active) {
            level = s.level();
            exp = s.exp();
            points = s.skillPoints();
        }
        int fixed = (int) DataSanitizerHelper.clamp(level, 1, curve.maxLevel());
        if (fixed != level) {
            log.log(Level.WARNING, "Cấp độ không hợp lệ ({0}) của {1} ở nguyên tố {2} - đã sửa thành {3}.",
                    new Object[]{level, s.uuid(), e, fixed});
        }
        long req = curve.required(fixed);
        long fixedExp = Math.max(0, exp);
        if (req == 0) fixedExp = 0;
        else if (fixedExp >= req) fixedExp = req - 1;
        d.setProgress(e, fixed, fixedExp, Math.max(0, points));
    }

    /** Tạo mới dữ liệu trống cho người chơi lần đầu. */
    public static PlayerData fresh(UUID uuid, String name) {
        PlayerData d = new PlayerData(uuid, name);
        d.markDirty();
        return d;
    }
}

final class DataSanitizerHelper {
    private DataSanitizerHelper() {}
    static long clamp(long v, long min, long max) { return Math.max(min, Math.min(max, v)); }
}
