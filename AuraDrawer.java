package com.astrax.elementer;

import org.bukkit.Location;

/** Vẽ các kiểu hào quang bằng hạt quanh chân người chơi. */
final class AuraDrawer {
    private AuraDrawer() {}

    static void draw(Location base, AuraDef a, long t) {
        double phase = t * 0.35;
        String accent = a.accentParticle() == null ? a.particle() : a.accentParticle();
        String spark = a.sparkParticle() == null ? accent : a.sparkParticle();
        switch (a.pattern()) {
            case "DOUBLE_RING" -> {
                ring(base, a.radius(), a.points(), phase, a.particle());
                ring(base, a.radius() * 0.55, Math.max(4, a.points() / 2), -phase * 1.3, accent);
            }
            case "SPIRAL" -> {
                ring(base, a.radius(), a.points(), phase, a.particle());
                column(base, a.radius() * 0.6, 3, phase * 1.5, t * 0.08, 1.4, accent);
            }
            case "HELIX" -> {
                ring(base, a.radius(), a.points(), phase, a.particle());
                column(base, a.radius() * 0.7, 2, phase * 1.3, t * 0.06, 2.0, spark);
            }
            case "DRAGON" -> {
                ring(base, a.radius(), a.points(), phase, a.particle());
                ring(base, a.radius() * 0.55, Math.max(4, a.points() / 2), -phase * 1.3, accent);
                column(base, a.radius() * 0.75, 2, phase * 1.3, t * 0.06, 2.2, spark);
                if (t % 4 == 0) { // bùng lửa dưới chân
                    Fx.particle(base.clone().add(0, 0.1, 0), a.particle(), 8, a.radius() * 0.5, 0.05, a.radius() * 0.5, 0.03);
                }
            }
            default -> ring(base, a.radius(), a.points(), phase, a.particle());
        }
    }

    private static void ring(Location base, double radius, int points, double phase, String particle) {
        for (int k = 0; k < points; k++) {
            double ang = 2 * Math.PI * k / points + phase;
            Fx.particle(base.clone().add(Math.cos(ang) * radius, 0.08, Math.sin(ang) * radius), particle, 1, 0, 0, 0, 0);
        }
    }

    /** {@code strands} sợi hạt xoáy quanh người và trôi dần lên (lặp lại sau mỗi {@code height} block). */
    private static void column(Location base, double radius, int strands, double phase, double rise, double height, String particle) {
        for (int i = 0; i < strands; i++) {
            double ang = phase + i * (2 * Math.PI / strands);
            double h = ((rise + (double) i / strands) % 1.0) * height;
            Fx.particle(base.clone().add(Math.cos(ang) * radius, h, Math.sin(ang) * radius), particle, 1, 0, 0, 0, 0);
        }
    }
}
