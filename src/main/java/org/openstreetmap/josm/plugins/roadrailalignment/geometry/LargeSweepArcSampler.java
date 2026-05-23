package org.openstreetmap.josm.plugins.roadrailalignment.geometry;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.coor.EastNorth;

public final class LargeSweepArcSampler {
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double MIN_SWEEP_RADIANS = Math.toRadians(1.0);
    private static final int MAX_SAMPLE_POINTS = 10000;

    private LargeSweepArcSampler() {
    }

    public static List<EastNorth> sample(
            EastNorth start,
            EastNorth tangentGuide,
            EastNorth endGuide,
            double radiusMeters,
            int extraTurns,
            double spiralLengthMeters,
            double intervalMeters) {
        validate(start, tangentGuide, endGuide, radiusMeters, extraTurns);

        Vector2D startTangent = Vector2D.between(start, tangentGuide).normalize();
        Vector2D guideVector = Vector2D.between(start, endGuide);
        double turnSign = Math.signum(startTangent.cross(guideVector));
        if (turnSign == 0.0) {
            turnSign = 1.0;
        }

        double radius = Math.max(1.0, radiusMeters);
        double totalSweep = totalSweep(start, startTangent, endGuide, radius, turnSign, extraTurns);
        if (Math.abs(totalSweep) < MIN_SWEEP_RADIANS) {
            throw new IllegalArgumentException(tr("The large-sweep circular curve end bearing is too close to the start point."));
        }

        if (spiralLengthMeters > 0.0) {
            return sampleWithSpirals(
                    start,
                    startTangent,
                    radius,
                    totalSweep,
                    spiralLengthMeters,
                    intervalMeters);
        }
        return sampleCircular(start, startTangent, radius, totalSweep, intervalMeters);
    }

    public static Vector2D endTangent(
            EastNorth start,
            EastNorth tangentGuide,
            EastNorth endGuide,
            double radiusMeters,
            int extraTurns,
            double spiralLengthMeters) {
        validate(start, tangentGuide, endGuide, radiusMeters, extraTurns);

        Vector2D startTangent = Vector2D.between(start, tangentGuide).normalize();
        Vector2D guideVector = Vector2D.between(start, endGuide);
        double turnSign = Math.signum(startTangent.cross(guideVector));
        if (turnSign == 0.0) {
            turnSign = 1.0;
        }

        double totalSweep = totalSweep(
                start,
                startTangent,
                endGuide,
                Math.max(1.0, radiusMeters),
                turnSign,
                extraTurns);
        return rotate(startTangent, totalSweep);
    }

    private static List<EastNorth> sampleCircular(
            EastNorth start,
            Vector2D startTangent,
            double radius,
            double sweep,
            double intervalMeters) {
        double turnSign = Math.signum(sweep);
        EastNorth center = normalForTurn(startTangent, turnSign).pointFrom(start, radius);
        double startAngle = Math.atan2(start.north() - center.north(), start.east() - center.east());
        guardSamplePointCount(Math.abs(sweep) * radius, intervalMeters);
        return ArcSampler.sampleBySweep(center, startAngle, sweep, radius, intervalMeters);
    }

