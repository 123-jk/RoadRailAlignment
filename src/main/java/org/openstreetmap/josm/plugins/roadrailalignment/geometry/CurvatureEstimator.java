package org.openstreetmap.josm.plugins.roadrailalignment.geometry;

import java.util.List;

import org.openstreetmap.josm.data.coor.EastNorth;

public final class CurvatureEstimator {
    private static final double EPS = 1e-9;

    private CurvatureEstimator() {
    }

    public static double radiusFromThreePoints(EastNorth first, EastNorth second, EastNorth third) {
        double a = second.distance(third);
        double b = first.distance(third);
        double c = first.distance(second);
        double twiceArea = Math.abs(
                (second.east() - first.east()) * (third.north() - first.north())
                        - (second.north() - first.north()) * (third.east() - first.east()));
        if (twiceArea < EPS || a < EPS || b < EPS || c < EPS) {
            return Double.POSITIVE_INFINITY;
        }
        return a * b * c / (2.0 * twiceArea);
    }

    public static double minRadius(List<EastNorth> points) {
        if (points == null || points.size() < 3) {
            return Double.POSITIVE_INFINITY;
        }
        double minRadius = Double.POSITIVE_INFINITY;
        for (int i = 1; i < points.size() - 1; i++) {
            double radius = radiusFromThreePoints(points.get(i - 1), points.get(i), points.get(i + 1));
            if (radius < minRadius) {
                minRadius = radius;
            }
        }
        return minRadius;
    }
}

