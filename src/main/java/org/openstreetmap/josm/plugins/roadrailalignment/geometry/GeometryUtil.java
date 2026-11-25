package org.openstreetmap.josm.plugins.roadrailalignment.geometry;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
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

    public static List<EastNorth> simplify(List<EastNorth> points, double tolerance) {
        if (points.size() < 3) {
            return points;
        }
        int maxIndex = 0;
        double maxDistance = 0;
        for (int i = 1; i < points.size() - 1; i++) {
            double distance = perpendicularDistance(points.get(i), points.get(0), points.get(points.size() - 1));
            if (distance > maxDistance) {
                maxDistance = distance;
                maxIndex = i;
            }
        }
        if (maxDistance > tolerance) {
            List<EastNorth> left = simplify(points.subList(0, maxIndex + 1), tolerance);
            List<EastNorth> right = simplify(points.subList(maxIndex, points.size()), tolerance);
            List<EastNorth> result = new ArrayList<>(left.subList(0, left.size() - 1));
            result.addAll(right);
            return result;
        } else {
            return List.of(points.get(0), points.get(points.size() - 1));
        }
    }

    private static double perpendicularDistance(EastNorth point, EastNorth start, EastNorth end) {
        double dx = end.east() - start.east();
        double dy = end.north() - start.north();
        double mag = Math.sqrt(dx * dx + dy * dy);
        if (mag > 0) {
            dx /= mag;
            dy /= mag;
        }
        double pvx = point.east() - start.east();
        double pvy = point.north() - start.north();
        double pvdot = pvx * dx + pvy * dy;
        double ax = pvx - pvdot * dx;
        double ay = pvy - pvdot * dy;
        return Math.sqrt(ax * ax + ay * ay);
    }
}
