package org.openstreetmap.josm.plugins.roadrailalignment.geometry;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.List;

import org.openstreetmap.josm.data.coor.EastNorth;

public final class GeometryUtil {
    public static final int MAX_SAMPLE_POINTS = 10000;

    private GeometryUtil() {
    }

    public static void appendWithoutDuplicate(List<EastNorth> target, List<EastNorth> source) {
        for (EastNorth point : source) {
            if (target.isEmpty() || target.get(target.size() - 1).distance(point) > 1e-6) {
                target.add(point);
            }
        }
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static Vector2D orientToward(Vector2D tangent, Vector2D direction) {
        Vector2D normalized = tangent.normalize();
        return normalized.dot(direction) < 0.0 ? normalized.reverse() : normalized;
    }

    public static int segmentCountForLength(
            double lengthMeters,
            double intervalMeters,
            int minimumSegments,
            String description) {
        if (!Double.isFinite(lengthMeters) || !Double.isFinite(intervalMeters)) {
            throw new IllegalArgumentException(tr("{0} has invalid length or sample interval.", description));
        }
        double length = Math.max(0.0, lengthMeters);
        double interval = Math.max(1.0, intervalMeters);
        double calculatedSegments = Math.max(minimumSegments, Math.ceil(length / interval));
        if (calculatedSegments + 1.0 > MAX_SAMPLE_POINTS) {
            throw new IllegalArgumentException(tr(
                    "{0} is expected to exceed {1} sampled points. Increase the sample interval or draw a shorter segment.",
                    description,
                    MAX_SAMPLE_POINTS));
        }
        return (int) calculatedSegments;
    }
}
