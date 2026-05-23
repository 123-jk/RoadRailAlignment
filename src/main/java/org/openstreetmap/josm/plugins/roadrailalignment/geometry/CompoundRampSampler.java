package org.openstreetmap.josm.plugins.roadrailalignment.geometry;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.plugins.roadrailalignment.TieInDirectionMode;
import org.openstreetmap.josm.plugins.roadrailalignment.model.TieInPoint;

public final class CompoundRampSampler {
    private CompoundRampSampler() {
    }

    public static List<EastNorth> sampleSingleTieRamp(
            TieInPoint tieInPoint,
            EastNorth target,
            double minRadiusMeters,
            double spiralLengthMeters,
            double intervalMeters) {
        // 先按普通单端匝道算出基础几何，再视长度决定是否叠加缓和过渡。
        List<EastNorth> base = RampArcSampler.sample(tieInPoint, target, minRadiusMeters, intervalMeters);
        if (spiralLengthMeters <= 0.0 || base.size() < 3) {
            return base;
        }

        EastNorth start = tieInPoint.getPoint();
        Vector2D chord = Vector2D.between(start, target);
        Vector2D originalTangent = tieInPoint.getTangent().normalize();
        Vector2D tangent = GeometryUtil.orientToward(originalTangent, chord);
        double sourceCurvature = signedCurvatureFromTieIn(tieInPoint);
        if (tangent.dot(originalTangent) < 0.0) {
            sourceCurvature = -sourceCurvature;
        }
        // 源端曲率按目标方向和最小半径做一次裁剪，避免过渡段过猛。
        double targetCurvature = RampArcSampler.signedCurvatureFromTangent(start, tangent, target);
        sourceCurvature = departureCompatibleSourceCurvature(sourceCurvature, targetCurvature);
        sourceCurvature = capCurvatureMagnitude(sourceCurvature, maxCurvatureForRadius(minRadiusMeters));

        double usableLength = Math.min(spiralLengthMeters, start.distance(target) * 0.35);
        if (usableLength < 1.0 || Math.abs(sourceCurvature - targetCurvature) < 1e-9) {
            return base;
        }

        List<EastNorth> transition = SpiralSampler.sampleTransitionFromState(
                start,
                tangent,
                sourceCurvature,
                targetCurvature,
                usableLength,
                intervalMeters);
        EastNorth transitionEnd = transition.get(transition.size() - 1);
        List<EastNorth> rest = RampArcSampler.sample(
                new TieInPoint(transitionEnd, SpiralSampler.tangentAtEnd(tangent, sourceCurvature, targetCurvature, usableLength),
                        tieInPoint.getSourceWay(), tieInPoint.getSegmentIndex(), 0.0,
                        tieInPoint.getStationMeters() + usableLength,
                        Math.abs(targetCurvature) > 1e-9 ? Math.abs(1.0 / targetCurvature) : Double.NaN,
                        targetCurvature),
                target,
                minRadiusMeters,
                intervalMeters);

        List<EastNorth> result = new ArrayList<>();
        GeometryUtil.appendWithoutDuplicate(result, transition);
        GeometryUtil.appendWithoutDuplicate(result, rest);
        return result;
    }

    public static List<EastNorth> sampleTwoTieRamp(
            TieInPoint startTieIn,
            TieInPoint endTieIn,
            double minRadiusMeters,
            double spiralLengthMeters,
            double intervalMeters) {
        return sampleTwoTieRamp(
                startTieIn,
                endTieIn,
                minRadiusMeters,
                spiralLengthMeters,
                TieInDirectionMode.AUTO_CHORD,
                intervalMeters);
    }

