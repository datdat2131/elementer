package com.astrax.elementer;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Dữ liệu nguyên tố của một người chơi. Chỉ truy cập từ main thread.
 * Mỗi nguyên tố sở hữu có tiến trình RIÊNG (cấp, EXP, điểm kỹ năng) bắt đầu từ cấp 1; các hàm getLevel/getExp/
 * getSkillPoints và setter không tham số luôn tác động lên nguyên tố ĐANG DÙNG.
 */
public final class PlayerData {
    /** Số ô kỹ năng tối đa của một nguyên tố. */
    public static final int MAX_SKILL_SLOTS = 4;

    /** Tiến trình của một nguyên tố. */
    public static final class Progress {
        public int level = 1;
        public long exp;
        public int skillPoints;
    }

    private final UUID uuid;
    private String name;
    private Element element;
    private final Map<Element, Progress> progress = new EnumMap<>(Element.class);
    private int selectedSlot = 1;
    private long dailyExp;
    private long dailyDay;
    private long awakenedAt;
    private final Map<String, Integer> skillLevels = new HashMap<>();
    private final Set<Element> unlocked = EnumSet.noneOf(Element.class);
    private boolean auraEnabled = true;
    private boolean wingsEnabled = true;
    private boolean dirty;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public void setName(String name) {
        if (name != null && !name.equals(this.name)) {
            this.name = name;
            dirty = true;
        }
    }

    /** Nguyên tố đang dùng (null nếu chưa có). */
    public Element getElement() { return element; }
    public void setElement(Element element) { this.element = element; dirty = true; }

    // ---- tiến trình của nguyên tố ĐANG DÙNG (1/0/0 nếu chưa có nguyên tố)
    private Progress active() { return element == null ? null : progress.computeIfAbsent(element, k -> new Progress()); }

    public int getLevel() { return getLevel(element); }
    public void setLevel(int level) {
        Progress p = active();
        if (p != null) {
            p.level = Math.max(1, level);
            dirty = true;
        }
    }

    public long getExp() { return getExp(element); }
    public void setExp(long exp) {
        Progress p = active();
        if (p != null) {
            p.exp = Math.max(0, exp);
            dirty = true;
        }
    }

    public int getSkillPoints() { return getSkillPoints(element); }
    public void setSkillPoints(int points) {
        Progress p = active();
        if (p != null) {
            p.skillPoints = Math.max(0, points);
            dirty = true;
        }
    }
    public void addSkillPoints(int points) { setSkillPoints(getSkillPoints() + points); }

    // ---- tiến trình theo từng nguyên tố
    public int getLevel(Element e) {
        Progress p = e == null ? null : progress.get(e);
        return p == null ? 1 : p.level;
    }

    public long getExp(Element e) {
        Progress p = e == null ? null : progress.get(e);
        return p == null ? 0 : p.exp;
    }

    public int getSkillPoints(Element e) {
        Progress p = e == null ? null : progress.get(e);
        return p == null ? 0 : p.skillPoints;
    }

    /** Đặt trọn tiến trình của một nguyên tố (dùng khi nạp dữ liệu). */
    public void setProgress(Element e, int level, long exp, int skillPoints) {
        if (e == null) return;
        Progress p = progress.computeIfAbsent(e, k -> new Progress());
        p.level = Math.max(1, level);
        p.exp = Math.max(0, exp);
        p.skillPoints = Math.max(0, skillPoints);
        dirty = true;
    }

    public int getSelectedSlot() { return selectedSlot; }
    public void setSelectedSlot(int slot) { this.selectedSlot = Math.min(MAX_SKILL_SLOTS, Math.max(1, slot)); dirty = true; }

    public long getDailyExp() { return dailyExp; }
    public void setDailyExp(long v) { this.dailyExp = Math.max(0, v); dirty = true; }
    public long getDailyDay() { return dailyDay; }
    public void setDailyDay(long v) { this.dailyDay = v; dirty = true; }

    public long getAwakenedAt() { return awakenedAt; }
    public void setAwakenedAt(long v) { this.awakenedAt = v; dirty = true; }

    /** Cấp kỹ năng (mặc định 1 khi đã mở khóa). Kỹ năng thuộc đúng một nguyên tố nên mỗi nguyên tố có cấp kỹ năng riêng. */
    public int skillLevel(String skillId) { return skillLevels.getOrDefault(skillId, 1); }
    public void setSkillLevel(String skillId, int lvl) { skillLevels.put(skillId, Math.max(1, lvl)); dirty = true; }
    public Map<String, Integer> skillLevelsView() { return Map.copyOf(skillLevels); }
    public void clearSkillLevels() { skillLevels.clear(); dirty = true; }

    // ---- nhiều nguyên tố: element = nguyên tố đang dùng, unlocked = các nguyên tố đã sở hữu
    public boolean hasUnlocked(Element e) { return unlocked.contains(e); }
    public int unlockedCount() { return unlocked.size(); }
    public Set<Element> unlockedView() { return EnumSet.copyOf(unlocked.isEmpty() ? EnumSet.noneOf(Element.class) : unlocked); }
    public void unlock(Element e) { if (e != null && unlocked.add(e)) dirty = true; }

    public boolean isAuraEnabled() { return auraEnabled; }
    public void setAuraEnabled(boolean v) { this.auraEnabled = v; dirty = true; }
    public boolean isWingsEnabled() { return wingsEnabled; }
    public void setWingsEnabled(boolean v) { this.wingsEnabled = v; dirty = true; }

    public boolean isDirty() { return dirty; }
    public void markDirty() { dirty = true; }
    public void clearDirty() { dirty = false; }

    /** Xóa toàn bộ tiến trình (dùng cho /elementeradmin reset). */
    public void reset() {
        element = null;
        progress.clear();
        selectedSlot = 1;
        dailyExp = 0;
        dailyDay = 0;
        awakenedAt = 0;
        skillLevels.clear();
        unlocked.clear();
        auraEnabled = true;
        wingsEnabled = true;
        dirty = true;
    }

    public PlayerSnapshot snapshot() {
        Set<String> names = new TreeSet<>();
        for (Element e : unlocked) names.add(e.name());
        Map<String, PlayerSnapshot.ProgressEntry> prog = new TreeMap<>();
        for (Map.Entry<Element, Progress> e : progress.entrySet()) {
            Progress p = e.getValue();
            prog.put(e.getKey().name(), new PlayerSnapshot.ProgressEntry(p.level, p.exp, p.skillPoints));
        }
        return new PlayerSnapshot(uuid, name, element == null ? null : element.name(), getLevel(), getExp(),
                getSkillPoints(), selectedSlot, dailyExp, dailyDay, awakenedAt, Map.copyOf(skillLevels),
                names, auraEnabled, wingsEnabled, prog);
    }
}
