package org.openstreetmap.josm.plugins.roadrailalignment.geometry;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.plugins.roadrailalignment.TieInDirectionMode;
import org.openstreetmap.josm.plugins.roadrailalignment.model.TieInPoint;

public final class HermiteRampSampler {
    private static final double MIN_LENGTH_METERS = 1.0;
    private static final double DEFAULT_HANDLE_SCALE = 0.55;

    private HermiteRampSampler() {
    }

    public static List<EastNorth> sample(
            TieInPoint startTieIn,
            TieInPoint endTieIn,
            double minRadiusMeters,
            double intervalMeters) {
        return sample(startTieIn, endTieIn, minRadiusMeters, TieInDirectionMode.AUTO_CHORD, intervalMeters);
    }

    public static List<EastNorth> sample(
            TieInPoint startTieIn,
            TieInPoint endTieIn,
            double minRadiusMeters,
            TieInDirectionMode directionMode,
            double intervalMeters) {
        if (startTieIn == null || endTieIn == null) {
            throw new IllegalArgumentException(tr("Two tie-in points are required."));
        }

        List<EastNorth> points = sampleWithHandleScale(
                startTieIn,
                endTieIn,
                DEFAULT_HANDLE_SCALE,
                directionMode,
                intervalMeters);

        double minRadius = CurvatureEstimator.minRadius(points);
        double requiredMinRadius = Math.max(1.0, minRadiusMeters);
        if (Double.isFinite(minRadius) && minRadius < requiredMinRadius) {
            throw new IllegalArgumentException(tr(
                    "The minimum radius of the two-end ramp is {0} m, below the required {1} m.",
                    RadiusFormatter.formatMetersBelowThreshold(minRadius, requiredMinRadius),
                    RadiusFormatter.formatThresholdMeters(requiredMinRadius, minRadius)));
        }
        return points;
    }

    public static List<EastNorth> sampleWithHandleScale(
            TieInPoint startTieIn,
            TieInPoint endTieIn,
            double handleScale,
            double intervalMeters) {
        return sampleWithHandleScale(
                startTieIn,
                endTieIn,
                handleScale,
                TieInDirectionMode.AUTO_CHORD,
                intervalMeters);
    }

    public static List<EastNorth> sampleWithHandleScale(
            TieInPoint startTieIn,
            TieInPoint endTieIn,
            double handleScale,
            TieInDirectionMode directionMode,
            double intervalMeters) {
        if (startTieIn == null || endTieIn == null) {
            throw new IllegalArgumentException(tr("Two tie-in points are required."));
        }

        EastNorth start = startTieIn.getPoint();
        EastNorth end = endTieIn.getPoint();
        Vector2D chord = Vector2D.between(start, end);
        double length = chord.length();
        if (length < MIN_LENGTH_METERS) {
            throw new IllegalArgumentException(tr("The two tie-in points are too close."));
        }

        TieInDirectionUtil.Resolved direction = TieInDirectionUtil.resolve(startTieIn, endTieIn, directionMode);
        return sampleWithTangents(
                startTieIn.getPoint(),
                direction.getStartTangent(),
                endTieIn.getPoint(),
                direction.getEndTangent(),
                handleScale,
                intervalMeters);
    }

    public static List<EastNorth> sampleWithTangents(
            EastNorth start,
            Vector2D startTangent,
            EastNorth end,
            Vector2D endTangent,
            double handleScale,
            double intervalMeters) {
        if (start == null || end == null || !start.isValid() || !end.isValid()
                || startTangent == null || endTangent == null) {
            throw new IllegalArgumentException(tr("The two-end ramp tie-in points or tangents are invalid."));
        }
        Vector2D chord = Vector2D.between(start, end);
        double length = chord.length();
        if (length < MIN_LENGTH_METERS) {
            throw new IllegalArgumentException(tr("The two tie-in points are too close."));
        }

        Vector2D normalizedStartTangent = startTangent.normalize();
        Vector2D normalizedEndTangent = endTangent.normalize();
        double normalizedHandleScale = Math.max(0.05, handleScale);
        double handleLength = length * normalizedHandleScale;
        Vector2D m0 = normalizedStartTangent.scale(handleLength);
        Vector2D m1 = normalizedEndTangent.scale(handleLength);

        int segmentCount = GeometryUtil.segmentCountForLength(
                length * 1.4,
                intervalMeters,
                6,
                tr("The two-end ramp"));
        List<EastNorth> points = new ArrayList<>(segmentCount + 1);
        for (int i = 0; i <= segmentCount; i++) {
            double t = (double) i / segmentCount;
            double t2 = t * t;
            double t3 = t2 * t;
            double h00 = 2.0 * t3 - 3.0 * t2 + 1.0;
            double h10 = t3 - 2.0 * t2 + t;
            double h01 = -2.0 * t3 + 3.0 * t2;
            double h11 = t3 - t2;
            points.add(new EastNorth(
                    h00 * start.east() + h10 * m0.x() + h01 * end.east() + h11 * m1.x(),
                    h00 * start.north() + h10 * m0.y() + h01 * end.north() + h11 * m1.y()));
        }
        return points;
    }
}
