package org.openstreetmap.josm.plugins.roadrailalignment.geometry;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.plugins.roadrailalignment.TieInDirectionMode;
import org.openstreetmap.josm.plugins.roadrailalignment.model.TieInPoint;

public final class OptimizedTwoTieRampSampler {
    private static final double MAX_AUTO_SPIRAL_LENGTH_METERS = 5000.0;
    private static final double MIN_HANDLE_SCALE = 0.18;
    private static final double MAX_HANDLE_SCALE = 1.20;
    private static final double MIN_RADIUS_TOLERANCE_METERS = 1e-6;
    private static final int HANDLE_CANDIDATE_COUNT = 52;
    private static final int TRANSITION_CANDIDATE_COUNT = 8;

    private OptimizedTwoTieRampSampler() {
    }

    public static Result sample(
            TieInPoint startTieIn,
            TieInPoint endTieIn,
            double requiredMinRadiusMeters,
            boolean useSpiralTransitions,
            double intervalMeters) {
        return sample(startTieIn, endTieIn, requiredMinRadiusMeters, useSpiralTransitions, 0, intervalMeters);
    }

    public static Result sample(
            TieInPoint startTieIn,
            TieInPoint endTieIn,
            double requiredMinRadiusMeters,
            boolean useSpiralTransitions,
            int extraLoopTurns,
            double intervalMeters) {
        return sample(
                startTieIn,
                endTieIn,
                requiredMinRadiusMeters,
                useSpiralTransitions,
                extraLoopTurns,
                TieInDirectionMode.AUTO_CHORD,
                intervalMeters);
    }

    public static Result sample(
            TieInPoint startTieIn,
            TieInPoint endTieIn,
            double requiredMinRadiusMeters,
            boolean useSpiralTransitions,
            int extraLoopTurns,
            TieInDirectionMode directionMode,
            double intervalMeters) {
        if (startTieIn == null || endTieIn == null) {
            throw new IllegalArgumentException(tr("Two tie-in points are required."));
        }

        EastNorth start = startTieIn.getPoint();
        EastNorth end = endTieIn.getPoint();
        Vector2D chord = Vector2D.between(start, end);
        double length = chord.length();
        if (length < 2.0) {
            throw new IllegalArgumentException(tr("The two tie-in points are too close."));
        }

        TieInDirectionUtil.Resolved direction = TieInDirectionUtil.resolve(startTieIn, endTieIn, directionMode);
        Vector2D startTangent = direction.getStartTangent();
        Vector2D endTangent = direction.getEndTangent();
        double startCurvature = direction.getStartCurvature();
        double endCurvature = direction.getEndCurvature();
        double maxCurvature = maxCurvatureForRadius(requiredMinRadiusMeters);
        startCurvature = capCurvatureMagnitude(startCurvature, maxCurvature);
        endCurvature = capCurvatureMagnitude(endCurvature, maxCurvature);

        Candidate best = null;
        for (double transitionLength : transitionCandidates(length, useSpiralTransitions)) {
            Candidate candidate = bestForTransitionLength(
                    startTieIn,
                    endTieIn,
                    startTangent,
                    endTangent,
                    startCurvature,
                    endCurvature,
                    transitionLength,
                    Math.max(0, extraLoopTurns),
                    requiredMinRadiusMeters,
                    intervalMeters);
            if (candidate != null && (best == null || candidate.score() > best.score())) {
                best = candidate;
            }
        }

        if (best == null) {
            throw new IllegalArgumentException(tr("Could not automatically optimize the two-way connection."));
        }

        double requiredMinRadius = Math.max(1.0, requiredMinRadiusMeters);
        if (Double.isFinite(best.designRadiusMeters)
                && best.designRadiusMeters < requiredMinRadius - MIN_RADIUS_TOLERANCE_METERS) {
            throw new IllegalArgumentException(tr(
                    "The maximum usable R after automatic optimization is {0} m, still below the required {1} m. Move the tie-in points farther apart or lower the minimum radius.",
                    RadiusFormatter.formatMetersBelowThreshold(best.designRadiusMeters, requiredMinRadius),
                    RadiusFormatter.formatThresholdMeters(requiredMinRadius, best.designRadiusMeters)));
        }

        return new Result(
                best.points,
                best.designRadiusMeters,
                best.transitionLengthMeters,
                best.handleScale,
                best.insertedStraight,
                best.insertedLoop,
                direction.isSourceWayDirection());
    }

