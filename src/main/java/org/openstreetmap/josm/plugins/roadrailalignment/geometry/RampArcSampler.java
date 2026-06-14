package org.openstreetmap.josm.plugins.roadrailalignment.geometry;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.List;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.plugins.roadrailalignment.model.TieInPoint;

public final class RampArcSampler {
    private static final double EPS = 1e-9;
    private static final double MIN_TARGET_DISTANCE_METERS = 0.5;

    private RampArcSampler() {
    }

    public static List<EastNorth> sample(
            TieInPoint tieInPoint,
            EastNorth target,
            double minRadiusMeters,
            double intervalMeters) {
        return sample(tieInPoint, target, minRadiusMeters, intervalMeters, false);
    }

    public static List<EastNorth> sample(
            TieInPoint tieInPoint,
            EastNorth target,
            double minRadiusMeters,
            double intervalMeters,
            boolean keepTieInDirection) {
        if (tieInPoint == null || tieInPoint.getPoint() == null || target == null || !target.isValid()) {
            throw new IllegalArgumentException(tr("Invalid ramp tie-in point or target point."));
        }

        EastNorth start = tieInPoint.getPoint();
        Vector2D offset = Vector2D.between(start, target);
        double targetDistance = offset.length();
        if (targetDistance < MIN_TARGET_DISTANCE_METERS) {
            throw new IllegalArgumentException(tr("The ramp target point is too close to the tie-in point."));
        }

        Vector2D tangent = tieInPoint.getTangent().normalize();
        if (!keepTieInDirection && offset.dot(tangent) < 0.0) {
            tangent = tangent.reverse();
        }

        Vector2D leftNormal = tangent.leftNormal();
        double signedDenominator = 2.0 * offset.dot(leftNormal);
        if (Math.abs(signedDenominator) < EPS) {
            if (offset.dot(tangent) <= 0.0) {
                throw new IllegalArgumentException(tr("The target point is opposite the tie-in tangent direction."));
            }
            return LineSampler.sample(start, target, intervalMeters);
        }

        double signedRadius = offset.lengthSquared() / signedDenominator;
        double radius = Math.abs(signedRadius);
        double requiredMinRadius = Math.max(1.0, minRadiusMeters);
        if (radius < requiredMinRadius) {
            throw new IllegalArgumentException(tr(
                    "The tangent ramp radius is {0} m, below the minimum radius {1} m.",
                    RadiusFormatter.formatMetersBelowThreshold(radius, requiredMinRadius),
                    RadiusFormatter.formatThresholdMeters(requiredMinRadius, radius)));
        }

        EastNorth center = leftNormal.pointFrom(start, signedRadius);
        double turnSign = Math.signum(signedRadius);
        double arcLength = arcLength(center, start, target, radius, turnSign);
        GeometryUtil.segmentCountForLength(arcLength, intervalMeters, 1, tr("The tangent ramp arc"));
        return ArcSampler.sample(center, start, target, radius, turnSign, intervalMeters);
    }

    private static double arcLength(
            EastNorth center,
            EastNorth start,
            EastNorth end,
            double radius,
            double turnSign) {
        double startAngle = Math.atan2(start.north() - center.north(), start.east() - center.east());
        double endAngle = Math.atan2(end.north() - center.north(), end.east() - center.east());
        double sweep = endAngle - startAngle;
        if (turnSign > 0.0 && sweep < 0.0) {
            sweep += Math.PI * 2.0;
        } else if (turnSign < 0.0 && sweep > 0.0) {
            sweep -= Math.PI * 2.0;
        }
        return Math.abs(sweep) * radius;
    }

    public static double signedCurvatureFromTangent(EastNorth start, Vector2D tangent, EastNorth target) {
        return signedCurvatureFromTangent(start, tangent, target, false);
    }

    public static Vector2D endTangent(
            TieInPoint tieInPoint,
            EastNorth target,
            double minRadiusMeters,
            boolean keepTieInDirection) {
        if (tieInPoint == null || tieInPoint.getPoint() == null || target == null || !target.isValid()) {
            throw new IllegalArgumentException(tr("Invalid ramp tie-in point or target point."));
        }

        EastNorth start = tieInPoint.getPoint();
        Vector2D offset = Vector2D.between(start, target);
        double targetDistance = offset.length();
        if (targetDistance < MIN_TARGET_DISTANCE_METERS) {
            throw new IllegalArgumentException(tr("The ramp target point is too close to the tie-in point."));
        }

        Vector2D tangent = tieInPoint.getTangent().normalize();
        if (!keepTieInDirection && offset.dot(tangent) < 0.0) {
            tangent = tangent.reverse();
        }

        Vector2D leftNormal = tangent.leftNormal();
        double signedDenominator = 2.0 * offset.dot(leftNormal);
        if (Math.abs(signedDenominator) < EPS) {
            if (offset.dot(tangent) <= 0.0) {
                throw new IllegalArgumentException(tr("The target point is opposite the tie-in tangent direction."));
            }
            return offset.normalize();
        }

        double signedRadius = offset.lengthSquared() / signedDenominator;
        double radius = Math.abs(signedRadius);
        double requiredMinRadius = Math.max(1.0, minRadiusMeters);
        if (radius < requiredMinRadius) {
            throw new IllegalArgumentException(tr(
                    "The tangent ramp radius is {0} m, below the minimum radius {1} m.",
                    RadiusFormatter.formatMetersBelowThreshold(radius, requiredMinRadius),
                    RadiusFormatter.formatThresholdMeters(requiredMinRadius, radius)));
        }

        EastNorth center = leftNormal.pointFrom(start, signedRadius);
        Vector2D radiusAtEnd = Vector2D.between(center, target).normalize();
        return signedRadius > 0.0 ? radiusAtEnd.leftNormal() : radiusAtEnd.rightNormal();
    }

    public static double signedCurvatureFromTangent(
            EastNorth start,
            Vector2D tangent,
            EastNorth target,
            boolean keepTangentDirection) {
        if (start == null || tangent == null || target == null || !start.isValid() || !target.isValid()) {
            throw new IllegalArgumentException(tr("Invalid ramp tie-in point or target point."));
        }

        Vector2D offset = Vector2D.between(start, target);
        if (offset.length() < MIN_TARGET_DISTANCE_METERS) {
            throw new IllegalArgumentException(tr("The ramp target point is too close to the tie-in point."));
        }

        Vector2D orientedTangent = keepTangentDirection
                ? tangent.normalize()
                : GeometryUtil.orientToward(tangent, offset);
        double signedDenominator = 2.0 * offset.dot(orientedTangent.leftNormal());
        if (Math.abs(signedDenominator) < EPS) {
            return 0.0;
        }

        double signedRadius = offset.lengthSquared() / signedDenominator;
        return 1.0 / signedRadius;
    }
}
