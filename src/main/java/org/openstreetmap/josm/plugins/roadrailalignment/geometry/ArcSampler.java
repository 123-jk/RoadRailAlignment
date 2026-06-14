package org.openstreetmap.josm.plugins.roadrailalignment.geometry;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.coor.EastNorth;

public final class ArcSampler {
    private static final int MIN_ARC_SEGMENTS = 6;

    private ArcSampler() {
    }

    public static List<EastNorth> sample(
            EastNorth center,
            EastNorth start,
            EastNorth end,
            double radius,
            double turnSign,
            double intervalMeters) {
        double startAngle = Math.atan2(start.north() - center.north(), start.east() - center.east());
        double endAngle = Math.atan2(end.north() - center.north(), end.east() - center.east());
        double sweep = endAngle - startAngle;
        if (turnSign > 0.0 && sweep < 0.0) {
            sweep += Math.PI * 2.0;
        } else if (turnSign < 0.0 && sweep > 0.0) {
            sweep -= Math.PI * 2.0;
        }
        List<EastNorth> points = sampleBySweep(center, startAngle, sweep, radius, intervalMeters);
        points.set(0, start);
        points.set(points.size() - 1, end);
        return points;
    }

    public static List<EastNorth> sampleBySweep(
            EastNorth center,
            double startAngle,
            double sweep,
            double radius,
            double intervalMeters) {
        double arcLength = Math.abs(sweep) * radius;
        int segmentCount = GeometryUtil.segmentCountForLength(
                arcLength,
                intervalMeters,
                MIN_ARC_SEGMENTS,
                tr("The circular arc"));
        List<EastNorth> points = new ArrayList<>(segmentCount + 1);
        for (int i = 0; i <= segmentCount; i++) {
            double t = (double) i / segmentCount;
            double angle = startAngle + sweep * t;
            points.add(new EastNorth(
                    center.east() + Math.cos(angle) * radius,
                    center.north() + Math.sin(angle) * radius));
        }
        return points;
    }
}