    public static List<EastNorth> sampleTwoTieRamp(
            TieInPoint startTieIn,
            TieInPoint endTieIn,
            double minRadiusMeters,
            double spiralLengthMeters,
            TieInDirectionMode directionMode,
            double intervalMeters) {
        // 双端连接先尝试普通 Hermite，几何不成立时再退回中间直线插入方案。
        List<EastNorth> base;
        try {
            base = HermiteRampSampler.sample(startTieIn, endTieIn, minRadiusMeters, directionMode, intervalMeters);
        } catch (IllegalArgumentException exception) {
            return StraightInsertRampSampler.sample(startTieIn, endTieIn, minRadiusMeters, 0, directionMode, intervalMeters);
        }
        if (spiralLengthMeters <= 0.0 || base.size() < 4) {
            return base;
        }

        EastNorth start = startTieIn.getPoint();
        EastNorth end = endTieIn.getPoint();
        Vector2D chord = Vector2D.between(start, end);
        double length = chord.length();
        if (length < 2.0) {
            throw new IllegalArgumentException(tr("两个接入点距离过近。"));
        }

        // 两端各预留一段缓和长度，中间主体再去补连接形状。
        double transitionLength = Math.min(spiralLengthMeters, length * 0.25);
        if (transitionLength < 1.0 || length <= transitionLength * 2.0) {
            return base;
        }

        TieInDirectionUtil.Resolved direction = TieInDirectionUtil.resolve(startTieIn, endTieIn, directionMode);
        Vector2D startTangent = direction.getStartTangent();
        Vector2D endTangent = direction.getEndTangent();
        double startCurvature = direction.getStartCurvature();
        double endCurvature = direction.getEndCurvature();
        double maxCurvature = maxCurvatureForRadius(minRadiusMeters);
        startCurvature = capCurvatureMagnitude(startCurvature, maxCurvature);
        endCurvature = capCurvatureMagnitude(endCurvature, maxCurvature);

        List<EastNorth> startTransition = SpiralSampler.sampleTransitionFromState(
                start,
                startTangent,
                startCurvature,
                0.0,
                transitionLength,
                intervalMeters);
        List<EastNorth> endTransitionReverse = SpiralSampler.sampleTransitionFromState(
                end,
                endTangent.reverse(),
                -endCurvature,
                0.0,
                transitionLength,
                intervalMeters);
        List<EastNorth> endTransition = new ArrayList<>();
        for (int i = endTransitionReverse.size() - 1; i >= 0; i--) {
            endTransition.add(endTransitionReverse.get(i));
        }

        EastNorth middleStart = startTransition.get(startTransition.size() - 1);
        EastNorth middleEnd = endTransition.get(0);
        TieInPoint middleStartTie = new TieInPoint(
                middleStart,
                SpiralSampler.tangentAtEnd(startTangent, startCurvature, 0.0, transitionLength),
                startTieIn.getSourceWay(),
                startTieIn.getSegmentIndex(),
                0.0);
        TieInPoint middleEndTie = new TieInPoint(
                middleEnd,
                SpiralSampler.tangentAtEnd(endTangent.reverse(), -endCurvature, 0.0, transitionLength).reverse(),
                endTieIn.getSourceWay(),
                endTieIn.getSegmentIndex(),
                0.0);

        List<EastNorth> middle;
        try {
            // 中间段优先保持平滑连接，达不到最小半径时再退回带直线的解。
            middle = HermiteRampSampler.sampleWithTangents(
                    middleStartTie.getPoint(),
                    middleStartTie.getTangent(),
                    middleEndTie.getPoint(),
                    middleEndTie.getTangent(),
                    0.55,
                    intervalMeters);
            double middleMinRadius = CurvatureEstimator.minRadius(middle);
            if (Double.isFinite(middleMinRadius) && middleMinRadius < Math.max(1.0, minRadiusMeters)) {
                throw new IllegalArgumentException(tr("双端匝道中间段半径不足。"));
            }
        } catch (IllegalArgumentException exception) {
            middle = StraightInsertRampSampler.sample(
                    middleStart,
                    middleStartTie.getTangent(),
                    middleEnd,
                    middleEndTie.getTangent(),
                    minRadiusMeters,
                    intervalMeters);
        }
        List<EastNorth> result = new ArrayList<>();
        GeometryUtil.appendWithoutDuplicate(result, startTransition);
        GeometryUtil.appendWithoutDuplicate(result, middle);
        GeometryUtil.appendWithoutDuplicate(result, endTransition);
        return result;
    }

    private static double signedCurvatureFromTieIn(TieInPoint tieInPoint) {
        return tieInPoint == null || !Double.isFinite(tieInPoint.getEstimatedCurvature())
                ? 0.0
                : tieInPoint.getEstimatedCurvature();
    }

    private static double departureCompatibleSourceCurvature(double sourceCurvature, double targetCurvature) {
        if (!Double.isFinite(sourceCurvature) || Math.abs(sourceCurvature) < 1e-9 || Math.abs(targetCurvature) < 1e-9) {
            return 0.0;
        }

        double sourceSign = Math.signum(sourceCurvature);
        double targetSign = Math.signum(targetCurvature);
        if (sourceSign != targetSign) {
            return 0.0;
        }

        double cappedMagnitude = Math.min(Math.abs(sourceCurvature), Math.abs(targetCurvature));
        return Math.copySign(cappedMagnitude, targetCurvature);
    }

    private static double capCurvatureMagnitude(double curvature, double maxAbsCurvature) {
        if (!Double.isFinite(curvature) || !Double.isFinite(maxAbsCurvature) || maxAbsCurvature <= 0.0) {
            return 0.0;
        }
        if (Math.abs(curvature) <= maxAbsCurvature) {
            return curvature;
        }
        return Math.copySign(maxAbsCurvature, curvature);
    }

    private static double maxCurvatureForRadius(double radiusMeters) {
        return 1.0 / Math.max(1.0, radiusMeters);
    }
}
