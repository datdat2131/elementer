package com.astrax.elementer;

import java.util.Locale;

/** Sáu nguyên tố chính. Dữ liệu hiển thị/chỉ số nằm trong elements.yml. */
public enum Element {
    FIRE("hoa"), ICE("bang"), LIGHTNING("loi"), EARTH("tho"), WIND("phong"), LIGHT("quang"), DARK("toi"), DRAGON("rong");

    private final String alias;

    Element(String alias) {
        this.alias = alias;
    }

    /** Tên ngắn tiếng Việt không dấu dùng trong lệnh, ví dụ "hoa". */
    public String alias() {
        return alias;
    }

    /** Nhận cả tên tiếng Anh (FIRE) lẫn tiếng Việt (hoa, bang, loi, tho, phong, quang). Trả về null nếu không hợp lệ. */
    public static Element parse(String input) {
        if (input == null) return null;
        String n = input.trim().toUpperCase(Locale.ROOT);
        switch (n) {
            case "HOA": case "HỎA": return FIRE;
            case "BANG": case "BĂNG": return ICE;
            case "LOI": case "LÔI": return LIGHTNING;
            case "THO": case "THỔ": return EARTH;
            case "PHONG": return WIND;
            case "QUANG": return LIGHT;
            case "TOI": case "TỐI": case "BONG TOI": case "BÓNG TỐI": return DARK;
            case "RONG": case "RỒNG": case "LONG": return DRAGON;
            default:
                try {
                    return valueOf(n);
                } catch (IllegalArgumentException ex) {
                    return null;
                }
        }
    }
}
