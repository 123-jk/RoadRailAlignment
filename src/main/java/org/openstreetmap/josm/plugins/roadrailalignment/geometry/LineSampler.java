package org.openstreetmap.josm.plugins.roadrailalignment.geometry;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.coor.EastNorth;

public final class LineSampler {
    private static final double MIN_LENGTH_METERS = 0.01;

    private LineSampler() {
    }

    public static List<EastNorth> sample(EastNorth start, EastNorth end, double intervalMeters) {
        if (start == null || end == null || !start.isValid() || !end.isValid()) {
            throw new IllegalArgumentException(tr("线形控制点无效。"));
        }
        double length = start.distance(end);
        if (length < MIN_LENGTH_METERS) {
            throw new IllegalArgumentException(tr("两个控制点距离过近。"));
        }

        double interval = Math.max(1.0, intervalMeters);
        int segmentCount = Math.max(1, (int) Math.ceil(length / interval));
        List<EastNorth> points = new ArrayList<>(segmentCount + 1);
        for (int i = 0; i <= segmentCount; i++) {
            double t = (double) i / segmentCount;
            points.add(start.interpolate(end, t));
        }
        return points;
    }
}
