package com.astrax.elementer;

import java.util.ArrayList;
import java.util.List;

/**
 * Hình học cánh rồng (thuần toán học, không phụ thuộc Bukkit để dễ kiểm thử).
 * Trả về các điểm {ngang, cao, sau} trong hệ tọa độ cục bộ của lưng người chơi:
 * ngang &gt; 0 = bên phải, cao = lên trên, sau = lùi ra sau lưng. Mỗi điểm kèm cờ "màng cánh" (true) hay "xương" (false).
 */
public final class WingsGeometry {
    /** Một điểm hạt của cánh. */
    public record Point(double side, double up, double back, boolean membrane) {}

    // Khung xương (x = ra ngoài, y = lên) của MỘT cánh khi dang rộng, đơn vị block.
    private static final double[][] ARM = {{0.10, 0.00}, {0.45, 0.45}, {0.80, 0.85}, {1.25, 1.10}};
    private static final double[][][] FINGERS = {
            {{1.25, 1.10}, {1.90, 1.05}, {2.35, 0.70}},
            {{1.25, 1.10}, {1.95, 0.55}, {2.15, 0.05}},
            {{1.25, 1.10}, {1.70, 0.20}, {1.65, -0.45}},
    };
    private static final double[] BODY_ANCHOR = {0.10, -0.55};
    private static final double STEP = 0.32;

    private WingsGeometry() {}

    /**
     * @param scale   hệ số kích thước
     * @param flapRad góc vỗ cánh (radian), 0 = dang rộng; dương = cánh hạ xuống
     * @return điểm của cả hai cánh (đã đối xứng)
     */
    public static List<Point> points(double scale, double flapRad) {
        List<Point> out = new ArrayList<>();
        for (int side : new int[]{-1, 1}) {
            polyline(out, ARM, side, scale, flapRad, false);
            for (double[][] f : FINGERS) {
                polyline(out, f, side, scale, flapRad, false);
                // màng: nối đầu ngón về điểm bám thân
                polyline(out, new double[][]{f[f.length - 1], BODY_ANCHOR}, side, scale, flapRad, true);
            }
        }
        return out;
    }

    private static void polyline(List<Point> out, double[][] pts, int side, double scale, double flap, boolean membrane) {
        double cos = Math.cos(flap);
        double sin = Math.sin(flap);
        for (int i = 0; i + 1 < pts.length; i++) {
            double[] a = pts[i];
            double[] b = pts[i + 1];
            double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
            int n = Math.max(1, (int) Math.round(len / STEP));
            int start = membrane ? 1 : 0; // màng: lấy điểm thưa, bỏ điểm đầu (trùng đầu ngón)
            int step = membrane ? 2 : 1;
            for (int k = start; k < n; k += step) {
                double t = (double) k / n;
                double x = (a[0] + (b[0] - a[0]) * t) * scale;
                double y = (a[1] + (b[1] - a[1]) * t) * scale;
                // quay quanh vai trong mặt phẳng ngang-cao: flap > 0 hạ đầu cánh xuống
                double rx = x * cos + y * sin;
                double ry = -x * sin + y * cos;
                out.add(new Point(side * rx, ry, 0.2 + rx * 0.10, membrane)); // cánh hơi cong ra sau
            }
        }
    }
}
