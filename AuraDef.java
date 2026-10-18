package com.astrax.elementer;

/**
 * Hào quang dưới chân.
 * pattern: RING (một vòng), DOUBLE_RING (hai vòng ngược chiều), SPIRAL (vòng + cột xoáy), HELIX (vòng + xoắn kép bay lên),
 * DRAGON (nhiều lớp: vòng ngoài, vòng trong ngược chiều, xoắn kép, tia lửa và bùng lửa dưới chân).
 * accentParticle/sparkParticle: hạt phụ (có thể null). unlockLevel: cấp tối thiểu của người chơi để hiện hào quang.
 */
public record AuraDef(int unlockLevel, String particle, String accentParticle, String sparkParticle,
                      double radius, int points, String pattern) {
}
