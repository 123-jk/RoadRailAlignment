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
        piSpiralArcKeepsEndpoints();
        piSpiralArcCanStopAtCurveEnd();
        piSpiralArcDoesNotExceedCircularCurvature();
        largeSweepArcSupportsMoreThanHalfTurn();
        largeSweepArcSupportsExtraLoop();
        singleTieRampKeepsEndpoints();
        compoundSingleTieRampKeepsEndpoints();
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
        controllerCanSetExtraLoopTurns();
        controllerCanSetTieInDirectionMode();
        controllerCanRemoveLastControlPoint();
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

    private static void compoundSingleTieRampKeepsEndpoints() {
        TieInPoint tieIn = new TieInPoint(new EastNorth(0, 0), new Vector2D(1, 0), null, 0, 0,
                0, 500, 1.0 / 500.0);
        EastNorth target = new EastNorth(140, 60);
        List<EastNorth> points = CompoundRampSampler.sampleSingleTieRamp(tieIn, target, 40, 30, 10);
        assertPoint(points.get(0), tieIn.getPoint(), "compound single tie ramp start");
        assertPoint(points.get(points.size() - 1), target, "compound single tie ramp end");
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
}
