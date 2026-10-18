package com.astrax.elementer;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Dữ liệu nguyên tố của một người chơi. Chỉ truy cập từ main thread. */
public final class PlayerData {
    /** Số ô kỹ năng tối đa của một nguyên tố. */
    public static final int MAX_SKILL_SLOTS = 4;

    private final UUID uuid;
    private String name;
    private Element element;
    private int level = 1;
    private long exp;
    private int skillPoints;
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

    public Element getElement() { return element; }
    public void setElement(Element element) { this.element = element; dirty = true; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = Math.max(1, level); dirty = true; }

    public long getExp() { return exp; }
    public void setExp(long exp) { this.exp = Math.max(0, exp); dirty = true; }

    public int getSkillPoints() { return skillPoints; }
    public void setSkillPoints(int p) { this.skillPoints = Math.max(0, p); dirty = true; }
    public void addSkillPoints(int p) { setSkillPoints(this.skillPoints + p); }

    public int getSelectedSlot() { return selectedSlot; }
    public void setSelectedSlot(int slot) { this.selectedSlot = Math.min(MAX_SKILL_SLOTS, Math.max(1, slot)); dirty = true; }

    public long getDailyExp() { return dailyExp; }
    public void setDailyExp(long v) { this.dailyExp = Math.max(0, v); dirty = true; }
    public long getDailyDay() { return dailyDay; }
    public void setDailyDay(long v) { this.dailyDay = v; dirty = true; }

    public long getAwakenedAt() { return awakenedAt; }
    public void setAwakenedAt(long v) { this.awakenedAt = v; dirty = true; }

    /** Cấp kỹ năng (mặc định 1 khi đã mở khóa). */
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
        level = 1;
        exp = 0;
        skillPoints = 0;
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
        return new PlayerSnapshot(uuid, name, element == null ? null : element.name(), level, exp,
                skillPoints, selectedSlot, dailyExp, dailyDay, awakenedAt, Map.copyOf(skillLevels),
                names, auraEnabled, wingsEnabled);
    }
}
