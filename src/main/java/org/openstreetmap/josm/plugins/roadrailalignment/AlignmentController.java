package org.openstreetmap.josm.plugins.roadrailalignment;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.openstreetmap.josm.data.coor.EastNorth;

/**
 * Shared state between the map mode and the side panel.
 */
public final class AlignmentController {
    private static final double RECOMMENDED_SPIRAL_RATIO = 0.3;
    private static final double MIN_RECOMMENDED_SPIRAL_LENGTH = 20.0;
    private static final double MAX_RECOMMENDED_SPIRAL_LENGTH = 500.0;

    public static final String PROP_FEATURE_TYPE = "featureType";
    public static final String PROP_ALIGNMENT_MODE = "alignmentMode";
    public static final String PROP_SAMPLE_INTERVAL = "sampleIntervalMeters";
    public static final String PROP_RADIUS = "radiusMeters";
    public static final String PROP_SPIRAL_LENGTH = "spiralLengthMeters";
    public static final String PROP_CONTROL_POINTS = "controlPoints";
    public static final String PROP_STATUS = "statusMessage";
    public static final String PROP_CONTINUOUS_MODE = "continuousMode";
    public static final String PROP_USE_SPIRAL_TRANSITIONS = "useSpiralTransitions";
    public static final String PROP_CONTINUOUS_RAMP_CURVATURE = "continuousRampCurvature";
    public static final String PROP_AUTO_OPTIMIZE_TWO_TIE = "autoOptimizeTwoTieRamp";
    public static final String PROP_APPLY_OPTIMIZED_TWO_TIE_PARAMETERS = "applyOptimizedTwoTieRampParameters";
    public static final String PROP_SNAP_TO_EXISTING_NODES = "snapToExistingNodes";
    public static final String PROP_NODE_SNAP_TOLERANCE = "nodeSnapToleranceMeters";
    public static final String PROP_EXTRA_LOOP_TURNS = "extraLoopTurns";
    public static final String PROP_TIE_IN_DIRECTION_MODE = "tieInDirectionMode";

    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
    private final List<EastNorth> controlPoints = new ArrayList<>();
    private FeatureType featureType = FeatureType.ROAD;
    private AlignmentMode alignmentMode = AlignmentMode.BASIC_ALIGNMENT;
    private double sampleIntervalMeters = 20.0;
    private double radiusMeters = 200.0;
    private double spiralLengthMeters = 60.0;
    private String statusMessage = "";
    private boolean continuousMode = true;
    private boolean useSpiralTransitions = true;
    private boolean continuousRampCurvature = false;
    private boolean autoOptimizeTwoTieRamp = true;
    private boolean applyOptimizedTwoTieRampParameters = false;
    private boolean snapToExistingNodes = true;
    private double nodeSnapToleranceMeters = 8.0;
    private int extraLoopTurns = 0;
    private TieInDirectionMode tieInDirectionMode = TieInDirectionMode.AUTO_CHORD;

    public FeatureType getFeatureType() {
        return featureType;
    }

    public void setFeatureType(FeatureType featureType) {
        if (featureType == null || featureType == this.featureType) {
            return;
        }
        FeatureType old = this.featureType;
        this.featureType = featureType;
        propertyChangeSupport.firePropertyChange(PROP_FEATURE_TYPE, old, featureType);
    }

    public AlignmentMode getAlignmentMode() {
        return alignmentMode;
    }

    public void setAlignmentMode(AlignmentMode alignmentMode) {
        if (alignmentMode == null || alignmentMode == this.alignmentMode) {
            return;
        }
        AlignmentMode old = this.alignmentMode;
        this.alignmentMode = alignmentMode;
        clearControlPoints();
        propertyChangeSupport.firePropertyChange(PROP_ALIGNMENT_MODE, old, alignmentMode);
    }

    public double getSampleIntervalMeters() {
        return sampleIntervalMeters;
    }

