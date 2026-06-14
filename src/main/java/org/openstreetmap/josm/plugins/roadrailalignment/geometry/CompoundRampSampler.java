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
        return sampleSingleTieRamp(tieInPoint, target, minRadiusMeters, spiralLengthMeters, intervalMeters, false);
    }

    public static List<EastNorth> sampleSingleTieRamp(
            TieInPoint tieInPoint,
            EastNorth target,
            double minRadiusMeters,
            double spiralLengthMeters,
            double intervalMeters,
            boolean keepTieInDirection) {
        return sampleSingleTieRamp(
                tieInPoint,
                target,
                minRadiusMeters,
                spiralLengthMeters,
                intervalMeters,
                keepTieInDirection,
                false);
    }

    public static List<EastNorth> sampleSingleTieRamp(
            TieInPoint tieInPoint,
            EastNorth target,
            double minRadiusMeters,
            double spiralLengthMeters,
            double intervalMeters,
            boolean keepTieInDirection,
            boolean keepExitCurvature) {
        List<EastNorth> base = RampArcSampler.sample(
                tieInPoint,
                target,
                minRadiusMeters,
                intervalMeters,
                keepTieInDirection);
        if (spiralLengthMeters <= 0.0 || base.size() < 3) {
            return base;
        }

        EastNorth start = tieInPoint.getPoint();
        Vector2D chord = Vector2D.between(start, target);
        Vector2D originalTangent = tieInPoint.getTangent().normalize();
        Vector2D tangent = keepTieInDirection
                ? originalTangent
                : GeometryUtil.orientToward(originalTangent, chord);
        double sourceCurvature = signedCurvatureFromTieIn(tieInPoint);
        if (tangent.dot(originalTangent) < 0.0) {
            sourceCurvature = -sourceCurvature;
        }
        double targetCurvature = RampArcSampler.signedCurvatureFromTangent(
                start,
                tangent,
                target,
                keepTieInDirection);
        sourceCurvature = departureCompatibleSourceCurvature(sourceCurvature, targetCurvature, keepTieInDirection);
        sourceCurvature = capCurvatureMagnitude(sourceCurvature, maxCurvatureForRadius(minRadiusMeters));

        double usableLength = Math.min(spiralLengthMeters, start.distance(target) * 0.35);
        if (usableLength < 1.0 || Math.abs(sourceCurvature - targetCurvature) < 1e-9) {
            return keepExitCurvature
                    ? base
                    : appendExitTransitionToZero(base, targetCurvature, usableLength, intervalMeters);
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
                new TieInPoint(
                        transitionEnd,
                        SpiralSampler.tangentAtEnd(tangent, sourceCurvature, targetCurvature, usableLength),
                        tieInPoint.getSourceWay(),
                        tieInPoint.getSegmentIndex(),
                        0.0,
                        tieInPoint.getStationMeters() + usableLength,
                        Math.abs(targetCurvature) > 1e-9 ? Math.abs(1.0 / targetCurvature) : Double.NaN,
                        targetCurvature),
                target,
                minRadiusMeters,
                intervalMeters,
                true);

        List<EastNorth> restWithExit = keepExitCurvature
                ? rest
                : appendExitTransitionToZero(rest, targetCurvature, usableLength, intervalMeters);
        List<EastNorth> result = new ArrayList<>();
        GeometryUtil.appendWithoutDuplicate(result, transition);
        GeometryUtil.appendWithoutDuplicate(result, restWithExit);
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
            throw new IllegalArgumentException(tr("The two tie-in points are too close."));
        }

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
            middle = HermiteRampSampler.sampleWithTangents(
                    middleStartTie.getPoint(),
                    middleStartTie.getTangent(),
                    middleEndTie.getPoint(),
                    middleEndTie.getTangent(),
                    0.55,
                    intervalMeters);
            double middleMinRadius = CurvatureEstimator.minRadius(middle);
            if (Double.isFinite(middleMinRadius) && middleMinRadius < Math.max(1.0, minRadiusMeters)) {
                throw new IllegalArgumentException(tr("The middle segment radius of the two-end ramp is insufficient."));
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

    private static List<EastNorth> appendExitTransitionToZero(
            List<EastNorth> points,
            double startCurvature,
            double transitionLength,
            double intervalMeters) {
        if (points == null || points.size() < 3
                || !Double.isFinite(startCurvature)
                || Math.abs(startCurvature) < 1e-9
                || transitionLength < 1.0) {
            return points;
        }

        EastNorth target = points.get(points.size() - 1);
        Vector2D endTangent = endTangent(points);
        if (target == null || !target.isValid() || endTangent == null) {
            return points;
        }

        double usableLength = Math.min(transitionLength, pathLength(points) * 0.35);
        if (usableLength < 1.0) {
            return points;
        }

        ExitStart exitStart = bestExitStart(points, target, startCurvature, usableLength, intervalMeters);
        if (exitStart == null) {
            return points;
        }
        List<EastNorth> exitTransition = SpiralSampler.sampleTransitionFromState(
                exitStart.point,
                exitStart.tangent,
                startCurvature,
                0.0,
                exitStart.length,
                intervalMeters);

        List<EastNorth> result = new ArrayList<>();
        GeometryUtil.appendWithoutDuplicate(result, exitStart.prefix);
        GeometryUtil.appendWithoutDuplicate(result, exitTransition);
        EastNorth exitEnd = exitTransition.get(exitTransition.size() - 1);
        if (exitEnd.distance(target) > 0.001) {
            appendStraightTail(result, exitEnd, target, intervalMeters);
        } else {
            result.add(target);
        }
        result.set(result.size() - 1, target);
        return result;
    }

    private static void appendStraightTail(
            List<EastNorth> result,
            EastNorth start,
            EastNorth target,
            double intervalMeters) {
        double length = start.distance(target);
        if (length <= 0.001) {
            return;
        }
        int segmentCount = GeometryUtil.segmentCountForLength(
                length,
                Math.max(0.25, intervalMeters),
                2,
                tr("The ramp tail"));
        for (int i = 1; i <= segmentCount; i++) {
            GeometryUtil.appendWithoutDuplicate(result, List.of(interpolate(start, target, (double) i / segmentCount)));
        }
    }

    private static ExitStart bestExitStart(
            List<EastNorth> points,
            EastNorth target,
            double startCurvature,
            double transitionLength,
            double intervalMeters) {
        double totalLength = pathLength(points);
        if (totalLength < 1.0) {
            return null;
        }

        ExitStart best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        int lengthSampleCount = 12;
        int distanceSampleCount = 48;
        double minLength = Math.min(transitionLength, 1.0);
        for (int lengthIndex = 0; lengthIndex <= lengthSampleCount; lengthIndex++) {
            double lengthRatio = lengthSampleCount == 0 ? 0.0 : (double) lengthIndex / lengthSampleCount;
            double candidateLength = transitionLength - (transitionLength - minLength) * lengthRatio;
            double maxDistance = Math.min(totalLength - 0.001, Math.max(candidateLength, candidateLength * 3.0));
            double minDistance = Math.min(maxDistance, Math.max(0.001, candidateLength * 0.25));
            for (int i = 0; i <= distanceSampleCount; i++) {
                double ratio = distanceSampleCount == 0 ? 0.0 : (double) i / distanceSampleCount;
                double distance = minDistance + (maxDistance - minDistance) * ratio;
                ExitStart candidate = exitStartBeforeDistanceFromEnd(points, distance, candidateLength);
                if (candidate == null) {
                    continue;
                }
                Vector2D exitTangent = SpiralSampler.tangentAtEnd(
                        candidate.tangent,
                        startCurvature,
                        0.0,
                        candidateLength);
                List<EastNorth> transition = SpiralSampler.sampleTransitionFromState(
                        candidate.point,
                        candidate.tangent,
                        startCurvature,
                        0.0,
                        candidateLength,
                        intervalMeters);
                EastNorth exitEnd = transition.get(transition.size() - 1);
                Vector2D toTarget = Vector2D.between(exitEnd, target);
                double distanceToTarget = toTarget.length();
                double lateralError = Math.abs(exitTangent.cross(toTarget));
                double reversePenalty = Math.max(0.0, -exitTangent.dot(toTarget)) * 20.0;
                double lengthPenalty = (transitionLength - candidateLength) * 0.02;
                double score = lateralError + reversePenalty + distanceToTarget * 0.02 + lengthPenalty;
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best == null ? exitStartBeforeDistanceFromEnd(points, transitionLength, transitionLength) : best;
    }

    private static ExitStart exitStartBeforeDistanceFromEnd(
            List<EastNorth> points,
            double distanceFromEnd,
            double transitionLength) {
        if (points == null || points.size() < 2) {
            return null;
        }
        double remaining = Math.max(0.0, distanceFromEnd);
        int index = points.size() - 1;
        EastNorth next = points.get(index);
        while (index > 0 && remaining > 0.0) {
            EastNorth previous = points.get(index - 1);
            double segmentLength = previous.distance(next);
            if (segmentLength >= remaining && segmentLength > 1e-9) {
                double keepRatio = (segmentLength - remaining) / segmentLength;
                List<EastNorth> prefix = new ArrayList<>(points.subList(0, index));
                EastNorth point = interpolate(previous, next, keepRatio);
                prefix.add(point);
                Vector2D tangent = Vector2D.between(previous, next).normalize();
                return new ExitStart(prefix, point, tangent, transitionLength);
            }
            remaining -= segmentLength;
            index--;
            next = previous;
        }
        List<EastNorth> prefix = new ArrayList<>();
        prefix.add(points.get(0));
        Vector2D tangent = firstTangent(points);
        return tangent == null ? null : new ExitStart(prefix, points.get(0), tangent, transitionLength);
    }

    private static EastNorth interpolate(EastNorth start, EastNorth end, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return new EastNorth(
                start.east() + (end.east() - start.east()) * clamped,
                start.north() + (end.north() - start.north()) * clamped);
    }

    private static double pathLength(List<EastNorth> points) {
        if (points == null || points.size() < 2) {
            return 0.0;
        }
        double length = 0.0;
        for (int i = 1; i < points.size(); i++) {
            length += points.get(i - 1).distance(points.get(i));
        }
        return length;
    }

    private static Vector2D endTangent(List<EastNorth> points) {
        if (points == null || points.size() < 2) {
            return null;
        }
        EastNorth end = points.get(points.size() - 1);
        for (int i = points.size() - 2; i >= 0; i--) {
            EastNorth previous = points.get(i);
            if (previous != null && previous.isValid() && previous.distance(end) > 0.001) {
                return Vector2D.between(previous, end).normalize();
            }
        }
        return null;
    }

    private static Vector2D firstTangent(List<EastNorth> points) {
        if (points == null || points.size() < 2) {
            return null;
        }
        EastNorth start = points.get(0);
        for (int i = 1; i < points.size(); i++) {
            EastNorth next = points.get(i);
            if (next != null && next.isValid() && next.distance(start) > 0.001) {
                return Vector2D.between(start, next).normalize();
            }
        }
        return null;
    }

    private static double departureCompatibleSourceCurvature(
            double sourceCurvature,
            double targetCurvature,
            boolean allowInflection) {
        if (!Double.isFinite(sourceCurvature) || Math.abs(sourceCurvature) < 1e-9 || Math.abs(targetCurvature) < 1e-9) {
            return allowInflection && Double.isFinite(sourceCurvature) ? sourceCurvature : 0.0;
        }
        if (allowInflection) {
            return sourceCurvature;
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

    private static final class ExitStart {
        private final List<EastNorth> prefix;
        private final EastNorth point;
        private final Vector2D tangent;
        private final double length;

        private ExitStart(List<EastNorth> prefix, EastNorth point, Vector2D tangent, double length) {
            this.prefix = prefix;
            this.point = point;
            this.tangent = tangent;
            this.length = length;
        }
    }
}
