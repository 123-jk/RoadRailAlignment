package org.openstreetmap.josm.plugins.roadrailalignment.geometry;

import java.util.List;
import java.util.Map;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.plugins.roadrailalignment.AlignmentController;
import org.openstreetmap.josm.plugins.roadrailalignment.AlignmentMode;
import org.openstreetmap.josm.plugins.roadrailalignment.FeatureType;
import org.openstreetmap.josm.plugins.roadrailalignment.TieInDirectionMode;
import org.openstreetmap.josm.plugins.roadrailalignment.model.TieInPoint;

public final class GeometrySmokeTest {
    private GeometrySmokeTest() {
    }

    public static void main(String[] args) {
        lineSamplerKeepsEndpoints();
        piArcKeepsEndpoints();
        piArcCanStopAtCurveEnd();
        piArcUsesTurnAngleNotInteriorAngle();
        piArcEndTangentIsAnalyticForShallowCurve();
        piSpiralArcKeepsEndpoints();
        piSpiralArcCanStopAtCurveEnd();
        piSpiralArcEndTangentIsAnalyticForShallowCurve();
        piSpiralArcDoesNotExceedCircularCurvature();
        largeSweepArcSupportsMoreThanHalfTurn();
        largeSweepArcSupportsExtraLoop();
        singleTieRampKeepsEndpoints();
        singleTieRampCanKeepTieInDirectionForUTurn();
        compoundSingleTieRampKeepsEndpoints();
        compoundSingleTieRampExitTransitionConnectsSmoothly();
        compoundSingleTieRampExitTailAlignsSmoothly();
        compoundSingleTieRampCanStartFromStoredCurvature();
        compoundSingleTieRampLeavesTowardTargetSide();
        twoTieRampKeepsEndpoints();
        twoTieRampCanUseReverseSourceDirection();
        compoundTwoTieRampKeepsEndpoints();
        straightInsertTwoTieRampKeepsEndpointsAndRadius();
        straightInsertTwoTieRampSupportsExtraLoop();
        optimizedTwoTieRampKeepsEndpointsAndFindsRadius();
        optimizedTwoTieRampCanInsertStraight();
        optimizedTwoTieRampCanForceLoop();
        optimizedTwoTieRampReportsSourceWayDirection();
        optimizedTwoTieRampCapsTightSourceCurvature();
        radiusFormatterSeparatesRoundedBelowMinimum();
        transitionSpiralProducesForwardPoints();
        recommendedSpiralLengthFollowsRadius();
        transitionSpiralModeIsHiddenFromMainUi();
        featureTypePresetTagsCanWriteMultipleTags();
        controllerCanToggleOptimizedTwoTieRampParameterBackfill();
        controllerCanToggleContinuousRampCurvature();
        controllerCanSetExtraLoopTurns();
        controllerCanSetTieInDirectionMode();
        controllerCanRemoveLastControlPoint();
        controllerCanPromoteShallowSameDirectionCurve();
        excessiveSamplingIsRejected();
        System.out.println("Geometry smoke tests passed.");
    }

    private static void lineSamplerKeepsEndpoints() {
        EastNorth start = new EastNorth(0, 0);
        EastNorth end = new EastNorth(100, 0);
        List<EastNorth> points = LineSampler.sample(start, end, 10);
        assertPoint(points.get(0), start, "line start");
        assertPoint(points.get(points.size() - 1), end, "line end");
    }

    private static void piArcKeepsEndpoints() {
        EastNorth start = new EastNorth(0, 0);
        EastNorth pi = new EastNorth(100, 0);
        EastNorth end = new EastNorth(100, 100);
        List<EastNorth> points = PiArcSampler.sample(start, pi, end, 50, 10);
        assertPoint(points.get(0), start, "PI arc start");
        assertPoint(points.get(points.size() - 1), end, "PI arc end");
    }

    private static void piArcCanStopAtCurveEnd() {
        EastNorth start = new EastNorth(0, 0);
        EastNorth pi = new EastNorth(100, 0);
        EastNorth end = new EastNorth(100, 100);
        List<EastNorth> points = PiArcSampler.sample(start, pi, end, 50, 10, false);
        assertPoint(points.get(0), start, "PI arc truncated start");
        assertPoint(points.get(points.size() - 1), new EastNorth(100, 50), "PI arc truncated end");
    }

