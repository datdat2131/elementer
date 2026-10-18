package com.astrax.elementer;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/** Vẽ cánh rồng bằng hạt sau lưng người chơi; vỗ cánh nhanh và rộng hơn khi đang ở trên không. */
final class WingsRenderer {
    private WingsRenderer() {}

    static void draw(Player p, WingsDef w, long tick) {
        Location loc = p.getLocation();
        double yaw = Math.toRadians(loc.getYaw());
        Vector right = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vector back = new Vector(Math.sin(yaw), 0, -Math.cos(yaw));
        boolean airborne = !p.isOnGround();
        double amp = Math.toRadians(w.flapDegrees()) * (airborne ? 1.6 : 1.0);
        double flap = amp * (0.5 + 0.5 * Math.sin(tick * (airborne ? 1.6 : 0.9))) + 0.05;
        Location base = loc.clone().add(0, 1.25, 0);
        List<WingsGeometry.Point> pts = WingsGeometry.points(w.scale(), flap);
        for (WingsGeometry.Point pt : pts) {
            Location at = base.clone()
                    .add(right.clone().multiply(pt.side()))
                    .add(back.clone().multiply(pt.back()))
                    .add(0, pt.up(), 0);
            Fx.particle(at, pt.membrane() ? w.membraneParticle() : w.boneParticle(), 1, 0, 0, 0, 0);
        }
    }
}