    private static Candidate bestForTransitionLength(
            TieInPoint startTieIn,
            TieInPoint endTieIn,
            Vector2D startTangent,
            Vector2D endTangent,
            double startCurvature,
            double endCurvature,
            double transitionLength,
            int extraLoopTurns,
            double requiredMinRadiusMeters,
            double intervalMeters) {
        List<EastNorth> startTransition = Collections.singletonList(startTieIn.getPoint());
        List<EastNorth> endTransition = Collections.singletonList(endTieIn.getPoint());
        EastNorth middleStart = startTieIn.getPoint();
        EastNorth middleEnd = endTieIn.getPoint();
        Vector2D middleStartTangent = startTangent;
        Vector2D middleEndTangent = endTangent;

        if (transitionLength > 0.0) {
            startTransition = SpiralSampler.sampleTransitionFromState(
                    startTieIn.getPoint(),
                    startTangent,
                    startCurvature,
                    0.0,
                    transitionLength,
                    intervalMeters);
            List<EastNorth> endTransitionReverse = SpiralSampler.sampleTransitionFromState(
                    endTieIn.getPoint(),
                    endTangent.reverse(),
                    -endCurvature,
                    0.0,
                    transitionLength,
                    intervalMeters);
            endTransition = new ArrayList<>();
            for (int i = endTransitionReverse.size() - 1; i >= 0; i--) {
                endTransition.add(endTransitionReverse.get(i));
            }

            middleStart = startTransition.get(startTransition.size() - 1);
            middleEnd = endTransition.get(0);
            middleStartTangent = SpiralSampler.tangentAtEnd(startTangent, startCurvature, 0.0, transitionLength);
            middleEndTangent = SpiralSampler.tangentAtEnd(endTangent.reverse(), -endCurvature, 0.0, transitionLength)
                    .reverse();
        }

        if (middleStart.distance(middleEnd) < 2.0) {
            return null;
        }

        TieInPoint middleStartTie = new TieInPoint(
                middleStart,
                middleStartTangent,
                startTieIn.getSourceWay(),
                startTieIn.getSegmentIndex(),
                0.0);
        TieInPoint middleEndTie = new TieInPoint(
                middleEnd,
                middleEndTangent,
                endTieIn.getSourceWay(),
                endTieIn.getSegmentIndex(),
                0.0);

        Candidate best = null;
        for (int i = 0; i < HANDLE_CANDIDATE_COUNT; i++) {
            double t = (double) i / (HANDLE_CANDIDATE_COUNT - 1);
            double handleScale = MIN_HANDLE_SCALE + (MAX_HANDLE_SCALE - MIN_HANDLE_SCALE) * t;
            List<EastNorth> middle = HermiteRampSampler.sampleWithTangents(
                    middleStartTie.getPoint(),
                    middleStartTie.getTangent(),
                    middleEndTie.getPoint(),
                    middleEndTie.getTangent(),
                    handleScale,
                    intervalMeters);
            if (!isReasonablePath(middle, middleStart.distance(middleEnd))) {
                continue;
            }

            List<EastNorth> full = new ArrayList<>();
            GeometryUtil.appendWithoutDuplicate(full, startTransition);
            GeometryUtil.appendWithoutDuplicate(full, middle);
            GeometryUtil.appendWithoutDuplicate(full, endTransition);
            double designRadius = CurvatureEstimator.minRadius(full);
            Candidate candidate = new Candidate(full, designRadius, transitionLength, handleScale, false);
            if (best == null || candidate.score() > best.score()) {
                best = candidate;
            }
        }
        Candidate straightInsertCandidate = straightInsertCandidate(
                startTransition,
                endTransition,
                middleStart,
                middleEnd,
                middleStartTangent,
                middleEndTangent,
                requiredMinRadiusMeters,
                extraLoopTurns,
                transitionLength,
                intervalMeters);
        if (straightInsertCandidate != null && (best == null || straightInsertCandidate.score() > best.score())) {
            best = straightInsertCandidate;
        }
        return best;
    }