    private static void piArcUsesTurnAngleNotInteriorAngle() {
        EastNorth start = new EastNorth(0, 0);
        EastNorth pi = new EastNorth(1000, 0);
        EastNorth end = new EastNorth(
                1000 + Math.cos(Math.toRadians(30)) * 1000,
                Math.sin(Math.toRadians(30)) * 1000);
        List<EastNorth> points = PiArcSampler.sample(start, pi, end, 100, 10);
        double minDistanceToPi = Double.POSITIVE_INFINITY;
        for (EastNorth point : points) {
            minDistanceToPi = Math.min(minDistanceToPi, point.distance(pi));
        }
        if (minDistanceToPi > 60) {
            throw new AssertionError("PI arc used the intersection interior angle instead of the actual turn angle");
        }
    }

    private static void piArcEndTangentIsAnalyticForShallowCurve() {
        EastNorth start = new EastNorth(0, 0);
        EastNorth pi = new EastNorth(1000, 0);
        EastNorth end = new EastNorth(
                1000 + Math.cos(Math.toRadians(5)) * 1000,
                Math.sin(Math.toRadians(5)) * 1000);
        List<EastNorth> points = PiArcSampler.sample(start, pi, end, 200, 1000, false);
        Vector2D sampledChordTangent = Vector2D.between(
                points.get(points.size() - 2),
                points.get(points.size() - 1)).normalize();
        Vector2D analyticTangent = PiArcSampler.endTangent(start, pi, end, 200);
        Vector2D expectedTangent = Vector2D.between(pi, end).normalize();

        assertAngle(analyticTangent, expectedTangent, 1e-9, "PI arc analytic end tangent");
        if (angleBetween(sampledChordTangent, expectedTangent) > Math.toRadians(0.5)) {
            throw new AssertionError("shallow PI arc end chord should stay close to the outgoing tangent");
        }
    }

    private static void piSpiralArcKeepsEndpoints() {
        EastNorth start = new EastNorth(0, 0);
        EastNorth pi = new EastNorth(200, 0);
        EastNorth end = new EastNorth(200, 200);
        List<EastNorth> points = PiSpiralArcSampler.sample(start, pi, end, 80, 40, 10);
        assertPoint(points.get(0), start, "PI spiral arc start");
        assertPoint(points.get(points.size() - 1), end, "PI spiral arc end");
    }

    private static void piSpiralArcCanStopAtCurveEnd() {
        EastNorth start = new EastNorth(0, 0);
        EastNorth pi = new EastNorth(200, 0);
        EastNorth end = new EastNorth(200, 200);
        List<EastNorth> points = PiSpiralArcSampler.sample(start, pi, end, 80, 40, 10, false);
        assertPoint(points.get(0), start, "PI spiral arc truncated start");
        if (points.get(points.size() - 1).distance(end) < 20) {
            throw new AssertionError("PI spiral arc should stop at curve end before the exit tangent");
        }
    }

    private static void piSpiralArcEndTangentIsAnalyticForShallowCurve() {
        EastNorth start = new EastNorth(0, 0);
        EastNorth pi = new EastNorth(1000, 0);
        EastNorth end = new EastNorth(
                1000 + Math.cos(Math.toRadians(5)) * 1000,
                Math.sin(Math.toRadians(5)) * 1000);
        Vector2D analyticTangent = PiSpiralArcSampler.endTangent(start, pi, end, 200, 10);
        Vector2D expectedTangent = Vector2D.between(pi, end).normalize();
        assertAngle(analyticTangent, expectedTangent, 1e-9, "PI spiral arc analytic end tangent");
    }

    private static void piSpiralArcDoesNotExceedCircularCurvature() {
        double radius = 80.0;
        EastNorth start = new EastNorth(0, 0);
        EastNorth pi = new EastNorth(200, 0);
        EastNorth end = new EastNorth(200, 200);
        List<EastNorth> points = PiSpiralArcSampler.sample(start, pi, end, radius, 40, 10);
        double minRadius = CurvatureEstimator.minRadius(points);
        if (minRadius < radius - 1e-6) {
            throw new AssertionError("PI spiral arc curvature exceeded circular curvature: min radius="
                    + minRadius + ", circular radius=" + radius);
        }
    }

