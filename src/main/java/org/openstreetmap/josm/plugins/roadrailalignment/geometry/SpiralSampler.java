package org.openstreetmap.josm.plugins.roadrailalignment.geometry;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.coor.EastNorth;

public final class SpiralSampler {
    private static final double MIN_LENGTH_METERS = 1.0;

    private SpiralSampler() {
    }

    public static List<EastNorth> sampleTransition(
            EastNorth start,
            EastNorth tangentGuide,
            EastNorth sideGuide,
            double targetRadiusMeters,
            double intervalMeters) {
        if (start == null || tangentGuide == null || sideGuide == null
                || !start.isValid() || !tangentGuide.isValid() || !sideGuide.isValid()) {
            throw new IllegalArgumentException(tr("Invalid transition spiral control points."));
        }

        Vector2D tangent = Vector2D.between(start, tangentGuide).normalize();
        Vector2D side = Vector2D.between(start, sideGuide);
        double length = start.distance(tangentGuide);
        if (length < MIN_LENGTH_METERS) {
            throw new IllegalArgumentException(tr("The transition spiral length is too short."));
        }
        if (targetRadiusMeters <= 0.0) {
            throw new IllegalArgumentException(tr("The transition spiral target radius must be greater than 0."));
        }

        double turnSign = Math.signum(tangent.cross(side));
        if (turnSign == 0.0) {
            turnSign = 1.0;
        }

        int segmentCount = Math.max(2, (int) Math.ceil(length / Math.max(1.0, intervalMeters)));
        double ds = length / segmentCount;
        double heading = Math.atan2(tangent.y(), tangent.x());
        double x = start.east();
        double y = start.north();
        double endCurvature = turnSign / targetRadiusMeters;

        List<EastNorth> points = new ArrayList<>(segmentCount + 1);
        points.add(start);
        for (int i = 0; i < segmentCount; i++) {
            double sMid = (i + 0.5) * ds;
            double curvature = endCurvature * sMid / length;
            double headingChange = curvature * ds;
            double segmentHeading = heading + headingChange * 0.5;
            x += Math.cos(segmentHeading) * ds;
            y += Math.sin(segmentHeading) * ds;
            heading += headingChange;
            points.add(new EastNorth(x, y));
        }
        return points;
    }

    public static List<EastNorth> sampleTransitionFromState(
            EastNorth start,
            Vector2D startTangent,
            double startCurvature,
            double endCurvature,
            double lengthMeters,
            double intervalMeters) {
        if (start == null || !start.isValid() || startTangent == null) {
            throw new IllegalArgumentException(tr("Invalid transition spiral start point or tangent."));
        }
        if (lengthMeters < MIN_LENGTH_METERS) {
            throw new IllegalArgumentException(tr("The transition spiral length is too short."));
        }

        Vector2D tangent = startTangent.normalize();
        int segmentCount = Math.max(2, (int) Math.ceil(lengthMeters / Math.max(1.0, intervalMeters)));
        double ds = lengthMeters / segmentCount;
        double heading = Math.atan2(tangent.y(), tangent.x());
        double x = start.east();
        double y = start.north();

        List<EastNorth> points = new ArrayList<>(segmentCount + 1);
        points.add(start);
        for (int i = 0; i < segmentCount; i++) {
            double tMid = (i + 0.5) / segmentCount;
            double curvature = startCurvature + (endCurvature - startCurvature) * tMid;
            double headingChange = curvature * ds;
            double segmentHeading = heading + headingChange * 0.5;
            x += Math.cos(segmentHeading) * ds;
            y += Math.sin(segmentHeading) * ds;
            heading += headingChange;
            points.add(new EastNorth(x, y));
        }
        return points;
    }

    public static Vector2D tangentAtEnd(
            Vector2D startTangent,
            double startCurvature,
            double endCurvature,
            double lengthMeters) {
        Vector2D tangent = startTangent.normalize();
        double heading = Math.atan2(tangent.y(), tangent.x());
        heading += (startCurvature + endCurvature) * 0.5 * lengthMeters;
        return new Vector2D(Math.cos(heading), Math.sin(heading));
    }
}
