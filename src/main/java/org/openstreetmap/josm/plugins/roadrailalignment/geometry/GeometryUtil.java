package org.openstreetmap.josm.plugins.roadrailalignment.geometry;

import java.util.List;

import org.openstreetmap.josm.data.coor.EastNorth;

public final class GeometryUtil {
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
}