    private static void largeSweepArcSupportsMoreThanHalfTurn() {
        EastNorth start = new EastNorth(0, 0);
        EastNorth tangentGuide = new EastNorth(100, 0);
        EastNorth endGuide = new EastNorth(-50, 50);
        List<EastNorth> points = LargeSweepArcSampler.sample(start, tangentGuide, endGuide, 50, 0, 0, 10);
        assertPoint(points.get(0), start, "large sweep arc start");
        assertPoint(points.get(points.size() - 1), endGuide, "large sweep arc 270 degree end");
        if (pathLength(points) < Math.toRadians(260.0) * 50.0) {
            throw new AssertionError("large sweep arc should support sweeps greater than 180 degrees");
        }
    }

    private static void largeSweepArcSupportsExtraLoop() {
        EastNorth start = new EastNorth(0, 0);
        EastNorth tangentGuide = new EastNorth(100, 0);
        EastNorth endGuide = new EastNorth(50, 50);
        List<EastNorth> points = LargeSweepArcSampler.sample(start, tangentGuide, endGuide, 50, 1, 0, 10);
        assertPoint(points.get(0), start, "large sweep loop start");
        assertPoint(points.get(points.size() - 1), endGuide, "large sweep loop end");
        if (pathLength(points) < Math.toRadians(430.0) * 50.0) {
            throw new AssertionError("large sweep arc should include the requested extra full loop");
        }
    }

    private static void singleTieRampKeepsEndpoints() {
        TieInPoint tieIn = new TieInPoint(new EastNorth(0, 0), new Vector2D(1, 0), null, 0, 0);
        EastNorth target = new EastNorth(100, 50);
        List<EastNorth> points = RampArcSampler.sample(tieIn, target, 20, 10);
        assertPoint(points.get(0), tieIn.getPoint(), "single tie ramp start");
        assertPoint(points.get(points.size() - 1), target, "single tie ramp end");
    }

    private static void singleTieRampCanKeepTieInDirectionForUTurn() {
        TieInPoint tieIn = new TieInPoint(new EastNorth(0, 0), new Vector2D(1, 0), null, -1, 0);
        EastNorth targetBehind = new EastNorth(-100, 50);
        List<EastNorth> points = RampArcSampler.sample(tieIn, targetBehind, 20, 10, true);
        assertPoint(points.get(0), tieIn.getPoint(), "single tie u-turn ramp start");
        assertPoint(points.get(points.size() - 1), targetBehind, "single tie u-turn ramp end");
        if (points.size() < 2 || points.get(1).east() <= tieIn.getPoint().east()) {
            throw new AssertionError("continuous ramp should leave along the retained tangent before turning back");
        }
    }

    private static void compoundSingleTieRampKeepsEndpoints() {
        TieInPoint tieIn = new TieInPoint(new EastNorth(0, 0), new Vector2D(1, 0), null, 0, 0,
                0, 500, 1.0 / 500.0);
        EastNorth target = new EastNorth(140, 60);
        List<EastNorth> points = CompoundRampSampler.sampleSingleTieRamp(tieIn, target, 40, 30, 10);
        assertPoint(points.get(0), tieIn.getPoint(), "compound single tie ramp start");
        assertPoint(points.get(points.size() - 1), target, "compound single tie ramp end");
        if (Math.abs(endSignedCurvature(points)) > 0.002) {
            throw new AssertionError("compound single tie ramp should ease out close to zero curvature");
        }
    }

    private static void compoundSingleTieRampExitTransitionConnectsSmoothly() {
        TieInPoint tieIn = new TieInPoint(new EastNorth(0, 0), new Vector2D(1, 0), null, 0, 0,
                0, Double.NaN, 0.0);
        EastNorth target = new EastNorth(90, 90);
        List<EastNorth> points = CompoundRampSampler.sampleSingleTieRamp(tieIn, target, 20, 45, 2);
        assertPoint(points.get(0), tieIn.getPoint(), "compound exit transition start");
        assertPoint(points.get(points.size() - 1), target, "compound exit transition end");
        if (maxSegmentLength(points) > 3.0) {
            throw new AssertionError("exit transition should not jump away from the circular arc");
        }
        if (maxHeadingChange(points) > 0.35) {
            throw new AssertionError("exit transition should connect smoothly to the circular arc");
        }
    }