    public void setSampleIntervalMeters(double sampleIntervalMeters) {
        double normalized = Math.max(1.0, sampleIntervalMeters);
        double old = this.sampleIntervalMeters;
        if (Double.compare(old, normalized) == 0) {
            return;
        }
        this.sampleIntervalMeters = normalized;
        propertyChangeSupport.firePropertyChange(PROP_SAMPLE_INTERVAL, old, normalized);
    }

    public double getRadiusMeters() {
        return radiusMeters;
    }

    public void setRadiusMeters(double radiusMeters) {
        double normalized = Math.max(1.0, radiusMeters);
        double old = this.radiusMeters;
        if (Double.compare(old, normalized) == 0) {
            return;
        }
        this.radiusMeters = normalized;
        propertyChangeSupport.firePropertyChange(PROP_RADIUS, old, normalized);
    }

    public double getSpiralLengthMeters() {
        return spiralLengthMeters;
    }

    public static double recommendSpiralLengthMeters(double radiusMeters) {
        double radius = Math.max(1.0, radiusMeters);
        double recommended = radius * RECOMMENDED_SPIRAL_RATIO;
        recommended = Math.max(MIN_RECOMMENDED_SPIRAL_LENGTH,
                Math.min(MAX_RECOMMENDED_SPIRAL_LENGTH, recommended));
        return Math.round(recommended / 5.0) * 5.0;
    }

    public void setSpiralLengthMeters(double spiralLengthMeters) {
        double normalized = Math.max(0.0, spiralLengthMeters);
        double old = this.spiralLengthMeters;
        if (Double.compare(old, normalized) == 0) {
            return;
        }
        this.spiralLengthMeters = normalized;
        propertyChangeSupport.firePropertyChange(PROP_SPIRAL_LENGTH, old, normalized);
    }

    public void addControlPoint(EastNorth point) {
        if (point == null || !point.isValid()) {
            return;
        }
        List<EastNorth> old = snapshotControlPoints();
        controlPoints.add(point);
        propertyChangeSupport.firePropertyChange(PROP_CONTROL_POINTS, old, snapshotControlPoints());
    }

    public void clearControlPoints() {
        if (controlPoints.isEmpty()) {
            return;
        }
        List<EastNorth> old = snapshotControlPoints();
        controlPoints.clear();
        propertyChangeSupport.firePropertyChange(PROP_CONTROL_POINTS, old, snapshotControlPoints());
    }

    public boolean removeLastControlPoint() {
        if (controlPoints.isEmpty()) {
            return false;
        }
        List<EastNorth> old = snapshotControlPoints();
        controlPoints.remove(controlPoints.size() - 1);
        propertyChangeSupport.firePropertyChange(PROP_CONTROL_POINTS, old, snapshotControlPoints());
        return true;
    }

    public void keepOnlyControlPoint(EastNorth point) {
        List<EastNorth> old = snapshotControlPoints();
        controlPoints.clear();
        if (point != null && point.isValid()) {
            controlPoints.add(point);
        }
        propertyChangeSupport.firePropertyChange(PROP_CONTROL_POINTS, old, snapshotControlPoints());
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        String normalized = statusMessage == null ? "" : statusMessage;
        String old = this.statusMessage;
        if (old.equals(normalized)) {
            return;
        }
        this.statusMessage = normalized;
        propertyChangeSupport.firePropertyChange(PROP_STATUS, old, normalized);
    }

    public boolean isContinuousMode() {
        return continuousMode;
    }

    public void setContinuousMode(boolean continuousMode) {
        boolean old = this.continuousMode;
        if (old == continuousMode) {
            return;
        }
        this.continuousMode = continuousMode;
        propertyChangeSupport.firePropertyChange(PROP_CONTINUOUS_MODE, old, continuousMode);
    }

    public boolean isUseSpiralTransitions() {
        return useSpiralTransitions;
    }

    public void setUseSpiralTransitions(boolean useSpiralTransitions) {
        boolean old = this.useSpiralTransitions;
        if (old == useSpiralTransitions) {
            return;
        }
        this.useSpiralTransitions = useSpiralTransitions;
        propertyChangeSupport.firePropertyChange(PROP_USE_SPIRAL_TRANSITIONS, old, useSpiralTransitions);
    }

    public boolean isContinuousRampCurvature() {
        return continuousRampCurvature;
    }

