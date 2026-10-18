package com.astrax.elementer;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Mã hóa cấp kỹ năng thành chuỗi "id=lv;id=lv" để lưu trong một cột. */
public final class SkillCodec {
    private SkillCodec() {}

    public static String serialize(Map<String, Integer> skills) {
        if (skills == null || skills.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : skills.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    /** Đọc "id=lv;id=lv". Bỏ qua mục sai định dạng thay vì ném lỗi. */
    public static Map<String, Integer> parse(String raw) {
        Map<String, Integer> m = new HashMap<>();
        if (raw == null || raw.isBlank()) return m;
        for (String part : raw.split(";")) {
            int i = part.indexOf('=');
            if (i <= 0 || i == part.length() - 1) continue;
            try {
                m.put(part.substring(0, i).trim(), Integer.parseInt(part.substring(i + 1).trim()));
            } catch (NumberFormatException ignored) {
                // mục hỏng
            }
        }
        return m;
    }

    /** Tập hợp tên (ví dụ nguyên tố đã mở khóa) thành "A,B,C". */
    public static String serializeSet(Set<String> names) {
        return names == null || names.isEmpty() ? "" : String.join(",", new TreeSet<>(names));
    }

    public static Set<String> parseSet(String raw) {
        Set<String> out = new HashSet<>();
        if (raw == null || raw.isBlank()) return out;
        for (String p : raw.split(",")) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