    private static void compoundSingleTieRampExitTailAlignsSmoothly() {
        TieInPoint tieIn = new TieInPoint(new EastNorth(0, 0), new Vector2D(1, 0), null, 0, 0,
                0, Double.NaN, 0.0);
        EastNorth target = new EastNorth(140, 25);
        List<EastNorth> points = CompoundRampSampler.sampleSingleTieRamp(tieIn, target, 20, 45, 5);
        assertPoint(points.get(points.size() - 1), target, "compound exit tail end");
        if (tailHeadingChange(points) > Math.toRadians(5.0)) {
            throw new AssertionError("exit transition tail should align smoothly with the final straight segment");
        }
    }

    private static void compoundSingleTieRampCanStartFromStoredCurvature() {
        EastNorth start = new EastNorth(0, 0);
        EastNorth target = new EastNorth(120, 120);
        TieInPoint straightStart = new TieInPoint(start, new Vector2D(1, 0), null, -1, 0,
                0, Double.NaN, 0.0);
        TieInPoint curvedStart = new TieInPoint(start, new Vector2D(1, 0), null, -1, 0,
                0, 120, -1.0 / 120.0);

        List<EastNorth> fromStraight = CompoundRampSampler.sampleSingleTieRamp(
                straightStart, target, 20, 40, 5, true);
        List<EastNorth> fromStoredCurvature = CompoundRampSampler.sampleSingleTieRamp(
                curvedStart, target, 20, 40, 5, true, true);

        if (fromStraight.size() < 3 || fromStoredCurvature.size() < 3) {
            throw new AssertionError("compound continuous ramp should produce sampled points");
        }
        if (fromStoredCurvature.get(1).north() >= fromStraight.get(1).north()) {
            throw new AssertionError("continuous ramp should reuse stored opposite curvature at the next segment start");
        }
        if (Math.abs(endSignedCurvature(fromStoredCurvature)) < 0.002) {
            throw new AssertionError("continuous ramp should keep exit curvature when requested");
        }
    }

    private static void compoundSingleTieRampLeavesTowardTargetSide() {
        TieInPoint tieIn = new TieInPoint(new EastNorth(0, 0), new Vector2D(1, 0), null, 0, 0,
                0, 500, 1.0 / 500.0);
        EastNorth target = new EastNorth(140, -60);
        List<EastNorth> points = CompoundRampSampler.sampleSingleTieRamp(tieIn, target, 40, 30, 5);
        if (points.size() < 3 || points.get(1).north() >= tieIn.getPoint().north()) {
            throw new AssertionError("compound single tie ramp should leave toward the target side");
        }
    }

    private static void twoTieRampKeepsEndpoints() {
        TieInPoint startTieIn = new TieInPoint(new EastNorth(0, 0), new Vector2D(1, 0), null, 0, 0);
        TieInPoint endTieIn = new TieInPoint(new EastNorth(150, 80), new Vector2D(1, 0), null, 0, 0);
        List<EastNorth> points = HermiteRampSampler.sample(startTieIn, endTieIn, 15, 10);
        assertPoint(points.get(0), startTieIn.getPoint(), "two tie ramp start");
        assertPoint(points.get(points.size() - 1), endTieIn.getPoint(), "two tie ramp end");
    }

    private static void twoTieRampCanUseReverseSourceDirection() {
        TieInPoint startTieIn = new TieInPoint(new EastNorth(0, 0), new Vector2D(1, 0), null, 0, 0);
        TieInPoint endTieIn = new TieInPoint(new EastNorth(100, 0), new Vector2D(1, 0), null, 0, 0);
        List<EastNorth> points = HermiteRampSampler.sample(
                startTieIn,
                endTieIn,
                1,
                TieInDirectionMode.REVERSE_SOURCE_WAY,
                10);
        assertPoint(points.get(0), startTieIn.getPoint(), "reverse source direction start");
        assertPoint(points.get(points.size() - 1), endTieIn.getPoint(), "reverse source direction end");
        if (points.size() < 2 || points.get(1).east() >= startTieIn.getPoint().east()) {
            throw new AssertionError("reverse source direction should leave opposite to the source-way tangent");
        }
    }

    private static void compoundTwoTieRampKeepsEndpoints() {
        TieInPoint startTieIn = new TieInPoint(new EastNorth(0, 0), new Vector2D(1, 0), null, 0, 0,
                0, 800, 1.0 / 800.0);
        TieInPoint endTieIn = new TieInPoint(new EastNorth(180, 90), new Vector2D(1, 0), null, 0, 0,
                0, 600, 1.0 / 600.0);
        List<EastNorth> points = CompoundRampSampler.sampleTwoTieRamp(startTieIn, endTieIn, 20, 25, 10);
        assertPoint(points.get(0), startTieIn.getPoint(), "compound two tie ramp start");
        assertPoint(points.get(points.size() - 1), endTieIn.getPoint(), "compound two tie ramp end");
    }

