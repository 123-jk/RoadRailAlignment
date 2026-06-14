package org.openstreetmap.josm.plugins.roadrailalignment;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Cursor;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.plugins.roadrailalignment.geometry.CompoundRampSampler;
import org.openstreetmap.josm.plugins.roadrailalignment.geometry.CurvatureEstimator;
import org.openstreetmap.josm.plugins.roadrailalignment.geometry.HermiteRampSampler;
import org.openstreetmap.josm.plugins.roadrailalignment.geometry.LargeSweepArcSampler;
import org.openstreetmap.josm.plugins.roadrailalignment.geometry.LineSampler;
import org.openstreetmap.josm.plugins.roadrailalignment.geometry.OptimizedTwoTieRampSampler;
import org.openstreetmap.josm.plugins.roadrailalignment.geometry.PiArcSampler;
import org.openstreetmap.josm.plugins.roadrailalignment.geometry.PiSpiralArcSampler;
import org.openstreetmap.josm.plugins.roadrailalignment.geometry.RampArcSampler;
import org.openstreetmap.josm.plugins.roadrailalignment.geometry.SpiralSampler;
import org.openstreetmap.josm.plugins.roadrailalignment.geometry.StraightInsertRampSampler;
import org.openstreetmap.josm.plugins.roadrailalignment.geometry.Vector2D;
import org.openstreetmap.josm.plugins.roadrailalignment.josm.ExistingWayAnalyzer;
import org.openstreetmap.josm.plugins.roadrailalignment.josm.JosmWayBuilder;
import org.openstreetmap.josm.plugins.roadrailalignment.josm.NodeSnapper;
import org.openstreetmap.josm.plugins.roadrailalignment.josm.PreviewPainter;
import org.openstreetmap.josm.plugins.roadrailalignment.model.TieInPoint;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;

public final class AlignmentMapMode extends MapMode implements PropertyChangeListener {
    private static final int PREVIEW_COALESCE_MILLIS = 60;
    private static final long STALE_CLICK_MILLIS = 1000;
    private static final double PREVIEW_SAMPLE_INTERVAL_MULTIPLIER = 4.0;

    private final AlignmentController controller;
    private final Runnable openWindowAction;
    private final PreviewPainter previewPainter = new PreviewPainter();
    private final Timer previewTimer = new Timer(PREVIEW_COALESCE_MILLIS, event -> flushPendingPreview());
    private TieInPoint pendingStartTieIn;
    private TieInPoint pendingEndTieIn;
    private boolean pendingNewPointStart;
    private boolean currentSegmentKeepsExitCurvature;
    private EastNorth continuousAnchorPoint;
    private Vector2D continuousExtensionTangent;
    private double continuousRampCurvature;
    private GeneratedSegment previousGeneratedSegment;
    private GeneratedSegment pendingPromotedPreviousSegment;
    private List<EastNorth> lastSingleTieNoExitPoints;
    private double lastSingleTieExitCurvature;
    private boolean bidirectionalExtensionSnap;
    private boolean controllerListenerRegistered;
    private boolean generating;
    private long lastPreviewMillis;
    private EastNorth pendingPreviewCursor;
    private String lastGeneratedDetail = "";

