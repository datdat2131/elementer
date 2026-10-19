package com.astrax.elementer;

import org.bukkit.Material;

/** Vật phẩm rớt thêm từ boss (ngoài Tinh Thể): số lượng min-max (tối đa 64) và xác suất 0-1 cho mỗi người nhận thưởng. */
public record BossDrop(Material material, int min, int max, double chance, String name) {
}
