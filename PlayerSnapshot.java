package com.astrax.elementer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bản chụp bất biến của dữ liệu người chơi để ghi xuống DB ở thread khác.
 * level/exp/skillPoints là của nguyên tố ĐANG DÙNG (cột cũ, dùng cho xếp hạng và nâng cấp dữ liệu cũ);
 * progress chứa tiến trình riêng của từng nguyên tố (khóa = tên nguyên tố).
 */
public record PlayerSnapshot(
        UUID uuid,
        String name,
        String element,
        int level,
        long exp,
        int skillPoints,
        int selectedSlot,
        long dailyExp,
        long dailyDay,
        long awakenedAt,
        Map<String, Integer> skills,
        Set<String> unlocked,
        boolean auraEnabled,
        boolean wingsEnabled,
        Map<String, ProgressEntry> progress) {

    /** Tiến trình của một nguyên tố. */
    public record ProgressEntry(int level, long exp, int skillPoints) {}
}