    private static void straightInsertTwoTieRampKeepsEndpointsAndRadius() {
        double radius = 60.0;
        TieInPoint startTieIn = new TieInPoint(new EastNorth(0, 0), new Vector2D(1, 0), null, 0, 0);
        TieInPoint endTieIn = new TieInPoint(new EastNorth(80, 80), new Vector2D(-1, 0), null, 0, 0);
        List<EastNorth> points = StraightInsertRampSampler.sample(startTieIn, endTieIn, radius, 10);
        assertPoint(points.get(0), startTieIn.getPoint(), "straight insert two tie ramp start");
        assertPoint(points.get(points.size() - 1), endTieIn.getPoint(), "straight insert two tie ramp end");
        double minRadius = CurvatureEstimator.minRadius(points);
        if (minRadius < radius - 1e-6) {
            throw new AssertionError("straight insert two tie ramp should honor the requested radius");
        }
    }

    private static void straightInsertTwoTieRampSupportsExtraLoop() {
        double radius = 40.0;
        TieInPoint startTieIn = new TieInPoint(new EastNorth(0, 0), new Vector2D(1, 0), null, 0, 0);
        TieInPoint endTieIn = new TieInPoint(new EastNorth(150, 80), new Vector2D(1, 0), null, 0, 0);
        List<EastNorth> plain = StraightInsertRampSampler.sample(startTieIn, endTieIn, radius, 10);
        List<EastNorth> looped = StraightInsertRampSampler.sample(startTieIn, endTieIn, radius, 1, 10);
        assertPoint(looped.get(0), startTieIn.getPoint(), "looped straight insert start");
        assertPoint(looped.get(looped.size() - 1), endTieIn.getPoint(), "looped straight insert end");
        if (pathLength(looped) <= pathLength(plain) + Math.PI * radius) {
            throw new AssertionError("looped straight insert should add a visible full-turn loop");
        }
    }

    private static void optimizedTwoTieRampKeepsEndpointsAndFindsRadius() {
        TieInPoint startTieIn = new TieInPoint(new EastNorth(0, 0), new Vector2D(1, 0), null, 0, 0);
        TieInPoint endTieIn = new TieInPoint(new EastNorth(220, 90), new Vector2D(1, 0), null, 0, 0);
        OptimizedTwoTieRampSampler.Result result = OptimizedTwoTieRampSampler.sample(
                startTieIn,
                endTieIn,
                10,
                true,
                10);
        List<EastNorth> points = result.getPoints();
        assertPoint(points.get(0), startTieIn.getPoint(), "optimized two tie ramp start");
        assertPoint(points.get(points.size() - 1), endTieIn.getPoint(), "optimized two tie ramp end");
        if (!Double.isFinite(result.getDesignRadiusMeters()) || result.getDesignRadiusMeters() <= 10) {
            throw new AssertionError("optimized two tie ramp should find a usable radius");
        }
        if (result.getTransitionLengthMeters() <= 0.0) {
            throw new AssertionError("optimized two tie ramp should choose a transition length when spirals are enabled");
        }
    }

    private static void optimizedTwoTieRampCanInsertStraight() {
        double radius = 60.0;
        TieInPoint startTieIn = new TieInPoint(new EastNorth(0, 0), new Vector2D(1, 0), null, 0, 0);
        TieInPoint endTieIn = new TieInPoint(new EastNorth(80, 80), new Vector2D(-1, 0), null, 0, 0);
        OptimizedTwoTieRampSampler.Result result = OptimizedTwoTieRampSampler.sample(
                startTieIn,
                endTieIn,
                radius,
                true,
                10);
        if (!result.hasInsertedStraight()) {
            throw new AssertionError("optimized two tie ramp should insert a straight segment when it is needed");
        }
        double minRadius = CurvatureEstimator.minRadius(result.getPoints());
        if (minRadius < radius - 1e-6) {
            throw new AssertionError("optimized straight-insert two tie ramp should honor the requested radius");
        }
    }

