package com.astrax.elementer;

/**
 * Cánh dựng bằng hạt sau lưng (chỉ để trang trí, không cho bay).
 * flapDegrees: biên độ vỗ cánh; unlockLevel: cấp tối thiểu của người chơi để hiện cánh.
 */
public record WingsDef(int unlockLevel, String boneParticle, String membraneParticle, double scale, double flapDegrees) {
}