    private static List<EastNorth> sampleWithSpirals(
            EastNorth start,
            Vector2D startTangent,
            double radius,
            double totalSweep,
            double spiralLengthMeters,
            double intervalMeters) {
        double turnSign = Math.signum(totalSweep);
        double spiralLength = Math.max(0.0, spiralLengthMeters);
        double spiralSweep = turnSign * spiralLength / (2.0 * radius);
        double circularSweep = totalSweep - 2.0 * spiralSweep;
        if (Math.signum(circularSweep) != turnSign || Math.abs(circularSweep) < MIN_SWEEP_RADIANS) {
            throw new IllegalArgumentException(tr("The transition spiral length is too large for the large-sweep circular curve."));
        }

        guardSamplePointCount(Math.abs(totalSweep) * radius + spiralLength, intervalMeters);

        List<EastNorth> entrySpiral = SpiralSampler.sampleTransitionFromState(
                start,
                startTangent,
                0.0,
                turnSign / radius,
                spiralLength,
                intervalMeters);
        EastNorth circularStart = entrySpiral.get(entrySpiral.size() - 1);
        Vector2D circularStartTangent = SpiralSampler.tangentAtEnd(
                startTangent,
                0.0,
                turnSign / radius,
                spiralLength);

        EastNorth center = normalForTurn(circularStartTangent, turnSign).pointFrom(circularStart, radius);
        double circularStartAngle = Math.atan2(
                circularStart.north() - center.north(),
                circularStart.east() - center.east());
        List<EastNorth> circularArc = ArcSampler.sampleBySweep(
                center,
                circularStartAngle,
                circularSweep,
                radius,
                intervalMeters);
        EastNorth circularEnd = circularArc.get(circularArc.size() - 1);
        Vector2D circularEndTangent = rotate(circularStartTangent, circularSweep);

        List<EastNorth> exitSpiral = SpiralSampler.sampleTransitionFromState(
                circularEnd,
                circularEndTangent,
                turnSign / radius,
                0.0,
                spiralLength,
                intervalMeters);

        List<EastNorth> result = new ArrayList<>();
        GeometryUtil.appendWithoutDuplicate(result, entrySpiral);
        GeometryUtil.appendWithoutDuplicate(result, circularArc);
        GeometryUtil.appendWithoutDuplicate(result, exitSpiral);
        return result;
    }

    private static double totalSweep(
            EastNorth start,
            Vector2D startTangent,
            EastNorth endGuide,
            double radius,
            double turnSign,
            int extraTurns) {
        EastNorth center = normalForTurn(startTangent, turnSign).pointFrom(start, radius);
        double startAngle = Math.atan2(start.north() - center.north(), start.east() - center.east());
        double guideAngle = Math.atan2(endGuide.north() - center.north(), endGuide.east() - center.east());
        double sweep = guideAngle - startAngle;
        if (turnSign > 0.0) {
            while (sweep < 0.0) {
                sweep += TWO_PI;
            }
        } else {
            while (sweep > 0.0) {
                sweep -= TWO_PI;
            }
        }
        return sweep + turnSign * TWO_PI * extraTurns;
    }

    private static Vector2D normalForTurn(Vector2D tangent, double turnSign) {
        return turnSign > 0.0 ? tangent.leftNormal() : tangent.rightNormal();
    }

    private static Vector2D rotate(Vector2D vector, double angleRadians) {
        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);
        return new Vector2D(
                vector.x() * cos - vector.y() * sin,
                vector.x() * sin + vector.y() * cos);
    }

    private static void guardSamplePointCount(double lengthMeters, double intervalMeters) {
        int pointCount = Math.max(2, (int) Math.ceil(lengthMeters / Math.max(1.0, intervalMeters)) + 1);
        if (pointCount > MAX_SAMPLE_POINTS) {
            throw new IllegalArgumentException(tr(
                    "The large-sweep curve is expected to exceed {0} sampled points. Increase the sample interval or reduce extra turns.",
                    MAX_SAMPLE_POINTS));
        }
    }

    private static void validate(
            EastNorth start,
            EastNorth tangentGuide,
            EastNorth endGuide,
            double radiusMeters,
            int extraTurns) {
        if (start == null || tangentGuide == null || endGuide == null
                || !start.isValid() || !tangentGuide.isValid() || !endGuide.isValid()) {
            throw new IllegalArgumentException(tr("Invalid control points for the large-sweep circular curve."));
        }
        if (start.distance(tangentGuide) < 0.01) {
            throw new IllegalArgumentException(tr("The large-sweep circular curve start point and tangent direction point are too close."));
        }
        if (start.distance(endGuide) < 0.01) {
            throw new IllegalArgumentException(tr("The large-sweep circular curve end bearing point is too close."));
        }
        if (radiusMeters <= 0.0) {
            throw new IllegalArgumentException(tr("The large-sweep circular curve radius must be greater than 0."));
        }
        if (extraTurns < 0) {
            throw new IllegalArgumentException(tr("The number of extra full turns cannot be negative."));
        }
    }
}