    private static void optimizedTwoTieRampCanForceLoop() {
        TieInPoint startTieIn = new TieInPoint(new EastNorth(0, 0), new Vector2D(1, 0), null, 0, 0);
        TieInPoint endTieIn = new TieInPoint(new EastNorth(220, 90), new Vector2D(1, 0), null, 0, 0);
        OptimizedTwoTieRampSampler.Result result = OptimizedTwoTieRampSampler.sample(
                startTieIn,
                endTieIn,
                40,
                true,
                1,
                10);
        assertPoint(result.getPoints().get(0), startTieIn.getPoint(), "optimized loop start");
        assertPoint(result.getPoints().get(result.getPoints().size() - 1), endTieIn.getPoint(), "optimized loop end");
        if (!result.hasInsertedLoop()) {
            throw new AssertionError("optimized two tie ramp should honor requested extra loop turns");
        }
    }

    private static void optimizedTwoTieRampReportsSourceWayDirection() {
        TieInPoint startTieIn = new TieInPoint(
                new EastNorth(0, 0),
                new Vector2D(1, 0),
                null,
                0,
                0,
                0,
                Double.NaN,
                0);
        TieInPoint endTieIn = new TieInPoint(
                new EastNorth(220, 90),
                new Vector2D(1, 0),
                null,
                1,
                0,
                250,
                Double.NaN,
                0);
        OptimizedTwoTieRampSampler.Result result = OptimizedTwoTieRampSampler.sample(
                startTieIn,
                endTieIn,
                40,
                true,
                0,
                TieInDirectionMode.SOURCE_WAY,
                10);
        if (!result.usesSourceWayDirection()) {
            throw new AssertionError("optimized two tie ramp should report source-way direction mode");
        }
    }

    private static void optimizedTwoTieRampCapsTightSourceCurvature() {
        TieInPoint startTieIn = new TieInPoint(
                new EastNorth(0, 0),
                new Vector2D(1, 0),
                null,
                0,
                0,
                0,
                5,
                1.0 / 5.0);
        TieInPoint endTieIn = new TieInPoint(
                new EastNorth(220, 90),
                new Vector2D(1, 0),
                null,
                0,
                0,
                0,
                5,
                1.0 / 5.0);
        OptimizedTwoTieRampSampler.Result result = OptimizedTwoTieRampSampler.sample(
                startTieIn,
                endTieIn,
                40,
                true,
                10);
        double minRadius = CurvatureEstimator.minRadius(result.getPoints());
        if (minRadius < 40.0 - 1e-6) {
            throw new AssertionError("optimized two tie ramp should cap transition curvature to the requested minimum radius");
        }
    }

    private static void radiusFormatterSeparatesRoundedBelowMinimum() {
        String measuredRadius = RadiusFormatter.formatMetersBelowThreshold(1199.6, 1200.0);
        String requiredRadius = RadiusFormatter.formatThresholdMeters(1200.0, 1199.6);
        if (measuredRadius.equals(requiredRadius)) {
            throw new AssertionError("radius formatter should not hide a below-minimum radius");
        }
        if (!"1199.6".equals(measuredRadius) || !"1200".equals(requiredRadius)) {
            throw new AssertionError("unexpected radius comparison text: "
                    + measuredRadius + " vs " + requiredRadius);
        }
    }

    private static void transitionSpiralProducesForwardPoints() {
        EastNorth start = new EastNorth(0, 0);
        EastNorth tangentGuide = new EastNorth(100, 0);
        EastNorth sideGuide = new EastNorth(20, 20);
        List<EastNorth> points = SpiralSampler.sampleTransition(start, tangentGuide, sideGuide, 300, 10);
        assertPoint(points.get(0), start, "spiral start");
        if (points.get(points.size() - 1).east() <= start.east()) {
            throw new AssertionError("spiral should advance in the tangent direction");
        }
    }

    private static void recommendedSpiralLengthFollowsRadius() {
        double recommended = AlignmentController.recommendSpiralLengthMeters(200);
        if (Math.abs(recommended - 60) > 1e-6) {
            throw new AssertionError("R=200 should recommend Ls=60, got " + recommended);
        }
    }

    private static void transitionSpiralModeIsHiddenFromMainUi() {
        for (AlignmentMode mode : AlignmentMode.userVisibleValues()) {
            if (mode == AlignmentMode.TRANSITION_SPIRAL) {
                throw new AssertionError("transition spiral approximation should not be shown as a main mode");
            }
        }
    }