    private final MouseAdapter mouseHandler = new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent event) {
            handleMouseClicked(event);
        }

        @Override
        public void mouseMoved(MouseEvent event) {
            handleMouseMoved(event);
        }
    };

    private final KeyAdapter keyHandler = new KeyAdapter() {
        @Override
        public void keyPressed(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                clearCurrentInput(MainApplication.getMap().mapView);
                event.consume();
            } else if (event.getKeyCode() == KeyEvent.VK_BACK_SPACE
                    || (event.getKeyCode() == KeyEvent.VK_Z && event.isControlDown())) {
                if (undoLastStep(MainApplication.getMap().mapView)) {
                    event.consume();
                }
            }
        }
    };

    public AlignmentMapMode(AlignmentController controller, Runnable openWindowAction) {
        super(
                tr("Road/Rail Alignment"),
                "roadrailalignment",
                tr("Draw road, rail, and ramp horizontal alignments"),
                Shortcut.registerShortcut(
                        "mapmode:roadrailalignment",
                        tr("Mode: {0}", tr("Road/Rail Alignment")),
                        KeyEvent.VK_R,
                        Shortcut.SHIFT),
                Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        this.controller = controller;
        this.openWindowAction = openWindowAction;
        previewTimer.setRepeats(false);
    }

    @Override
    public void enterMode() {
        super.enterMode();
        MapView mapView = MainApplication.getMap().mapView;
        if (!controllerListenerRegistered) {
            controller.addPropertyChangeListener(this);
            controllerListenerRegistered = true;
        }
        mapView.addMouseListener(mouseHandler);
        mapView.addMouseMotionListener(mouseHandler);
        mapView.addKeyListener(keyHandler);
        mapView.addTemporaryLayer(previewPainter);
        mapView.setFocusable(true);
        mapView.requestFocusInWindow();
        controller.setStatusMessage(tr("Click control points to generate an alignment."));
        if (openWindowAction != null) {
            SwingUtilities.invokeLater(openWindowAction);
        }
    }

    @Override
    public void exitMode() {
        MapView mapView = MainApplication.getMap().mapView;
        if (controllerListenerRegistered) {
            controller.removePropertyChangeListener(this);
            controllerListenerRegistered = false;
        }
        mapView.removeMouseListener(mouseHandler);
        mapView.removeMouseMotionListener(mouseHandler);
        mapView.removeKeyListener(keyHandler);
        mapView.removeTemporaryLayer(previewPainter);
        previewTimer.stop();
        pendingPreviewCursor = null;
        previewPainter.clear();
        clearPendingState();
        clearContinuousExtension();
        controller.clearControlPoints();
        controller.setStatusMessage("");
        super.exitMode();
    }

    private void handleMouseClicked(MouseEvent event) {
        if (!SwingUtilities.isLeftMouseButton(event) || event.getClickCount() != 1) {
            return;
        }
        MapView mapView = MainApplication.getMap().mapView;
        EastNorth point = mapView.getEastNorth(event.getX(), event.getY());
        if (point == null || !point.isValid()) {
            return;
        }
        if (System.currentTimeMillis() - event.getWhen() > STALE_CLICK_MILLIS) {
            event.consume();
            controller.setStatusMessage(tr("Ignored a delayed click after a busy operation."));
            return;
        }
        if (generating) {
            event.consume();
            controller.setStatusMessage(tr("Generation is already in progress."));
            return;
        }

        generating = true;
        previewTimer.stop();
        pendingPreviewCursor = null;
        try {
            addPointForCurrentMode(point, mapView);
            event.consume();

            int requiredPointCount = controller.getAlignmentMode().getRequiredPointCount();
            if (controller.getControlPointCount() < requiredPointCount) {
                Logging.info(tr(
                        "Road/Rail Alignment: added control point {0}/{1}.",
                        controller.getControlPointCount(),
                        requiredPointCount));
                repaintPreview(point);
                return;
            }

            List<EastNorth> controlPoints = controller.snapshotControlPoints();
            lastGeneratedDetail = "";
            maybePromotePreviousSegment(controlPoints);
            clearLastSingleTieSample();
            List<EastNorth> sampledPoints = sample(controlPoints);
            applyPendingPreviousSegmentPromotion();
            Way way = JosmWayBuilder.createWay(
                    sampledPoints,
                    controller.getFeatureType(),
                    controller.isSnapToExistingNodes(),
                    controller.getNodeSnapToleranceMeters(),
                    controlPoints);
            controller.setStatusMessage(lastGeneratedDetail == null || lastGeneratedDetail.isEmpty()
                    ? tr("Generated alignment with {0} nodes.", way.getNodesCount())
                    : tr("Generated alignment with {0} nodes. {1}", way.getNodesCount(), lastGeneratedDetail));
            keepEndPointForContinuousWork(way, controlPoints, sampledPoints);
            previewPainter.clear();
            mapView.repaint();
        } catch (IllegalArgumentException exception) {
            pendingPromotedPreviousSegment = null;
            showWarning(exception.getMessage());
            controller.setStatusMessage(exception.getMessage());
            controller.clearControlPoints();
            clearPendingState();
            clearContinuousExtension();
            previewPainter.clear();
            mapView.repaint();
        } catch (RuntimeException exception) {
            pendingPromotedPreviousSegment = null;
            Logging.error(exception);
            String message = tr("Unexpected error while generating the alignment. See the JOSM log for details.");
            showWarning(message);
            controller.setStatusMessage(message);
            controller.clearControlPoints();
            clearPendingState();
            clearContinuousExtension();
            previewPainter.clear();
            mapView.repaint();
        } finally {
            generating = false;
        }
    }

    private void addPointForCurrentMode(EastNorth point, MapView mapView) {
        double toleranceMeters = snapToleranceMeters(mapView);
        AlignmentMode mode = controller.getAlignmentMode();

        if (mode == AlignmentMode.BASIC_ALIGNMENT) {
            addPointForBasicMode(point, mapView, toleranceMeters);
            return;
        }

        if (mode == AlignmentMode.RAMP_FROM_SELECTED_WAY && controller.getControlPointCount() == 0) {
            pendingStartTieIn = ExistingWayAnalyzer.tieInFromNearestWay(point, toleranceMeters, null);
            controller.addControlPoint(pendingStartTieIn.getPoint());
            enableEndpointExtensionSnap(pendingStartTieIn);
            controller.setStatusMessage(ExistingWayAnalyzer.describeTieIn(pendingStartTieIn));
            return;
        }

        if (mode == AlignmentMode.RAMP_BETWEEN_SELECTED_WAYS) {
            if (controller.getControlPointCount() == 0) {
                pendingStartTieIn = ExistingWayAnalyzer.tieInFromNearestWay(point, toleranceMeters, null);
                controller.addControlPoint(pendingStartTieIn.getPoint());
                controller.setStatusMessage(ExistingWayAnalyzer.describeTieIn(pendingStartTieIn));
                return;
            }
            if (controller.getControlPointCount() == 1) {
                pendingEndTieIn = ExistingWayAnalyzer.tieInFromNearestWay(
                        point,
                        toleranceMeters,
                        pendingStartTieIn.getSourceWay());
                controller.addControlPoint(pendingEndTieIn.getPoint());
                ExistingWayAnalyzer.selectTieInWays(pendingStartTieIn, pendingEndTieIn);
                controller.setStatusMessage(ExistingWayAnalyzer.describeTieIn(pendingEndTieIn));
                return;
            }
        }

        if (isSourceDirectionSnapMode(mode) && controller.getControlPointCount() == 0) {
            TieInPoint sourceTieIn = sourceDirectionTieIn(point, toleranceMeters);
            if (sourceTieIn != null) {
                controller.addControlPoint(sourceTieIn.getPoint());
                continuousAnchorPoint = sourceTieIn.getPoint();
                continuousExtensionTangent = sourceTieIn.getTangent().normalize();
                continuousRampCurvature = 0.0;
                bidirectionalExtensionSnap = true;
                controller.setStatusMessage(tr("Snapped to the source way direction; the next point will be projected along it."));
                return;
            }
        }

        EastNorth snappedPoint = snapControlPoint(point, mapView);
        controller.addControlPoint(snappedPoint);
    }

    private void addPointForBasicMode(EastNorth point, MapView mapView, double toleranceMeters) {
        int count = controller.getControlPointCount();
        if (count == 0) {
            TieInPoint tieIn = sourceDirectionTieIn(point, toleranceMeters);
            if (tieIn != null) {
                pendingStartTieIn = tieIn;
                pendingNewPointStart = false;
                controller.addControlPoint(tieIn.getPoint());
                enableEndpointExtensionSnap(tieIn);
                controller.setStatusMessage(ExistingWayAnalyzer.describeTieIn(tieIn));
                return;
            }

            EastNorth start = snapControlPoint(point, mapView);
            pendingNewPointStart = true;
            pendingStartTieIn = null;
            controller.addControlPoint(start);
            continuousAnchorPoint = start;
            continuousExtensionTangent = null;
            continuousRampCurvature = 0.0;
            bidirectionalExtensionSnap = false;
            controller.setStatusMessage(tr("Started a new alignment point; the first segment will be straight."));
            return;
        }

        if (count == 1 && pendingStartTieIn != null && !pendingNewPointStart) {
            pendingEndTieIn = sourceDirectionTieInExcluding(point, toleranceMeters, pendingStartTieIn.getSourceWay());
            if (pendingEndTieIn != null) {
                controller.addControlPoint(pendingEndTieIn.getPoint());
                ExistingWayAnalyzer.selectTieInWays(pendingStartTieIn, pendingEndTieIn);
                controller.setStatusMessage(ExistingWayAnalyzer.describeTieIn(pendingEndTieIn));
                return;
            }
        }

        EastNorth snappedPoint = snapControlPoint(point, mapView);
        controller.addControlPoint(snappedPoint);
    }

    private void enableEndpointExtensionSnap(TieInPoint tieInPoint) {
        Vector2D tangent = ExistingWayAnalyzer.endpointExtensionTangent(tieInPoint);
        if (tangent == null) {
            clearContinuousExtension();
            return;
        }
        continuousAnchorPoint = tieInPoint.getPoint();
        continuousExtensionTangent = tangent.normalize();
        continuousRampCurvature = 0.0;
        bidirectionalExtensionSnap = false;
    }

    private boolean isSourceDirectionSnapMode(AlignmentMode mode) {
        return mode == AlignmentMode.STRAIGHT_LINE
                || mode == AlignmentMode.PI_CIRCULAR_ARC
                || mode == AlignmentMode.LARGE_SWEEP_ARC;
    }

    private TieInPoint sourceDirectionTieIn(EastNorth point, double toleranceMeters) {
        return sourceDirectionTieInExcluding(point, toleranceMeters, null);
    }

    private TieInPoint sourceDirectionTieInExcluding(EastNorth point, double toleranceMeters, Way excludedWay) {
        try {
            return ExistingWayAnalyzer.tieInFromNearestWay(point, toleranceMeters, excludedWay);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void handleMouseMoved(MouseEvent event) {
        MapView mapView = MainApplication.getMap().mapView;
        EastNorth cursor = mapView.getEastNorth(event.getX(), event.getY());
        if (cursor == null || !cursor.isValid()) {
            return;
        }
        requestPreview(cursor);
    }

    private void requestPreview(EastNorth cursor) {
        if (generating) {
            return;
        }
        pendingPreviewCursor = cursor;
        long now = System.currentTimeMillis();
        long elapsed = now - lastPreviewMillis;
        if (elapsed >= PREVIEW_COALESCE_MILLIS) {
            previewTimer.stop();
            flushPendingPreview();
            return;
        }
        if (!previewTimer.isRunning()) {
            previewTimer.setInitialDelay((int) Math.max(1, PREVIEW_COALESCE_MILLIS - elapsed));
            previewTimer.restart();
        }
    }

    private void flushPendingPreview() {
        EastNorth cursor = pendingPreviewCursor;
        pendingPreviewCursor = null;
        if (cursor == null || generating) {
            return;
        }
        lastPreviewMillis = System.currentTimeMillis();
        repaintPreview(cursor);
    }

    private void repaintPreview(EastNorth cursor) {
        MapView mapView = MainApplication.getMap().mapView;
        List<EastNorth> controls = new ArrayList<>(controller.snapshotControlPoints());
        List<EastNorth> previewControls = new ArrayList<>(controls);
        EastNorth previewCursor = cursor;
        if (cursor != null && controls.size() < controller.getAlignmentMode().getRequiredPointCount()) {
            previewCursor = previewCursorForMode(controls, cursor, mapView);
            previewControls.add(previewCursor);
        }

        try {
            List<EastNorth> preview = samplePreview(controls, previewCursor, mapView);
            previewPainter.setPreview(preview, previewControls);
        } catch (IllegalArgumentException exception) {
            previewPainter.setPreview(previewControls, previewControls);
        } catch (RuntimeException exception) {
            Logging.warn(exception);
            previewPainter.setPreview(previewControls, previewControls);
        }
        mapView.repaint();
    }

    private EastNorth previewCursorForMode(List<EastNorth> controls, EastNorth cursor, MapView mapView) {
        AlignmentMode mode = controller.getAlignmentMode();
        if (mode == AlignmentMode.RAMP_BETWEEN_SELECTED_WAYS && controls.size() == 1) {
            return cursor;
        }
        return snapControlPoint(cursor, mapView);
    }

    private List<EastNorth> samplePreview(List<EastNorth> controls, EastNorth cursor, MapView mapView) {
        if (controls.isEmpty() || cursor == null) {
            return controls;
        }

        AlignmentMode mode = controller.getAlignmentMode();
        if (mode == AlignmentMode.BASIC_ALIGNMENT && controls.size() == 1) {
            EastNorth basicTarget = pendingNewPointStart ? snapControlPoint(cursor, mapView) : cursor;
            return sampleBasicSegmentPreview(controls.get(0), basicTarget);
        }

        if ((mode == AlignmentMode.RAMP_FROM_SELECTED_WAY || mode == AlignmentMode.RAMP_BETWEEN_SELECTED_WAYS)
                && pendingStartTieIn != null
                && controls.size() == 1) {
            EastNorth rampTarget = mode == AlignmentMode.RAMP_FROM_SELECTED_WAY
                    ? snapControlPoint(cursor, mapView)
                    : cursor;
            return sampleSingleTieRampPreview(rampTarget);
        }

        if (mode == AlignmentMode.PI_CIRCULAR_ARC && controls.size() == 2) {
            return samplePiCurve(controls.get(0), controls.get(1), snapControlPoint(cursor, mapView));
        }

        if (mode == AlignmentMode.LARGE_SWEEP_ARC && controls.size() == 2) {
            return sampleLargeSweepArc(controls.get(0), controls.get(1), snapControlPoint(cursor, mapView));
        }

        if (mode == AlignmentMode.TRANSITION_SPIRAL && controls.size() == 2) {
            return SpiralSampler.sampleTransition(
                    controls.get(0),
                    controls.get(1),
                    cursor,
                    controller.getRadiusMeters(),
                    controller.getSampleIntervalMeters());
        }

        return LineSampler.sample(controls.get(0), snapControlPoint(cursor, mapView), controller.getSampleIntervalMeters());
    }

    private double previewSampleIntervalMeters() {
        return Math.max(controller.getSampleIntervalMeters() * PREVIEW_SAMPLE_INTERVAL_MULTIPLIER, 10.0);
    }

    private List<EastNorth> sampleBasicSegmentPreview(EastNorth start, EastNorth target) {
        if (pendingNewPointStart || pendingStartTieIn == null) {
            return LineSampler.sample(start, target, previewSampleIntervalMeters());
        }
        if (pendingEndTieIn != null) {
            return sampleTwoTieRampPreview();
        }
        return sampleSingleTieRampPreview(target);
    }

    private List<EastNorth> sample(List<EastNorth> points) {
        AlignmentMode mode = controller.getAlignmentMode();
        if (mode == AlignmentMode.BASIC_ALIGNMENT) {
            return sampleBasicSegment(points.get(0), points.get(1));
        }
        if (mode == AlignmentMode.RAMP_FROM_SELECTED_WAY) {
            if (pendingStartTieIn == null) {
                throw new IllegalArgumentException(tr("Click a tie-in point near one selected existing way first."));
            }
            return sampleSingleTieRamp(points.get(1));
        }
        if (mode == AlignmentMode.RAMP_BETWEEN_SELECTED_WAYS) {
            if (pendingStartTieIn == null || pendingEndTieIn == null) {
                throw new IllegalArgumentException(tr("Click tie-in points near two selected existing ways first."));
            }
            return sampleTwoTieRamp();
        }
        if (mode == AlignmentMode.TRANSITION_SPIRAL) {
            return SpiralSampler.sampleTransition(
                    points.get(0),
                    points.get(1),
                    points.get(2),
                    controller.getRadiusMeters(),
                    controller.getSampleIntervalMeters());
        }
        if (mode == AlignmentMode.PI_CIRCULAR_ARC) {
            return samplePiCurve(points.get(0), points.get(1), points.get(2));
        }
        if (mode == AlignmentMode.LARGE_SWEEP_ARC) {
            return sampleLargeSweepArc(points.get(0), points.get(1), points.get(2));
        }
        return LineSampler.sample(
                points.get(0),
                points.get(1),
                controller.getSampleIntervalMeters());
    }

    private List<EastNorth> sampleBasicSegment(EastNorth start, EastNorth target) {
        if (pendingNewPointStart || pendingStartTieIn == null) {
            clearLastSingleTieSample();
            return LineSampler.sample(start, target, controller.getSampleIntervalMeters());
        }
        if (pendingEndTieIn != null) {
            clearLastSingleTieSample();
            return sampleTwoTieRamp();
        }
        return sampleSingleTieRamp(target);
    }

    private List<EastNorth> samplePiCurve(EastNorth start, EastNorth pi, EastNorth end) {
        boolean includeExitTangent = !controller.isContinuousMode();
        if (controller.isUseSpiralTransitions() && controller.getSpiralLengthMeters() > 0.0) {
            return PiSpiralArcSampler.sample(
                    start,
                    pi,
                    end,
                    controller.getRadiusMeters(),
                    controller.getSpiralLengthMeters(),
                    controller.getSampleIntervalMeters(),
                    includeExitTangent);
        }
        return PiArcSampler.sample(
                start,
                pi,
                end,
                controller.getRadiusMeters(),
                controller.getSampleIntervalMeters(),
                includeExitTangent);
    }

    private List<EastNorth> sampleLargeSweepArc(EastNorth start, EastNorth tangentGuide, EastNorth endGuide) {
        return LargeSweepArcSampler.sample(
                start,
                tangentGuide,
                endGuide,
                controller.getRadiusMeters(),
                controller.getExtraLoopTurns(),
                controller.isUseSpiralTransitions() ? controller.getSpiralLengthMeters() : 0.0,
                controller.getSampleIntervalMeters());
    }

    private List<EastNorth> sampleSingleTieRamp(EastNorth target) {
        boolean keepTieInDirection = isContinuousRampStart();
        TieInPoint startTieIn = effectiveSingleTieRampStart(keepTieInDirection);
        if (controller.isUseSpiralTransitions() && controller.getSpiralLengthMeters() > 0.0) {
            lastSingleTieNoExitPoints = CompoundRampSampler.sampleSingleTieRamp(
                    startTieIn,
                    target,
                    controller.getRadiusMeters(),
                    controller.getSpiralLengthMeters(),
                    controller.getSampleIntervalMeters(),
                    keepTieInDirection,
                    true);
            lastSingleTieExitCurvature = endSignedCurvature(lastSingleTieNoExitPoints);
            return CompoundRampSampler.sampleSingleTieRamp(
                    startTieIn,
                    target,
                    controller.getRadiusMeters(),
                    controller.getSpiralLengthMeters(),
                    controller.getSampleIntervalMeters(),
                    keepTieInDirection,
                    currentSegmentKeepsExitCurvature);
        }
        lastSingleTieNoExitPoints = null;
        lastSingleTieExitCurvature = 0.0;
        return RampArcSampler.sample(
                startTieIn,
                target,
                controller.getRadiusMeters(),
                controller.getSampleIntervalMeters(),
                keepTieInDirection);
    }

    private List<EastNorth> sampleSingleTieRampPreview(EastNorth target) {
        boolean keepTieInDirection = isContinuousRampStart();
        TieInPoint startTieIn = effectiveSingleTieRampStart(keepTieInDirection);
        return RampArcSampler.sample(
                startTieIn,
                target,
                controller.getRadiusMeters(),
                previewSampleIntervalMeters(),
                keepTieInDirection);
    }

    private TieInPoint effectiveSingleTieRampStart(boolean continuousRampStart) {
        if (!continuousRampStart || shouldTrackContinuousRampCurvature()) {
            return pendingStartTieIn;
        }
        return new TieInPoint(
                pendingStartTieIn.getPoint(),
                pendingStartTieIn.getTangent(),
                pendingStartTieIn.getSourceWay(),
                pendingStartTieIn.getSegmentIndex(),
                pendingStartTieIn.getDistanceToClickMeters(),
                pendingStartTieIn.getStationMeters(),
                Double.NaN,
                0.0);
    }

    private boolean isContinuousRampStart() {
        return (controller.getAlignmentMode() == AlignmentMode.RAMP_FROM_SELECTED_WAY
                || controller.getAlignmentMode() == AlignmentMode.BASIC_ALIGNMENT)
                && pendingStartTieIn != null
                && pendingStartTieIn.getSourceWay() == null
                && pendingStartTieIn.getSegmentIndex() < 0;
    }

    private void maybePromotePreviousSegment(List<EastNorth> controlPoints) {
        currentSegmentKeepsExitCurvature = false;
        if (!shouldTrackContinuousRampCurvature()
                || previousGeneratedSegment == null
                || previousGeneratedSegment.noExitPoints == null
                || previousGeneratedSegment.noExitPoints.size() < 2
                || !isContinuousRampStart()
                || controlPoints == null
                || controlPoints.size() < 2) {
            return;
        }

        Vector2D promotedTangent = endTangent(previousGeneratedSegment.noExitPoints);
        if (promotedTangent == null) {
            return;
        }

        if (!isCurvedAwayFromTangent(controlPoints.get(1), promotedTangent)) {
            return;
        }

        double currentTargetCurvature = estimateCurrentSingleTieCurvature(controlPoints.get(1), promotedTangent);
        if (Math.signum(previousGeneratedSegment.exitCurvature) == 0.0
                || Math.signum(currentTargetCurvature) == 0.0
                || Math.signum(previousGeneratedSegment.exitCurvature) != Math.signum(currentTargetCurvature)) {
            return;
        }

        continuousRampCurvature = previousGeneratedSegment.exitCurvature;
        if (pendingStartTieIn != null) {
            pendingStartTieIn = new TieInPoint(
                    pendingStartTieIn.getPoint(),
                    promotedTangent,
                    pendingStartTieIn.getSourceWay(),
                    pendingStartTieIn.getSegmentIndex(),
                    pendingStartTieIn.getDistanceToClickMeters(),
                    pendingStartTieIn.getStationMeters(),
                    radiusFromCurvature(continuousRampCurvature),
                    continuousRampCurvature);
        }
        continuousExtensionTangent = promotedTangent;
        currentSegmentKeepsExitCurvature = false;
        pendingPromotedPreviousSegment = previousGeneratedSegment;
        previousGeneratedSegment = null;
        lastGeneratedDetail = tr("Reused the previous ramp curvature for the next same-direction curve.");
        controller.setStatusMessage(tr("Reused the previous ramp curvature for the next same-direction curve."));
    }

    private boolean isCurvedAwayFromTangent(EastNorth target, Vector2D tangent) {
        if (pendingStartTieIn == null || target == null || !target.isValid() || tangent == null) {
            return false;
        }

        Vector2D offset = Vector2D.between(pendingStartTieIn.getPoint(), target);
        if (offset.length() < 0.5 || offset.dot(tangent) <= 0.0) {
            return false;
        }

        double lateralOffset = Math.abs(offset.cross(tangent.normalize()));
        return lateralOffset > Math.max(0.5, controller.getNodeSnapToleranceMeters());
    }

    private void applyPendingPreviousSegmentPromotion() {
        if (pendingPromotedPreviousSegment == null) {
            return;
        }
        GeneratedSegment segment = pendingPromotedPreviousSegment;
        pendingPromotedPreviousSegment = null;
        JosmWayBuilder.replaceWayNodes(
                segment.way,
                segment.noExitPoints,
                controller.isSnapToExistingNodes(),
                controller.getNodeSnapToleranceMeters(),
                segment.controlPoints);
    }

    private double estimateCurrentSingleTieCurvature(EastNorth target) {
        return estimateCurrentSingleTieCurvature(
                target,
                pendingStartTieIn == null ? null : pendingStartTieIn.getTangent());
    }

    private double estimateCurrentSingleTieCurvature(EastNorth target, Vector2D tangent) {
        if (pendingStartTieIn == null || target == null || !target.isValid()) {
            return 0.0;
        }
        try {
            return RampArcSampler.signedCurvatureFromTangent(
                    pendingStartTieIn.getPoint(),
                    tangent,
                    target,
                    true);
        } catch (IllegalArgumentException exception) {
            return 0.0;
        }
    }

    private List<EastNorth> sampleTwoTieRamp() {
        if (controller.isAutoOptimizeTwoTieRamp()) {
            OptimizedTwoTieRampSampler.Result result = OptimizedTwoTieRampSampler.sample(
                    pendingStartTieIn,
                    pendingEndTieIn,
                    controller.getRadiusMeters(),
                    controller.isUseSpiralTransitions(),
                    controller.getExtraLoopTurns(),
                    effectiveTieInDirectionMode(),
                    controller.getSampleIntervalMeters());
            boolean applyOptimizedParameters = controller.isApplyOptimizedTwoTieRampParameters();
            if (applyOptimizedParameters) {
                if (Double.isFinite(result.getDesignRadiusMeters())) {
                    controller.setRadiusMeters(result.getDesignRadiusMeters());
                }
                controller.setSpiralLengthMeters(result.getTransitionLengthMeters());
            }
            lastGeneratedDetail = formatOptimizationDetail(result, applyOptimizedParameters);
            return result.getPoints();
        }
        if (controller.isUseSpiralTransitions() && controller.getSpiralLengthMeters() > 0.0) {
            return CompoundRampSampler.sampleTwoTieRamp(
                    pendingStartTieIn,
                    pendingEndTieIn,
                    controller.getRadiusMeters(),
                    controller.getSpiralLengthMeters(),
                    effectiveTieInDirectionMode(),
                    controller.getSampleIntervalMeters());
        }
        try {
            return HermiteRampSampler.sample(
                    pendingStartTieIn,
                    pendingEndTieIn,
                    controller.getRadiusMeters(),
                    effectiveTieInDirectionMode(),
                    controller.getSampleIntervalMeters());
        } catch (IllegalArgumentException exception) {
            lastGeneratedDetail = tr("Inserted an intermediate straight segment.");
            return StraightInsertRampSampler.sample(
                    pendingStartTieIn,
                    pendingEndTieIn,
                    controller.getRadiusMeters(),
                    controller.getExtraLoopTurns(),
                    effectiveTieInDirectionMode(),
                    controller.getSampleIntervalMeters());
        }
    }

    private List<EastNorth> sampleTwoTieRampPreview() {
        try {
            return HermiteRampSampler.sample(
                    pendingStartTieIn,
                    pendingEndTieIn,
                    controller.getRadiusMeters(),
                    effectiveTieInDirectionMode(),
                    previewSampleIntervalMeters());
        } catch (IllegalArgumentException exception) {
            return StraightInsertRampSampler.sample(
                    pendingStartTieIn,
                    pendingEndTieIn,
                    controller.getRadiusMeters(),
                    controller.getExtraLoopTurns(),
                    effectiveTieInDirectionMode(),
                    previewSampleIntervalMeters());
        }
    }

    private TieInDirectionMode effectiveTieInDirectionMode() {
        if (controller.getAlignmentMode() == AlignmentMode.RAMP_BETWEEN_SELECTED_WAYS
                && controller.getExtraLoopTurns() > 0
                && controller.getTieInDirectionMode() == TieInDirectionMode.AUTO_CHORD) {
            return TieInDirectionMode.SOURCE_WAY;
        }
        return controller.getTieInDirectionMode();
    }

    private String formatOptimizationDetail(
            OptimizedTwoTieRampSampler.Result result,
            boolean applyOptimizedParameters) {
        String radiusText = Double.isFinite(result.getDesignRadiusMeters())
                ? tr("Automatic R about {0} m", Math.round(result.getDesignRadiusMeters()))
                : tr("Automatic R approximates a straight line");
        String shapeText = radiusText;
        if (result.hasInsertedLoop()) {
            shapeText = tr("{0}, inserted a loop", shapeText);
        } else if (result.hasInsertedStraight()) {
            shapeText = tr("{0}, used an arc/straight combination", shapeText);
        }
        if (result.usesSourceWayDirection()) {
            shapeText = tr("{0}, along source way direction", shapeText);
        }
        return applyOptimizedParameters
                ? tr(
                        "{0}, transition spiral about {1} m.",
                        shapeText,
                        Math.round(result.getTransitionLengthMeters()))
                : tr(
                        "{0}, transition spiral about {1} m; parameters were not applied.",
                        shapeText,
                        Math.round(result.getTransitionLengthMeters()));
    }

    private EastNorth snapControlPoint(EastNorth point, MapView mapView) {
        if (shouldForcePiPointToPreviousTangent()) {
            return projectToContinuousExtension(point);
        }

        if (bidirectionalExtensionSnap && isContinuousExtensionSnapActive()) {
            EastNorth directionSnappedPoint = sourceDirectionSnappedPoint(point, mapView);
            if (directionSnappedPoint != null) {
                return directionSnappedPoint;
            }
        }

        if (controller.isSnapToExistingNodes()) {
            EastNorth snappedNode = NodeSnapper.snapToExistingNode(
                    point,
                    controller.getNodeSnapToleranceMeters()).orElse(null);
            if (snappedNode != null) {
                return snappedNode;
            }
        }
        return snapToContinuousExtension(point, mapView);
    }

    private EastNorth sourceDirectionSnappedPoint(EastNorth point, MapView mapView) {
        EastNorth projected = projectToContinuousExtension(point);
        double distanceAlong = Vector2D.between(continuousAnchorPoint, projected).dot(continuousExtensionTangent);
        if (distanceAlong == 0.0 && projected.distance(continuousAnchorPoint) > 0.001) {
            return null;
        }
        return projected.distance(point) <= snapToleranceMeters(mapView) ? projected : null;
    }

    private double snapToleranceMeters(MapView mapView) {
        return Math.min(100.0, Math.max(5.0, mapView.getDist100Pixel() * 0.3));
    }

    private void clearPendingState() {
        pendingStartTieIn = null;
        pendingEndTieIn = null;
        pendingNewPointStart = false;
        currentSegmentKeepsExitCurvature = false;
        clearLastSingleTieSample();
    }

    private void clearLastSingleTieSample() {
        lastSingleTieNoExitPoints = null;
        lastSingleTieExitCurvature = 0.0;
    }

    private void clearContinuousExtension() {
        continuousAnchorPoint = null;
        continuousExtensionTangent = null;
        continuousRampCurvature = 0.0;
        previousGeneratedSegment = null;
        pendingPromotedPreviousSegment = null;
        bidirectionalExtensionSnap = false;
    }

    public void clearCurrentInput() {
        clearCurrentInput(MainApplication.getMap() == null ? null : MainApplication.getMap().mapView);
    }

    public boolean undoLastStep() {
        return undoLastStep(MainApplication.getMap() == null ? null : MainApplication.getMap().mapView);
    }

    private boolean undoLastStep(MapView mapView) {
        int previousCount = controller.getControlPointCount();
        if (!controller.removeLastControlPoint()) {
            return false;
        }

        adjustStateAfterUndo(previousCount);
        List<EastNorth> controls = controller.snapshotControlPoints();
        previewPainter.setPreview(controls, controls);
        controller.setStatusMessage(tr(
                "Undid last step. Control points: {0}/{1}",
                controller.getControlPointCount(),
                controller.getAlignmentMode().getRequiredPointCount()));
        if (mapView != null) {
            mapView.repaint();
        }
        return true;
    }

    private void clearCurrentInput(MapView mapView) {
        controller.clearControlPoints();
        clearPendingState();
        clearContinuousExtension();
        previewPainter.clear();
        controller.setStatusMessage(tr("Cleared control points."));
        if (mapView != null) {
            mapView.repaint();
        }
    }

    private void adjustStateAfterUndo(int previousControlPointCount) {
        AlignmentMode mode = controller.getAlignmentMode();
        int currentCount = controller.getControlPointCount();

        if (currentCount == 0) {
            clearPendingState();
            clearContinuousExtension();
            return;
        }

        if (mode == AlignmentMode.RAMP_FROM_SELECTED_WAY && currentCount == 0) {
            pendingStartTieIn = null;
        } else if (mode == AlignmentMode.BASIC_ALIGNMENT) {
            if (previousControlPointCount >= 2 && currentCount < 2) {
                pendingEndTieIn = null;
            }
        } else if (mode == AlignmentMode.RAMP_BETWEEN_SELECTED_WAYS) {
            if (previousControlPointCount >= 2 && currentCount < 2) {
                pendingEndTieIn = null;
            }
            if (currentCount == 0) {
                pendingStartTieIn = null;
            }
        }

        if (previousControlPointCount == 1) {
            clearContinuousExtension();
        }
    }

    private EastNorth snapToContinuousExtension(EastNorth point, MapView mapView) {
        if (!isContinuousExtensionSnapActive() || point == null || !point.isValid()) {
            return point;
        }

        EastNorth projected = projectToContinuousExtension(point);
        double distanceAlong = Vector2D.between(continuousAnchorPoint, projected).dot(continuousExtensionTangent);
        if (!bidirectionalExtensionSnap && distanceAlong <= 0.0) {
            return point;
        }

        double tolerance = snapToleranceMeters(mapView);
        if (projected.distance(point) <= tolerance) {
            return projected;
        }
        return point;
    }

    private EastNorth projectToContinuousExtension(EastNorth point) {
        if (continuousAnchorPoint == null || continuousExtensionTangent == null || point == null || !point.isValid()) {
            return point;
        }
        Vector2D fromAnchor = Vector2D.between(continuousAnchorPoint, point);
        double distanceAlong = fromAnchor.dot(continuousExtensionTangent);
        if (!bidirectionalExtensionSnap && distanceAlong <= 0.0) {
            return point;
        }
        return continuousExtensionTangent.pointFrom(continuousAnchorPoint, distanceAlong);
    }

    private boolean shouldForcePiPointToPreviousTangent() {
        return (controller.getAlignmentMode() == AlignmentMode.PI_CIRCULAR_ARC
                || controller.getAlignmentMode() == AlignmentMode.LARGE_SWEEP_ARC)
                && isContinuousExtensionSnapActive();
    }

    private boolean isContinuousExtensionSnapActive() {
        AlignmentMode mode = controller.getAlignmentMode();
        if (!(mode == AlignmentMode.STRAIGHT_LINE
                || mode == AlignmentMode.PI_CIRCULAR_ARC
                || mode == AlignmentMode.LARGE_SWEEP_ARC
                || mode == AlignmentMode.RAMP_FROM_SELECTED_WAY
                || mode == AlignmentMode.BASIC_ALIGNMENT)
                || controller.getControlPointCount() != 1
                || continuousAnchorPoint == null
                || continuousExtensionTangent == null) {
            return false;
        }
        EastNorth retainedPoint = controller.snapshotControlPoints().get(0);
        return retainedPoint.distance(continuousAnchorPoint) <= 0.001;
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if (!AlignmentController.PROP_ALIGNMENT_MODE.equals(event.getPropertyName())) {
            return;
        }
        clearPendingState();
        previewPainter.clear();

        AlignmentMode mode = controller.getAlignmentMode();
        if (controller.isContinuousMode()
                && continuousAnchorPoint != null
                && continuousExtensionTangent != null
                && (mode == AlignmentMode.STRAIGHT_LINE
                || mode == AlignmentMode.PI_CIRCULAR_ARC
                || mode == AlignmentMode.LARGE_SWEEP_ARC
                || mode == AlignmentMode.RAMP_FROM_SELECTED_WAY
                || mode == AlignmentMode.BASIC_ALIGNMENT)) {
            controller.keepOnlyControlPoint(continuousAnchorPoint);
            if (mode == AlignmentMode.RAMP_FROM_SELECTED_WAY || mode == AlignmentMode.BASIC_ALIGNMENT) {
                pendingStartTieIn = new TieInPoint(
                        continuousAnchorPoint,
                        continuousExtensionTangent,
                        null,
                        -1,
                        0.0,
                        0.0,
                        radiusFromCurvature(retainedRampCurvature()),
                        retainedRampCurvature());
            }
            controller.setStatusMessage(tr("Reused the previous segment end tangent as the next segment start direction."));
            MapView mapView = MainApplication.getMap() == null ? null : MainApplication.getMap().mapView;
            if (mapView != null) {
                mapView.repaint();
            }
        } else if (mode != AlignmentMode.STRAIGHT_LINE
                && mode != AlignmentMode.PI_CIRCULAR_ARC
                && mode != AlignmentMode.LARGE_SWEEP_ARC
                && mode != AlignmentMode.RAMP_FROM_SELECTED_WAY
                && mode != AlignmentMode.BASIC_ALIGNMENT) {
            clearContinuousExtension();
        }
    }

    private void keepEndPointForContinuousWork(Way way, List<EastNorth> controlPoints, List<EastNorth> sampledPoints) {
        List<EastNorth> points = controller.snapshotControlPoints();
        AlignmentMode mode = controller.getAlignmentMode();
        boolean wasNewPointStart = pendingNewPointStart;
        boolean keepCurrentExitCurvature = currentSegmentKeepsExitCurvature;
        List<EastNorth> rememberedNoExitPoints = lastSingleTieNoExitPoints == null
                ? null
                : new ArrayList<>(lastSingleTieNoExitPoints);
        double rememberedExitCurvature = lastSingleTieExitCurvature;
        boolean canKeepEndPoint = controller.isContinuousMode()
                && (mode == AlignmentMode.STRAIGHT_LINE
                || mode == AlignmentMode.PI_CIRCULAR_ARC
                || mode == AlignmentMode.LARGE_SWEEP_ARC
                || mode == AlignmentMode.TRANSITION_SPIRAL
                || mode == AlignmentMode.BASIC_ALIGNMENT)
                && !points.isEmpty();

        clearPendingState();
        if (controller.isContinuousMode()
                && (mode == AlignmentMode.RAMP_FROM_SELECTED_WAY || mode == AlignmentMode.BASIC_ALIGNMENT)
                && !wasNewPointStart
                && sampledPoints != null
                && !sampledPoints.isEmpty()) {
            EastNorth endPoint = sampledPoints.get(sampledPoints.size() - 1);
            Vector2D tangent = endTangent(sampledPoints);
            double curvature = keepCurrentExitCurvature ? endSignedCurvature(sampledPoints) : 0.0;
            controller.keepOnlyControlPoint(endPoint);
            continuousAnchorPoint = endPoint;
            continuousExtensionTangent = tangent;
            continuousRampCurvature = curvature;
            bidirectionalExtensionSnap = false;
            if (tangent != null) {
                pendingStartTieIn = new TieInPoint(
                        endPoint,
                        tangent,
                        null,
                        -1,
                        0.0,
                        0.0,
                        radiusFromCurvature(curvature),
                        curvature);
            }
            rememberGeneratedSegment(way, controlPoints, rememberedNoExitPoints, rememberedExitCurvature);
            controller.setStatusMessage(tr("Kept the ramp end point and tangent as the next ramp start."));
            return;
        }

        if (canKeepEndPoint) {
            EastNorth endPoint = continuousRetainedPoint(mode, points, sampledPoints);
            controller.keepOnlyControlPoint(endPoint);
            if (mode == AlignmentMode.STRAIGHT_LINE
                    || mode == AlignmentMode.PI_CIRCULAR_ARC
                    || mode == AlignmentMode.LARGE_SWEEP_ARC
                    || mode == AlignmentMode.BASIC_ALIGNMENT) {
                continuousAnchorPoint = endPoint;
                continuousExtensionTangent = endTangent(sampledPoints);
                continuousRampCurvature = 0.0;
                bidirectionalExtensionSnap = false;
                if (mode == AlignmentMode.BASIC_ALIGNMENT && continuousExtensionTangent != null) {
                    pendingStartTieIn = new TieInPoint(
                            endPoint,
                            continuousExtensionTangent,
                            null,
                            -1,
                            0.0,
                            0.0,
                            Double.NaN,
                            0.0);
                    pendingNewPointStart = false;
                }
            } else {
                clearContinuousExtension();
            }
            previousGeneratedSegment = null;
            if (mode == AlignmentMode.PI_CIRCULAR_ARC || mode == AlignmentMode.LARGE_SWEEP_ARC) {
                controller.setStatusMessage(tr("Kept the curve end point and outgoing tangent for the next straight segment."));
            } else {
                controller.setStatusMessage(tr("Kept the end point as the start of the next line."));
            }
        } else {
            controller.clearControlPoints();
            clearContinuousExtension();
            previousGeneratedSegment = null;
        }
        pendingNewPointStart = false;
        currentSegmentKeepsExitCurvature = false;
    }

    private EastNorth continuousRetainedPoint(
            AlignmentMode mode,
            List<EastNorth> controlPoints,
            List<EastNorth> sampledPoints) {
        if ((mode == AlignmentMode.PI_CIRCULAR_ARC || mode == AlignmentMode.LARGE_SWEEP_ARC)
                && sampledPoints != null
                && !sampledPoints.isEmpty()) {
            return sampledPoints.get(sampledPoints.size() - 1);
        }
        return controlPoints.get(controlPoints.size() - 1);
    }

    private void rememberGeneratedSegment(
            Way way,
            List<EastNorth> controlPoints,
            List<EastNorth> noExitPoints,
            double exitCurvature) {
        if (!shouldTrackContinuousRampCurvature()
                || way == null
                || noExitPoints == null
                || noExitPoints.size() < 2
                || Math.abs(exitCurvature) < 1e-9) {
            previousGeneratedSegment = null;
            return;
        }
        previousGeneratedSegment = new GeneratedSegment(
                way,
                new ArrayList<>(controlPoints),
                new ArrayList<>(noExitPoints),
                exitCurvature);
    }

    private Vector2D endTangent(List<EastNorth> sampledPoints) {
        if (sampledPoints == null || sampledPoints.size() < 2) {
            return null;
        }
        EastNorth end = sampledPoints.get(sampledPoints.size() - 1);
        for (int i = sampledPoints.size() - 2; i >= 0; i--) {
            EastNorth previous = sampledPoints.get(i);
            if (previous != null && previous.isValid() && previous.distance(end) > 0.001) {
                return Vector2D.between(previous, end).normalize();
            }
        }
        return null;
    }

    private double endSignedCurvature(List<EastNorth> sampledPoints) {
        if (sampledPoints == null || sampledPoints.size() < 3) {
            return 0.0;
        }

        int endIndex = sampledPoints.size() - 1;
        EastNorth end = sampledPoints.get(endIndex);
        int middleIndex = previousDistinctPointIndex(sampledPoints, endIndex, end);
        if (middleIndex < 0) {
            return 0.0;
        }
        EastNorth middle = sampledPoints.get(middleIndex);
        int startIndex = previousDistinctPointIndex(sampledPoints, middleIndex, middle);
        if (startIndex < 0) {
            return 0.0;
        }
        EastNorth start = sampledPoints.get(startIndex);
        double radius = CurvatureEstimator.radiusFromThreePoints(start, middle, end);
        if (!Double.isFinite(radius) || radius <= 0.0) {
            return 0.0;
        }
        double cross = Vector2D.between(start, middle).cross(Vector2D.between(middle, end));
        if (Math.abs(cross) < 1e-9) {
            return 0.0;
        }
        return Math.copySign(1.0 / radius, cross);
    }

    private int previousDistinctPointIndex(List<EastNorth> points, int beforeIndex, EastNorth reference) {
        for (int i = beforeIndex - 1; i >= 0; i--) {
            EastNorth point = points.get(i);
            if (point != null && point.isValid() && point.distance(reference) > 0.001) {
                return i;
            }
        }
        return -1;
    }

    private double retainedRampCurvature() {
        return controller.isContinuousRampCurvature() ? continuousRampCurvature : 0.0;
    }

    private boolean shouldTrackContinuousRampCurvature() {
        return controller.isContinuousRampCurvature()
                || controller.getAlignmentMode() == AlignmentMode.BASIC_ALIGNMENT;
    }

    private double radiusFromCurvature(double curvature) {
        return Math.abs(curvature) > 1e-9 ? Math.abs(1.0 / curvature) : Double.NaN;
    }

    private void showWarning(String message) {
        JOptionPane.showMessageDialog(
                MainApplication.getMainFrame(),
                message,
                tr("Road/Rail Alignment"),
                JOptionPane.WARNING_MESSAGE);
    }

    private static final class GeneratedSegment {
        private final Way way;
        private final List<EastNorth> controlPoints;
        private final List<EastNorth> noExitPoints;
        private final double exitCurvature;

        private GeneratedSegment(
                Way way,
                List<EastNorth> controlPoints,
                List<EastNorth> noExitPoints,
                double exitCurvature) {
            this.way = way;
            this.controlPoints = controlPoints;
            this.noExitPoints = noExitPoints;
            this.exitCurvature = exitCurvature;
        }
    }
}