    public void setContinuousRampCurvature(boolean continuousRampCurvature) {
        boolean old = this.continuousRampCurvature;
        if (old == continuousRampCurvature) {
            return;
        }
        this.continuousRampCurvature = continuousRampCurvature;
        propertyChangeSupport.firePropertyChange(PROP_CONTINUOUS_RAMP_CURVATURE, old, continuousRampCurvature);
    }

    public boolean isAutoOptimizeTwoTieRamp() {
        return autoOptimizeTwoTieRamp;
    }

    public void setAutoOptimizeTwoTieRamp(boolean autoOptimizeTwoTieRamp) {
        boolean old = this.autoOptimizeTwoTieRamp;
        if (old == autoOptimizeTwoTieRamp) {
            return;
        }
        this.autoOptimizeTwoTieRamp = autoOptimizeTwoTieRamp;
        propertyChangeSupport.firePropertyChange(PROP_AUTO_OPTIMIZE_TWO_TIE, old, autoOptimizeTwoTieRamp);
    }

    public boolean isApplyOptimizedTwoTieRampParameters() {
        return applyOptimizedTwoTieRampParameters;
    }

    public void setApplyOptimizedTwoTieRampParameters(boolean applyOptimizedTwoTieRampParameters) {
        boolean old = this.applyOptimizedTwoTieRampParameters;
        if (old == applyOptimizedTwoTieRampParameters) {
            return;
        }
        this.applyOptimizedTwoTieRampParameters = applyOptimizedTwoTieRampParameters;
        propertyChangeSupport.firePropertyChange(
                PROP_APPLY_OPTIMIZED_TWO_TIE_PARAMETERS,
                old,
                applyOptimizedTwoTieRampParameters);
    }

    public boolean isSnapToExistingNodes() {
        return snapToExistingNodes;
    }

    public void setSnapToExistingNodes(boolean snapToExistingNodes) {
        boolean old = this.snapToExistingNodes;
        if (old == snapToExistingNodes) {
            return;
        }
        this.snapToExistingNodes = snapToExistingNodes;
        propertyChangeSupport.firePropertyChange(PROP_SNAP_TO_EXISTING_NODES, old, snapToExistingNodes);
    }

    public double getNodeSnapToleranceMeters() {
        return nodeSnapToleranceMeters;
    }

    public void setNodeSnapToleranceMeters(double nodeSnapToleranceMeters) {
        double normalized = Math.max(0.0, nodeSnapToleranceMeters);
        double old = this.nodeSnapToleranceMeters;
        if (Double.compare(old, normalized) == 0) {
            return;
        }
        this.nodeSnapToleranceMeters = normalized;
        propertyChangeSupport.firePropertyChange(PROP_NODE_SNAP_TOLERANCE, old, normalized);
    }

    public int getExtraLoopTurns() {
        return extraLoopTurns;
    }

    public void setExtraLoopTurns(int extraLoopTurns) {
        int normalized = Math.max(0, extraLoopTurns);
        int old = this.extraLoopTurns;
        if (old == normalized) {
            return;
        }
        this.extraLoopTurns = normalized;
        propertyChangeSupport.firePropertyChange(PROP_EXTRA_LOOP_TURNS, old, normalized);
    }

    public TieInDirectionMode getTieInDirectionMode() {
        return tieInDirectionMode;
    }

    public void setTieInDirectionMode(TieInDirectionMode tieInDirectionMode) {
        TieInDirectionMode normalized = tieInDirectionMode == null
                ? TieInDirectionMode.AUTO_CHORD
                : tieInDirectionMode;
        TieInDirectionMode old = this.tieInDirectionMode;
        if (old == normalized) {
            return;
        }
        this.tieInDirectionMode = normalized;
        propertyChangeSupport.firePropertyChange(PROP_TIE_IN_DIRECTION_MODE, old, normalized);
    }

    public int getControlPointCount() {
        return controlPoints.size();
    }

    public List<EastNorth> snapshotControlPoints() {
        return Collections.unmodifiableList(new ArrayList<>(controlPoints));
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }
}