    private static void featureTypePresetTagsCanWriteMultipleTags() {
        Map<String, String> motorwayTags = FeatureType.MOTORWAY_LINK.getTags();
        if (!"motorway_link".equals(motorwayTags.get("highway"))) {
            throw new AssertionError("motorway link preset should write highway=motorway_link");
        }

        Map<String, String> highSpeedRailTags = FeatureType.HIGH_SPEED_RAIL.getTags();
        if (!"rail".equals(highSpeedRailTags.get("railway"))
                || !"yes".equals(highSpeedRailTags.get("highspeed"))) {
            throw new AssertionError("high-speed rail preset should write railway=rail + highspeed=yes");
        }
    }

    private static void controllerCanToggleOptimizedTwoTieRampParameterBackfill() {
        AlignmentController controller = new AlignmentController();
        controller.setApplyOptimizedTwoTieRampParameters(false);
        if (controller.isApplyOptimizedTwoTieRampParameters()) {
            throw new AssertionError("controller should allow disabling optimized parameter backfill");
        }
        controller.setApplyOptimizedTwoTieRampParameters(true);
        if (!controller.isApplyOptimizedTwoTieRampParameters()) {
            throw new AssertionError("controller should allow enabling optimized parameter backfill");
        }
    }

    private static void controllerCanToggleContinuousRampCurvature() {
        AlignmentController controller = new AlignmentController();
        if (controller.isContinuousRampCurvature()) {
            throw new AssertionError("controller should not keep ramp curvature by default");
        }
        controller.setContinuousRampCurvature(true);
        if (!controller.isContinuousRampCurvature()) {
            throw new AssertionError("controller should store continuous ramp curvature mode");
        }
        controller.setContinuousRampCurvature(false);
        if (controller.isContinuousRampCurvature()) {
            throw new AssertionError("controller should disable continuous ramp curvature mode");
        }
    }

    private static void controllerCanSetExtraLoopTurns() {
        AlignmentController controller = new AlignmentController();
        controller.setExtraLoopTurns(2);
        if (controller.getExtraLoopTurns() != 2) {
            throw new AssertionError("controller should store extra loop turns");
        }
        controller.setExtraLoopTurns(-1);
        if (controller.getExtraLoopTurns() != 0) {
            throw new AssertionError("controller should clamp negative extra loop turns");
        }
    }

    private static void controllerCanSetTieInDirectionMode() {
        AlignmentController controller = new AlignmentController();
        controller.setTieInDirectionMode(TieInDirectionMode.SOURCE_WAY);
        if (controller.getTieInDirectionMode() != TieInDirectionMode.SOURCE_WAY) {
            throw new AssertionError("controller should store tie-in direction mode");
        }
        controller.setTieInDirectionMode(null);
        if (controller.getTieInDirectionMode() != TieInDirectionMode.AUTO_CHORD) {
            throw new AssertionError("controller should fall back to automatic tie-in direction mode");
        }
    }

    private static void controllerCanRemoveLastControlPoint() {
        AlignmentController controller = new AlignmentController();
        controller.addControlPoint(new EastNorth(0, 0));
        controller.addControlPoint(new EastNorth(10, 0));
        if (!controller.removeLastControlPoint() || controller.getControlPointCount() != 1) {
            throw new AssertionError("controller should remove the last control point");
        }
        if (!controller.removeLastControlPoint() || controller.getControlPointCount() != 0) {
            throw new AssertionError("controller should remove the remaining control point");
        }
        if (controller.removeLastControlPoint()) {
            throw new AssertionError("controller should report no-op when there are no control points");
        }
    }

    private static void controllerCanPromoteShallowSameDirectionCurve() {
        EastNorth start = new EastNorth(0, 0);
        EastNorth firstTarget = new EastNorth(120, 30);
        TieInPoint firstTie = new TieInPoint(start, new Vector2D(1, 0), null, -1, 0,
                0, Double.NaN, 0.0);
        List<EastNorth> firstNoExit = CompoundRampSampler.sampleSingleTieRamp(
                firstTie, firstTarget, 20, 40, 5, true, true);
        Vector2D tangent = endTangent(firstNoExit);
        double curvature = endSignedCurvature(firstNoExit);
        EastNorth shallowTarget = tangent.pointFrom(firstTarget, 80);
        shallowTarget = shallowTarget.add(tangent.leftNormal().scale(2.0).x(), tangent.leftNormal().scale(2.0).y());
        double nextCurvature = RampArcSampler.signedCurvatureFromTangent(firstTarget, tangent, shallowTarget, true);

        if (Math.abs(Vector2D.between(firstTarget, shallowTarget).cross(tangent.normalize())) >= 8.0) {
            throw new AssertionError("test setup should stay below the default node snap tolerance");
        }
        if (Math.signum(curvature) == 0.0 || Math.signum(curvature) != Math.signum(nextCurvature)) {
            throw new AssertionError("shallow target should be a same-direction continuation");
        }
    }

