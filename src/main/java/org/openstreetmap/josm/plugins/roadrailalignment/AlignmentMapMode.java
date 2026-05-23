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

import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.plugins.roadrailalignment.geometry.CompoundRampSampler;
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
    private final AlignmentController controller;
    private final Runnable openWindowAction;
    private final PreviewPainter previewPainter = new PreviewPainter();
    private TieInPoint pendingStartTieIn;
    private TieInPoint pendingEndTieIn;
    private EastNorth continuousAnchorPoint;
    private Vector2D continuousExtensionTangent;
    private boolean bidirectionalExtensionSnap;
    private boolean controllerListenerRegistered;
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
            List<EastNorth> sampledPoints = sample(controlPoints);
            Way way = JosmWayBuilder.createWay(
                    sampledPoints,
                    controller.getFeatureType(),
                    controller.isSnapToExistingNodes(),
                    controller.getNodeSnapToleranceMeters(),
                    controlPoints);
            controller.setStatusMessage(lastGeneratedDetail == null || lastGeneratedDetail.isEmpty()
                    ? tr("Generated alignment with {0} nodes.", way.getNodesCount())
                    : tr("Generated alignment with {0} nodes. {1}", way.getNodesCount(), lastGeneratedDetail));
            keepEndPointForContinuousWork(sampledPoints);
            previewPainter.clear();
            mapView.repaint();
        } catch (IllegalArgumentException exception) {
            showWarning(exception.getMessage());
            controller.setStatusMessage(exception.getMessage());
            controller.clearControlPoints();
            clearPendingState();
            clearContinuousExtension();
            previewPainter.clear();
            mapView.repaint();
        }
    }

    private void addPointForCurrentMode(EastNorth point, MapView mapView) {
        double toleranceMeters = snapToleranceMeters(mapView);
        AlignmentMode mode = controller.getAlignmentMode();

        if (mode == AlignmentMode.RAMP_FROM_SELECTED_WAY && controller.getControlPointCount() == 0) {
            pendingStartTieIn = ExistingWayAnalyzer.tieInFromNearestWay(point, toleranceMeters, null);
            controller.addControlPoint(pendingStartTieIn.getPoint());
            // 端头接出时复用连续作业的切线吸附，让第二点可投影到既有线延长线上。
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
                bidirectionalExtensionSnap = true;
                controller.setStatusMessage(tr("Snapped to the source way direction; the next point will be projected along it."));
                return;
            }
        }

        EastNorth snappedPoint = snapControlPoint(point, mapView);
        controller.addControlPoint(snappedPoint);
    }

    private void enableEndpointExtensionSnap(TieInPoint tieInPoint) {
        // 只有接入点确实落在既有 Way 首/末端时，才启用端头直线吸附。
        Vector2D tangent = ExistingWayAnalyzer.endpointExtensionTangent(tieInPoint);
        if (tangent == null) {
            clearContinuousExtension();
            return;
        }
        continuousAnchorPoint = tieInPoint.getPoint();
        continuousExtensionTangent = tangent.normalize();
        // 单端匝道端头接出只吸附到端头外侧，避免目标点被吸回原 Way 内部。
        bidirectionalExtensionSnap = false;
    }

    private boolean isSourceDirectionSnapMode(AlignmentMode mode) {
        return mode == AlignmentMode.STRAIGHT_LINE
                || mode == AlignmentMode.PI_CIRCULAR_ARC
                || mode == AlignmentMode.LARGE_SWEEP_ARC;
    }

    private TieInPoint sourceDirectionTieIn(EastNorth point, double toleranceMeters) {
        try {
            return ExistingWayAnalyzer.tieInFromNearestWay(point, toleranceMeters, null);
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
        // 预览阶段和正式生成使用同一套模式分发，避免鼠标预览与最终结果不一致。
        if ((mode == AlignmentMode.RAMP_FROM_SELECTED_WAY || mode == AlignmentMode.RAMP_BETWEEN_SELECTED_WAYS)
                && pendingStartTieIn != null
                && controls.size() == 1) {
            EastNorth rampTarget = mode == AlignmentMode.RAMP_FROM_SELECTED_WAY
                    ? snapControlPoint(cursor, mapView)
                    : cursor;
            return sampleSingleTieRamp(rampTarget);
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

    private List<EastNorth> sample(List<EastNorth> points) {
        AlignmentMode mode = controller.getAlignmentMode();
        // 正式生成阶段按模式选择对应采样器，匝道类模式还会带上回退逻辑。
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
        if (controller.isUseSpiralTransitions() && controller.getSpiralLengthMeters() > 0.0) {
            return CompoundRampSampler.sampleSingleTieRamp(
                    pendingStartTieIn,
                    target,
                    controller.getRadiusMeters(),
                    controller.getSpiralLengthMeters(),
                    controller.getSampleIntervalMeters());
        }
        return RampArcSampler.sample(
                pendingStartTieIn,
                target,
                controller.getRadiusMeters(),
                controller.getSampleIntervalMeters());
    }

    private List<EastNorth> sampleTwoTieRamp() {
        if (controller.isAutoOptimizeTwoTieRamp()) {
            // 自动优化优先给出较稳的半径和缓和段长度，减少手工反复试参。
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
            // 普通两端连接先尝试 Hermite；几何不成立时再退回中间直线插入。
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
        // 吸附顺序：强制切线投影优先，其次既有节点，最后才尝试连续延长线。
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
        // 只有鼠标位置足够靠近保留切线时，才把点钉到这条延长线上。
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
    }

    private void clearContinuousExtension() {
        continuousAnchorPoint = null;
        continuousExtensionTangent = null;
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
        // 非双向吸附用于端头延长线和连续作业，只允许沿保留切线正向延伸。
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
        // 直线/圆曲线连续作业和单端匝道端头接出共用同一套延长线吸附状态。
        if (!(mode == AlignmentMode.STRAIGHT_LINE
                || mode == AlignmentMode.PI_CIRCULAR_ARC
                || mode == AlignmentMode.LARGE_SWEEP_ARC
                || mode == AlignmentMode.RAMP_FROM_SELECTED_WAY)
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
        // 切换到可连续作业的模式时，保留上一段终点和切线，方便接着画下一段。
        if (controller.isContinuousMode()
                && continuousAnchorPoint != null
                && continuousExtensionTangent != null
                && (mode == AlignmentMode.STRAIGHT_LINE
                || mode == AlignmentMode.PI_CIRCULAR_ARC
                || mode == AlignmentMode.LARGE_SWEEP_ARC)) {
            controller.keepOnlyControlPoint(continuousAnchorPoint);
            controller.setStatusMessage(tr("Reused the previous segment end tangent as the next segment start direction."));
            MapView mapView = MainApplication.getMap() == null ? null : MainApplication.getMap().mapView;
            if (mapView != null) {
                mapView.repaint();
            }
        } else if (mode != AlignmentMode.STRAIGHT_LINE
                && mode != AlignmentMode.PI_CIRCULAR_ARC
                && mode != AlignmentMode.LARGE_SWEEP_ARC) {
            clearContinuousExtension();
        }
    }

    private void keepEndPointForContinuousWork(List<EastNorth> sampledPoints) {
        List<EastNorth> points = controller.snapshotControlPoints();
        AlignmentMode mode = controller.getAlignmentMode();
        // 生成后是否保留终点，取决于当前模式是否还能把终点当作下一段起点。
        boolean canKeepEndPoint = controller.isContinuousMode()
                && (mode == AlignmentMode.STRAIGHT_LINE
                || mode == AlignmentMode.PI_CIRCULAR_ARC
                || mode == AlignmentMode.LARGE_SWEEP_ARC
                || mode == AlignmentMode.TRANSITION_SPIRAL)
                && !points.isEmpty();

        clearPendingState();
        if (controller.isContinuousMode()
                && mode == AlignmentMode.RAMP_FROM_SELECTED_WAY
                && sampledPoints != null
                && !sampledPoints.isEmpty()) {
            // 单端匝道保留几何终点，并把出口切线作为下一段的直线接续方向。
            EastNorth endPoint = sampledPoints.get(sampledPoints.size() - 1);
            Vector2D tangent = endTangent(sampledPoints);
            controller.keepOnlyControlPoint(endPoint);
            continuousAnchorPoint = endPoint;
            continuousExtensionTangent = tangent;
            bidirectionalExtensionSnap = false;
            if (tangent != null) {
                pendingStartTieIn = new TieInPoint(endPoint, tangent, null, -1, 0.0, 0.0, Double.NaN, 0.0);
            }
            controller.setStatusMessage(tr("Kept the ramp end point and tangent as the next ramp start."));
            return;
        }

        if (canKeepEndPoint) {
            EastNorth endPoint = continuousRetainedPoint(mode, points, sampledPoints);
            controller.keepOnlyControlPoint(endPoint);
            if (mode == AlignmentMode.STRAIGHT_LINE
                    || mode == AlignmentMode.PI_CIRCULAR_ARC
                    || mode == AlignmentMode.LARGE_SWEEP_ARC) {
                continuousAnchorPoint = endPoint;
                continuousExtensionTangent = endTangent(sampledPoints);
                bidirectionalExtensionSnap = false;
            } else {
                clearContinuousExtension();
            }
            if (mode == AlignmentMode.PI_CIRCULAR_ARC || mode == AlignmentMode.LARGE_SWEEP_ARC) {
                controller.setStatusMessage(tr("Kept the curve end point and outgoing tangent for the next straight segment."));
            } else {
                controller.setStatusMessage(tr("Kept the end point as the start of the next line."));
            }
        } else {
            controller.clearControlPoints();
            clearContinuousExtension();
        }
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

    private void showWarning(String message) {
        JOptionPane.showMessageDialog(
                MainApplication.getMainFrame(),
                message,
                tr("Road/Rail Alignment"),
                JOptionPane.WARNING_MESSAGE);
    }
}