    private static Candidate straightInsertCandidate(
            List<EastNorth> startTransition,
            List<EastNorth> endTransition,
            EastNorth middleStart,
            EastNorth middleEnd,
            Vector2D middleStartTangent,
            Vector2D middleEndTangent,
            double requiredMinRadiusMeters,
            int extraLoopTurns,
            double transitionLength,
            double intervalMeters) {
        try {
            List<EastNorth> middle = StraightInsertRampSampler.sample(
                    middleStart,
                    middleStartTangent,
                    middleEnd,
                    middleEndTangent,
                    requiredMinRadiusMeters,
                    extraLoopTurns,
                    intervalMeters);
            List<EastNorth> full = new ArrayList<>();
            GeometryUtil.appendWithoutDuplicate(full, startTransition);
            GeometryUtil.appendWithoutDuplicate(full, middle);
            GeometryUtil.appendWithoutDuplicate(full, endTransition);
            double designRadius = CurvatureEstimator.minRadius(full);
            return new Candidate(full, designRadius, transitionLength, Double.NaN, true, extraLoopTurns > 0);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static List<Double> transitionCandidates(double lengthMeters, boolean useSpiralTransitions) {
        List<Double> candidates = new ArrayList<>();
        if (!useSpiralTransitions) {
            candidates.add(0.0);
            return candidates;
        }

        double maxTransitionLength = Math.min(MAX_AUTO_SPIRAL_LENGTH_METERS, lengthMeters * 0.25);
        if (maxTransitionLength < 1.0) {
            candidates.add(0.0);
            return candidates;
        }

        for (int i = 1; i <= TRANSITION_CANDIDATE_COUNT; i++) {
            candidates.add(maxTransitionLength * i / TRANSITION_CANDIDATE_COUNT);
        }
        return candidates;
    }

    private static boolean isReasonablePath(List<EastNorth> points, double directLengthMeters) {
        if (points == null || points.size() < 2 || directLengthMeters < 1.0) {
            return false;
        }
        double length = 0.0;
        for (int i = 1; i < points.size(); i++) {
            EastNorth previous = points.get(i - 1);
            EastNorth current = points.get(i);
            if (previous == null || current == null || !previous.isValid() || !current.isValid()) {
                return false;
            }
            length += previous.distance(current);
        }
        return length <= directLengthMeters * 4.0;
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

    private static final class Candidate {
        private final List<EastNorth> points;
        private final double designRadiusMeters;
        private final double transitionLengthMeters;
        private final double handleScale;
        private final boolean insertedStraight;
        private final boolean insertedLoop;

        private Candidate(
                List<EastNorth> points,
                double designRadiusMeters,
                double transitionLengthMeters,
                double handleScale,
                boolean insertedStraight) {
            this(points, designRadiusMeters, transitionLengthMeters, handleScale, insertedStraight, false);
        }

        private Candidate(
                List<EastNorth> points,
                double designRadiusMeters,
                double transitionLengthMeters,
                double handleScale,
                boolean insertedStraight,
                boolean insertedLoop) {
            this.points = points;
            this.designRadiusMeters = designRadiusMeters;
            this.transitionLengthMeters = transitionLengthMeters;
            this.handleScale = handleScale;
            this.insertedStraight = insertedStraight;
            this.insertedLoop = insertedLoop;
        }

        private double score() {
            double radiusScore = Double.isFinite(designRadiusMeters) ? designRadiusMeters : Double.MAX_VALUE / 4.0;
            return insertedLoop ? Double.MAX_VALUE / 8.0 + radiusScore : radiusScore;
        }
    }

    public static final class Result {
        private final List<EastNorth> points;
        private final double designRadiusMeters;
        private final double transitionLengthMeters;
        private final double handleScale;
        private final boolean insertedStraight;
        private final boolean insertedLoop;
        private final boolean sourceWayDirection;

        private Result(
                List<EastNorth> points,
                double designRadiusMeters,
                double transitionLengthMeters,
                double handleScale,
                boolean insertedStraight,
                boolean insertedLoop,
                boolean sourceWayDirection) {
            this.points = Collections.unmodifiableList(new ArrayList<>(points));
            this.designRadiusMeters = designRadiusMeters;
            this.transitionLengthMeters = transitionLengthMeters;
            this.handleScale = handleScale;
            this.insertedStraight = insertedStraight;
            this.insertedLoop = insertedLoop;
            this.sourceWayDirection = sourceWayDirection;
        }

        public List<EastNorth> getPoints() {
            return points;
        }

        public double getDesignRadiusMeters() {
            return designRadiusMeters;
        }

        public double getTransitionLengthMeters() {
            return transitionLengthMeters;
        }

        public double getHandleScale() {
            return handleScale;
        }

        public boolean hasInsertedStraight() {
            return insertedStraight;
        }

        public boolean hasInsertedLoop() {
            return insertedLoop;
        }

        public boolean usesSourceWayDirection() {
            return sourceWayDirection;
        }
    }
}
