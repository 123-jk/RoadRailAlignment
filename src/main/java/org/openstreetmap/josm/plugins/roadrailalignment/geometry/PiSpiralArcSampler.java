package org.openstreetmap.josm.plugins.roadrailalignment.geometry;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.coor.EastNorth;

public final class PiSpiralArcSampler {
    private static final double MIN_ANGLE_RADIANS = Math.toRadians(1.0);

    private PiSpiralArcSampler() {
    }

    public static List<EastNorth> sample(
            EastNorth start,
            EastNorth pi,
            EastNorth end,
            double radiusMeters,
            double spiralLengthMeters,
            double intervalMeters) {
        return sample(start, pi, end, radiusMeters, spiralLengthMeters, intervalMeters, true);
    }

    public static List<EastNorth> sample(
            EastNorth start,
            EastNorth pi,
            EastNorth end,
            double radiusMeters,
            double spiralLengthMeters,
            double intervalMeters,
            boolean includeExitTangent) {
        if (spiralLengthMeters <= 0.0) {
            return PiArcSampler.sample(start, pi, end, radiusMeters, intervalMeters, includeExitTangent);
        }
        if (start == null || pi == null || end == null || !start.isValid() || !pi.isValid() || !end.isValid()) {
            throw new IllegalArgumentException(tr("Invalid PI control points."));
        }

        Vector2D fromPiToStart = Vector2D.between(pi, start).normalize();
        Vector2D fromPiToEnd = Vector2D.between(pi, end).normalize();
        Vector2D incomingTangent = fromPiToStart.reverse();
        Vector2D outgoingTangent = fromPiToEnd;
        double turnAngle = Math.acos(Math.max(-1.0, Math.min(1.0, incomingTangent.dot(outgoingTangent))));
        if (turnAngle < MIN_ANGLE_RADIANS || Math.PI - turnAngle < MIN_ANGLE_RADIANS) {
            throw new IllegalArgumentException(tr("The PI deflection angle is too small or close to 180 degrees; a curve cannot be generated."));
        }

        double turnSign = Math.signum(incomingTangent.cross(outgoingTangent));
        if (turnSign == 0.0) {
            throw new IllegalArgumentException(tr("The selected PI point cannot form a valid turn."));
        }

        double radius = Math.max(1.0, radiusMeters);
        double spiralLength = Math.max(0.0, spiralLengthMeters);
        double spiralAngle = spiralLength / (2.0 * radius);
        if (turnAngle <= 2.0 * spiralAngle) {
            throw new IllegalArgumentException(tr("The transition spiral length is too large for the PI deflection angle."));
        }

        double shift = spiralLength * spiralLength / (24.0 * radius);
        double tangentLength = (radius + shift) * Math.tan(turnAngle / 2.0) + spiralLength / 2.0;
        if (tangentLength >= start.distance(pi) || tangentLength >= pi.distance(end)) {
            throw new IllegalArgumentException(tr("The transition-plus-circular curve tangent length is too large. Reduce the radius or transition spiral length."));
        }

        EastNorth ts = fromPiToStart.pointFrom(pi, tangentLength);
        List<EastNorth> entrySpiral = SpiralSampler.sampleTransitionFromState(
                ts,
                incomingTangent,
                0.0,
                turnSign / radius,
                spiralLength,
                intervalMeters);
        EastNorth st = entrySpiral.get(entrySpiral.size() - 1);

        Vector2D circularStartTangent = SpiralSampler.tangentAtEnd(
                incomingTangent,
                0.0,
                turnSign / radius,
                spiralLength);
        double circularSweep = turnSign * (turnAngle - 2.0 * spiralAngle);
        Vector2D circularEndTangent = rotate(circularStartTangent, circularSweep);
        EastNorth center = normalForTurn(circularStartTangent, turnSign).pointFrom(st, radius);
        EastNorth et = rotateAround(st, center, circularSweep);

        List<EastNorth> exitSpiral = SpiralSampler.sampleTransitionFromState(
                et,
                circularEndTangent,
                turnSign / radius,
                0.0,
                spiralLength,
                intervalMeters);
        EastNorth curveEnd = exitSpiral.get(exitSpiral.size() - 1);

        List<EastNorth> result = new ArrayList<>();
        GeometryUtil.appendWithoutDuplicate(result, LineSampler.sample(start, ts, intervalMeters));
        GeometryUtil.appendWithoutDuplicate(result, entrySpiral);
        GeometryUtil.appendWithoutDuplicate(result, ArcSampler.sample(center, st, et, radius, turnSign, intervalMeters));
        GeometryUtil.appendWithoutDuplicate(result, exitSpiral);
        if (includeExitTangent) {
            GeometryUtil.appendWithoutDuplicate(result, LineSampler.sample(curveEnd, end, intervalMeters));
        }
        return result;
    }

    public static Vector2D endTangent(
            EastNorth start,
            EastNorth pi,
            EastNorth end,
            double radiusMeters,
            double spiralLengthMeters) {
        if (spiralLengthMeters <= 0.0) {
            return PiArcSampler.endTangent(start, pi, end, radiusMeters);
        }
        if (start == null || pi == null || end == null || !start.isValid() || !pi.isValid() || !end.isValid()) {
            throw new IllegalArgumentException(tr("Invalid PI control points."));
        }

        Vector2D incomingTangent = Vector2D.between(pi, start).normalize().reverse();
        Vector2D outgoingTangent = Vector2D.between(pi, end).normalize();
        double turnAngle = Math.acos(Math.max(-1.0, Math.min(1.0, incomingTangent.dot(outgoingTangent))));
        if (turnAngle < MIN_ANGLE_RADIANS || Math.PI - turnAngle < MIN_ANGLE_RADIANS) {
            throw new IllegalArgumentException(tr("The PI deflection angle is too small or close to 180 degrees; a curve cannot be generated."));
        }

        double turnSign = Math.signum(incomingTangent.cross(outgoingTangent));
        if (turnSign == 0.0) {
            throw new IllegalArgumentException(tr("The selected PI point cannot form a valid turn."));
        }

        double radius = Math.max(1.0, radiusMeters);
        double spiralLength = Math.max(0.0, spiralLengthMeters);
        double spiralAngle = spiralLength / (2.0 * radius);
        if (turnAngle <= 2.0 * spiralAngle) {
            throw new IllegalArgumentException(tr("The transition spiral length is too large for the PI deflection angle."));
        }

        double shift = spiralLength * spiralLength / (24.0 * radius);
        double tangentLength = (radius + shift) * Math.tan(turnAngle / 2.0) + spiralLength / 2.0;
        if (tangentLength >= start.distance(pi) || tangentLength >= pi.distance(end)) {
            throw new IllegalArgumentException(tr("The transition-plus-circular curve tangent length is too large. Reduce the radius or transition spiral length."));
        }

        return outgoingTangent;
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

    private static EastNorth rotateAround(EastNorth point, EastNorth center, double angleRadians) {
        Vector2D radiusVector = Vector2D.between(center, point);
        Vector2D rotated = rotate(radiusVector, angleRadians);
        return new EastNorth(
                center.east() + rotated.x(),
                center.north() + rotated.y());
    }
}
