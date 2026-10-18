package com.astrax.elementer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WingsGeometryTest {
    @Test
    void wingsAreSymmetricAndBounded() {
        List<WingsGeometry.Point> pts = WingsGeometry.points(1.0, 0.0);
        long left = pts.stream().filter(p -> p.side() < 0).count();
        long right = pts.stream().filter(p -> p.side() > 0).count();
        assertEquals(left, right);
        assertTrue(pts.size() >= 30 && pts.size() <= 90, "số hạt mỗi khung hình hợp lý: " + pts.size());
        assertTrue(pts.stream().anyMatch(WingsGeometry.Point::membrane));
        assertTrue(pts.stream().anyMatch(p -> !p.membrane()));
    }

    @Test
    void flappingLowersWingTips() {
        double open = WingsGeometry.points(1.0, 0.0).stream().mapToDouble(WingsGeometry.Point::up).max().orElse(0);
        double flapped = WingsGeometry.points(1.0, 0.6).stream().mapToDouble(WingsGeometry.Point::up).max().orElse(0);
        assertTrue(flapped < open);
    }

    @Test
    void scaleEnlargesWings() {
        double small = WingsGeometry.points(0.5, 0).stream().mapToDouble(p -> Math.abs(p.side())).max().orElse(0);
        double big = WingsGeometry.points(1.5, 0).stream().mapToDouble(p -> Math.abs(p.side())).max().orElse(0);
        assertTrue(big > small * 2.5);
    }

    @Test
    void wingsSitBehindThePlayer() {
        assertTrue(WingsGeometry.points(1.0, 0.2).stream().allMatch(p -> p.back() > 0));
    }
}