    private static Vector2D endTangent(List<EastNorth> points) {
        EastNorth end = points.get(points.size() - 1);
        for (int i = points.size() - 2; i >= 0; i--) {
            EastNorth previous = points.get(i);
            if (previous.distance(end) > 0.001) {
                return Vector2D.between(previous, end).normalize();
            }
        }
        throw new AssertionError("points should have a usable end tangent");
    }

    private static void excessiveSamplingIsRejected() {
        try {
            LineSampler.sample(new EastNorth(0, 0), new EastNorth(GeometryUtil.MAX_SAMPLE_POINTS + 10, 0), 1);
            throw new AssertionError("excessive straight sampling should be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void assertPoint(EastNorth actual, EastNorth expected, String label) {
        double distance = actual.distance(expected);
        if (distance > 1e-6) {
            throw new AssertionError(label + " mismatch: " + actual + " != " + expected + ", distance=" + distance);
        }
    }

    private static double pathLength(List<EastNorth> points) {
        double length = 0.0;
        for (int i = 1; i < points.size(); i++) {
            length += points.get(i - 1).distance(points.get(i));
        }
        return length;
    }

    private static double maxSegmentLength(List<EastNorth> points) {
        double max = 0.0;
        for (int i = 1; i < points.size(); i++) {
            max = Math.max(max, points.get(i - 1).distance(points.get(i)));
        }
        return max;
    }

    private static double maxHeadingChange(List<EastNorth> points) {
        double max = 0.0;
        Vector2D previous = null;
        for (int i = 1; i < points.size(); i++) {
            EastNorth first = points.get(i - 1);
            EastNorth second = points.get(i);
            if (first.distance(second) <= 0.001) {
                continue;
            }
            Vector2D current = Vector2D.between(first, second).normalize();
            if (previous != null) {
                double cross = previous.cross(current);
                double dot = previous.dot(current);
                max = Math.max(max, Math.abs(Math.atan2(cross, dot)));
            }
            previous = current;
        }
        return max;
    }

    private static double tailHeadingChange(List<EastNorth> points) {
        if (points == null || points.size() < 3) {
            return 0.0;
        }
        Vector2D previous = null;
        for (int i = points.size() - 1; i > 0; i--) {
            EastNorth first = points.get(i - 1);
            EastNorth second = points.get(i);
            if (first.distance(second) > 0.001) {
                Vector2D current = Vector2D.between(first, second).normalize();
                if (previous != null) {
                    return angleBetween(current, previous);
                }
                previous = current;
            }
        }
        return 0.0;
    }

    private static void assertAngle(Vector2D actual, Vector2D expected, double toleranceRadians, String label) {
        double angle = angleBetween(actual, expected);
        if (angle > toleranceRadians) {
            throw new AssertionError(label + " angle mismatch: " + angle);
        }
    }

    private static double angleBetween(Vector2D first, Vector2D second) {
        double cross = first.cross(second);
        double dot = first.dot(second);
        return Math.abs(Math.atan2(cross, dot));
    }

    private static double endSignedCurvature(List<EastNorth> points) {
        if (points == null || points.size() < 3) {
            return 0.0;
        }
        EastNorth first = points.get(points.size() - 3);
        EastNorth second = points.get(points.size() - 2);
        EastNorth third = points.get(points.size() - 1);
        double radius = CurvatureEstimator.radiusFromThreePoints(first, second, third);
        if (!Double.isFinite(radius) || radius <= 0.0) {
            return 0.0;
        }
        double cross = Vector2D.between(first, second).cross(Vector2D.between(second, third));
        if (Math.abs(cross) < 1e-9) {
            return 0.0;
        }
        return Math.copySign(1.0 / radius, cross);
    }
}
