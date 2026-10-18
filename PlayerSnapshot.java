package com.astrax.elementer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bản chụp bất biến của dữ liệu người chơi để ghi xuống DB ở thread khác. */
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
        boolean wingsEnabled) {
}
