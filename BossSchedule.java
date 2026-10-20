package com.astrax.elementer;

import java.util.List;

/** Logic thuần (không phụ thuộc Bukkit) cho bộ spawn boss định kỳ: chọn boss theo tỉ lệ và tính thời gian chờ. */
public final class BossSchedule {
    /**
     * Một ứng viên spawn. chance &gt; 0: boss huyền thoại, xác suất tuyệt đối mỗi lần spawn (ví dụ 0.01 = 1%).
     * chance = 0: boss thường, chọn theo trọng số weight trong phần xác suất còn lại.
     */
    public record Candidate(String id, double weight, double chance) {}

    private BossSchedule() {}

    /**
     * Chọn boss cho một lần spawn.
     * @param roll1 số ngẫu nhiên [0,1) quyết định có ra huyền thoại không (và con nào)
     * @param roll2 số ngẫu nhiên [0,1) chọn boss thường theo trọng số
     * @return id boss, hoặc null nếu không có ứng viên hợp lệ
     */
    public static String pick(List<Candidate> list, double roll1, double roll2) {
        double legendaryTotal = 0;
        for (Candidate c : list) if (c.chance() > 0) legendaryTotal += c.chance();
        if (roll1 < legendaryTotal) {
            double acc = 0;
            for (Candidate c : list) {
                if (c.chance() <= 0) continue;
                acc += c.chance();
                if (roll1 < acc) return c.id();
            }
        }
        double total = 0;
        for (Candidate c : list) if (c.chance() <= 0 && c.weight() > 0) total += c.weight();
        if (total <= 0) return null;
        double target = Math.min(Math.max(roll2, 0), 0.9999999999) * total;
        double acc = 0;
        for (Candidate c : list) {
            if (c.chance() > 0 || c.weight() <= 0) continue;
            acc += c.weight();
            if (target < acc) return c.id();
        }
        return null;
    }

    /** Thời gian còn lại (ms) đến lần spawn kế; lastSpawn &lt;= 0 nghĩa là chưa bắt đầu đếm. */
    public static long remainingMs(long now, long lastSpawn, long intervalMs) {
        if (lastSpawn <= 0) return Long.MAX_VALUE;
        return Math.max(0, lastSpawn + intervalMs - now);
    }
}
