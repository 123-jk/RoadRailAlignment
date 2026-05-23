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
        if (tieInPoint == null || tieInPoint.getPoint() == null || target == null || !target.isValid()) {
            throw new IllegalArgumentException(tr("匝道接入点或目标点无效。"));
        }

        EastNorth start = tieInPoint.getPoint();
        Vector2D offset = Vector2D.between(start, target);
        double targetDistance = offset.length();
        if (targetDistance < MIN_TARGET_DISTANCE_METERS) {
            throw new IllegalArgumentException(tr("匝道目标点距离接入点过近。"));
        }

        Vector2D tangent = tieInPoint.getTangent().normalize();
        // 目标在切线背面时先翻转切线，保证匝道总是朝向点击方向外侧展开。
        if (offset.dot(tangent) < 0.0) {
            tangent = tangent.reverse();
        }

        Vector2D leftNormal = tangent.leftNormal();
        double signedDenominator = 2.0 * offset.dot(leftNormal);
        if (Math.abs(signedDenominator) < EPS) {
            // 目标几乎落在切线上时直接退回直线，避免算出接近无穷大的半径。
            if (offset.dot(tangent) <= 0.0) {
                throw new IllegalArgumentException(tr("目标点位于接入切线的反方向。"));
            }
            return LineSampler.sample(start, target, intervalMeters);
        }

        // 半径符号决定左转还是右转，绝对值用于实际曲率大小。
        double signedRadius = offset.lengthSquared() / signedDenominator;
        double radius = Math.abs(signedRadius);
        double requiredMinRadius = Math.max(1.0, minRadiusMeters);
        if (radius < requiredMinRadius) {
            throw new IllegalArgumentException(tr(
                    "切线接出匝道半径 {0} 米，小于最小半径 {1} 米。",
                    RadiusFormatter.formatMetersBelowThreshold(radius, requiredMinRadius),
                    RadiusFormatter.formatThresholdMeters(requiredMinRadius, radius)));
        }

        EastNorth center = leftNormal.pointFrom(start, signedRadius);
        double turnSign = Math.signum(signedRadius);
        return ArcSampler.sample(center, start, target, radius, turnSign, intervalMeters);
    }

    public static double signedCurvatureFromTangent(EastNorth start, Vector2D tangent, EastNorth target) {
        if (start == null || tangent == null || target == null || !start.isValid() || !target.isValid()) {
            throw new IllegalArgumentException(tr("匝道接入点或目标点无效。"));
        }

        Vector2D offset = Vector2D.between(start, target);
        if (offset.length() < MIN_TARGET_DISTANCE_METERS) {
            throw new IllegalArgumentException(tr("匝道目标点距离接入点过近。"));
        }

        // 先把切线朝向目标，再用左法线判断曲率正负。
        Vector2D orientedTangent = GeometryUtil.orientToward(tangent, offset);
        double signedDenominator = 2.0 * offset.dot(orientedTangent.leftNormal());
        if (Math.abs(signedDenominator) < EPS) {
            return 0.0;
        }

        double signedRadius = offset.lengthSquared() / signedDenominator;
        return 1.0 / signedRadius;
    }
}
