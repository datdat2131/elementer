package com.astrax.elementer;

/** Điểm hồi sinh boss tự động. */
public record BossSpawnPoint(String bossId, String world, double x, double y, double z, int respawnMinutes) {
}
