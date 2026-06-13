package org.openstreetmap.josm.plugins.roadrailalignment;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;

public final class AlignmentPanel extends JPanel implements PropertyChangeListener {
    private final AlignmentController controller;
    private final Runnable openWindowAction;
    private final Runnable clearAction;
    private final Runnable undoAction;
    private final JLabel controlPointLabel = new JLabel();
    private final JLabel statusLabel = new JLabel();

    private JComboBox<FeatureType> typeComboBox;
    private JComboBox<AlignmentMode> modeComboBox;
    private JSpinner intervalSpinner;
    private JSpinner radiusSpinner;
    private JSpinner spiralLengthSpinner;
    private JCheckBox spiralTransitionCheckBox;
    private JCheckBox continuousRampCurvatureCheckBox;
    private JCheckBox autoOptimizeTwoTieCheckBox;
    private JCheckBox applyOptimizedTwoTieParametersCheckBox;
    private JCheckBox continuousCheckBox;
    private JCheckBox nodeSnapCheckBox;
    private JSpinner nodeSnapToleranceSpinner;
    private JSpinner extraLoopTurnsSpinner;
    private JComboBox<TieInDirectionMode> tieInDirectionComboBox;
    private boolean updatingControls;
    private boolean listenerRegistered;

    public AlignmentPanel(AlignmentController controller) {
        this(controller, null, null, null);
    }

    public AlignmentPanel(AlignmentController controller, Runnable openWindowAction) {
        this(controller, openWindowAction, null, null);
    }

    public AlignmentPanel(AlignmentController controller, Runnable openWindowAction, Runnable clearAction) {
        this(controller, openWindowAction, clearAction, null);
    }

    public AlignmentPanel(
            AlignmentController controller,
            Runnable openWindowAction,
            Runnable clearAction,
            Runnable undoAction) {
        super(new GridBagLayout());
        this.controller = controller;
        this.openWindowAction = openWindowAction;
        this.clearAction = clearAction;
        this.undoAction = undoAction;
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        buildControls();
        bindEscapeToClear();
        bindBackspaceToUndo();
        updateAllControls();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (!listenerRegistered) {
            controller.addPropertyChangeListener(this);
            listenerRegistered = true;
        }
        updateAllControls();
    }

    @Override
    public void removeNotify() {
        if (listenerRegistered) {
            controller.removePropertyChangeListener(this);
            listenerRegistered = false;
        }
        super.removeNotify();
    }

    private void buildControls() {
        typeComboBox = new JComboBox<>(FeatureType.values());
        typeComboBox.addActionListener(event -> {
            if (!updatingControls) {
                controller.setFeatureType((FeatureType) typeComboBox.getSelectedItem());
            }
        });

        modeComboBox = new JComboBox<>(AlignmentMode.userVisibleValues());
        modeComboBox.addActionListener(event -> {
            if (!updatingControls) {
                controller.setAlignmentMode((AlignmentMode) modeComboBox.getSelectedItem());
            }
        });

        intervalSpinner = new JSpinner(new SpinnerNumberModel(
                controller.getSampleIntervalMeters(),
                1.0,
                500.0,
                5.0));
        intervalSpinner.addChangeListener(event -> {
            if (!updatingControls) {
                controller.setSampleIntervalMeters(((Number) intervalSpinner.getValue()).doubleValue());
            }
        });

        radiusSpinner = new JSpinner(new SpinnerNumberModel(
                controller.getRadiusMeters(),
                1.0,
                1000000.0,
                50.0));
        radiusSpinner.addChangeListener(event -> {
            if (!updatingControls) {
                controller.setRadiusMeters(((Number) radiusSpinner.getValue()).doubleValue());
            }
        });

        spiralLengthSpinner = new JSpinner(new SpinnerNumberModel(
                controller.getSpiralLengthMeters(),
                0.0,
                5000.0,
                10.0));
        spiralLengthSpinner.addChangeListener(event -> {
            if (!updatingControls) {
                controller.setSpiralLengthMeters(((Number) spiralLengthSpinner.getValue()).doubleValue());
            }
        });

        spiralTransitionCheckBox = new JCheckBox(tr("Use transition spirals"));
        spiralTransitionCheckBox.addActionListener(event -> {
            if (!updatingControls) {
                controller.setUseSpiralTransitions(spiralTransitionCheckBox.isSelected());
            }
        });

        continuousRampCurvatureCheckBox = new JCheckBox(tr("Keep ramp curvature for next segment"));
        continuousRampCurvatureCheckBox.addActionListener(event -> {
            if (!updatingControls) {
                controller.setContinuousRampCurvature(continuousRampCurvatureCheckBox.isSelected());
            }
        });

        autoOptimizeTwoTieCheckBox = new JCheckBox(tr("Auto-optimize max R/transition for two-way connection"));
        autoOptimizeTwoTieCheckBox.addActionListener(event -> {
            if (!updatingControls) {
                controller.setAutoOptimizeTwoTieRamp(autoOptimizeTwoTieCheckBox.isSelected());
            }
        });

        applyOptimizedTwoTieParametersCheckBox = new JCheckBox(tr("Apply optimized parameters after generation"));
        applyOptimizedTwoTieParametersCheckBox.addActionListener(event -> {
            if (!updatingControls) {
                controller.setApplyOptimizedTwoTieRampParameters(
                        applyOptimizedTwoTieParametersCheckBox.isSelected());
            }
        });

        continuousCheckBox = new JCheckBox(tr("Continuous work"));
        continuousCheckBox.addActionListener(event -> {
            if (!updatingControls) {
                controller.setContinuousMode(continuousCheckBox.isSelected());
            }
        });

        nodeSnapCheckBox = new JCheckBox(tr("Snap to existing nodes"));
        nodeSnapCheckBox.addActionListener(event -> {
            if (!updatingControls) {
                controller.setSnapToExistingNodes(nodeSnapCheckBox.isSelected());
            }
        });

        nodeSnapToleranceSpinner = new JSpinner(new SpinnerNumberModel(
                controller.getNodeSnapToleranceMeters(),
                0.0,
                100.0,
                1.0));
        nodeSnapToleranceSpinner.addChangeListener(event -> {
            if (!updatingControls) {
                controller.setNodeSnapToleranceMeters(((Number) nodeSnapToleranceSpinner.getValue()).doubleValue());
            }
        });

        extraLoopTurnsSpinner = new JSpinner(new SpinnerNumberModel(
                controller.getExtraLoopTurns(),
                0,
                20,
                1));
        extraLoopTurnsSpinner.addChangeListener(event -> {
            if (!updatingControls) {
                controller.setExtraLoopTurns(((Number) extraLoopTurnsSpinner.getValue()).intValue());
            }
        });

        tieInDirectionComboBox = new JComboBox<>(TieInDirectionMode.values());
        tieInDirectionComboBox.addActionListener(event -> {
            if (!updatingControls) {
                controller.setTieInDirectionMode((TieInDirectionMode) tieInDirectionComboBox.getSelectedItem());
            }
        });

        JButton recommendSpiralButton = new JButton(tr("Recommend by R"));
        recommendSpiralButton.addActionListener(event -> applyRecommendedSpiralLength());

        JPanel spiralLengthPanel = new JPanel(new BorderLayout(4, 0));
        spiralLengthPanel.add(spiralLengthSpinner, BorderLayout.CENTER);
        spiralLengthPanel.add(recommendSpiralButton, BorderLayout.EAST);

        JButton clearButton = new JButton(tr("Clear control points"));
        clearButton.addActionListener(event -> clearInput());
        JButton undoButton = new JButton(tr("Undo last step"));
        undoButton.addActionListener(event -> undoInput());

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 0;
        gc.anchor = GridBagConstraints.WEST;
        gc.insets = new Insets(0, 0, 6, 6);
        add(new JLabel(tr("Object type:")), gc);

        gc.gridx = 1;
        gc.weightx = 1.0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        add(typeComboBox, gc);

        gc.gridx = 0;
        gc.gridy++;
        gc.weightx = 0.0;
        gc.fill = GridBagConstraints.NONE;
        add(new JLabel(tr("Drawing mode:")), gc);

        gc.gridx = 1;
        gc.weightx = 1.0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        add(modeComboBox, gc);

        gc.gridx = 0;
        gc.gridy++;
        gc.weightx = 0.0;
        gc.fill = GridBagConstraints.NONE;
        add(new JLabel(tr("Sample interval:")), gc);

        gc.gridx = 1;
        gc.weightx = 1.0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        add(intervalSpinner, gc);

        gc.gridx = 0;
        gc.gridy++;
        gc.weightx = 0.0;
        gc.fill = GridBagConstraints.NONE;
        add(new JLabel(tr("Curve/minimum radius:")), gc);

        gc.gridx = 1;
        gc.weightx = 1.0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        add(radiusSpinner, gc);

        gc.gridx = 0;
        gc.gridy++;
        gc.weightx = 0.0;
        gc.fill = GridBagConstraints.NONE;
        add(new JLabel(tr("Transition spiral length:")), gc);

        gc.gridx = 1;
        gc.weightx = 1.0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        add(spiralLengthPanel, gc);

        gc.gridx = 0;
        gc.gridy++;
        gc.gridwidth = 2;
        gc.weightx = 1.0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        add(spiralTransitionCheckBox, gc);

        gc.gridy++;
        add(continuousRampCurvatureCheckBox, gc);

        gc.gridy++;
        add(autoOptimizeTwoTieCheckBox, gc);

        gc.gridy++;
        add(applyOptimizedTwoTieParametersCheckBox, gc);

        gc.gridy++;
        add(continuousCheckBox, gc);

        gc.gridy++;
        add(nodeSnapCheckBox, gc);

        gc.gridx = 0;
        gc.gridy++;
        gc.gridwidth = 1;
        gc.weightx = 0.0;
        gc.fill = GridBagConstraints.NONE;
        add(new JLabel(tr("Node snap tolerance:")), gc);

        gc.gridx = 1;
        gc.weightx = 1.0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        add(nodeSnapToleranceSpinner, gc);

        gc.gridx = 0;
        gc.gridy++;
        gc.weightx = 0.0;
        gc.fill = GridBagConstraints.NONE;
        add(new JLabel(tr("Extra full turns:")), gc);

        gc.gridx = 1;
        gc.weightx = 1.0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        add(extraLoopTurnsSpinner, gc);

        gc.gridx = 0;
        gc.gridy++;
        gc.weightx = 0.0;
        gc.fill = GridBagConstraints.NONE;
        add(new JLabel(tr("Tie-in direction:")), gc);

        gc.gridx = 1;
        gc.weightx = 1.0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        add(tieInDirectionComboBox, gc);

        if (openWindowAction != null) {
            JButton openWindowButton = new JButton(tr("Open standalone window"));
            openWindowButton.addActionListener(event -> openWindowAction.run());
            gc.gridx = 0;
            gc.gridy++;
            gc.gridwidth = 2;
            gc.weightx = 1.0;
            gc.fill = GridBagConstraints.HORIZONTAL;
            add(openWindowButton, gc);
        }

        gc.gridx = 0;
        gc.gridy++;
        gc.gridwidth = 2;
        add(controlPointLabel, gc);

        gc.gridy++;
        add(undoButton, gc);

        gc.gridy++;
        add(clearButton, gc);

        gc.gridy++;
        add(statusLabel, gc);
    }

    private void applyRecommendedSpiralLength() {
        double radius = controller.getRadiusMeters();
        double recommended = AlignmentController.recommendSpiralLengthMeters(radius);
        controller.setUseSpiralTransitions(true);
        controller.setSpiralLengthMeters(recommended);
        controller.setStatusMessage(tr(
                "Recommended transition spiral length {1} m for R={0} m.",
                Math.round(radius),
                Math.round(recommended)));
        updateAllControls();
    }

    private void clearInput() {
        if (clearAction != null) {
            clearAction.run();
        } else {
            controller.clearControlPoints();
            controller.setStatusMessage(tr("Cleared control points."));
        }
    }

    private void undoInput() {
        if (undoAction != null) {
            undoAction.run();
        } else if (controller.removeLastControlPoint()) {
            controller.setStatusMessage(tr(
                    "Undid last step. Control points: {0}/{1}",
                    controller.getControlPointCount(),
                    controller.getAlignmentMode().getRequiredPointCount()));
        }
    }

    private void bindEscapeToClear() {
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                KeyStroke.getKeyStroke("ESCAPE"),
                "clearAlignmentControlPoints");
        getActionMap().put("clearAlignmentControlPoints", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                clearInput();
            }
        });
    }

    private void bindBackspaceToUndo() {
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                KeyStroke.getKeyStroke("BACK_SPACE"),
                "undoAlignmentControlPoint");
        getActionMap().put("undoAlignmentControlPoint", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                undoInput();
            }
        });
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        String property = event.getPropertyName();
        if (AlignmentController.PROP_FEATURE_TYPE.equals(property)
                || AlignmentController.PROP_ALIGNMENT_MODE.equals(property)
                || AlignmentController.PROP_SAMPLE_INTERVAL.equals(property)
                || AlignmentController.PROP_RADIUS.equals(property)
                || AlignmentController.PROP_SPIRAL_LENGTH.equals(property)
                || AlignmentController.PROP_CONTINUOUS_MODE.equals(property)
                || AlignmentController.PROP_USE_SPIRAL_TRANSITIONS.equals(property)
                || AlignmentController.PROP_CONTINUOUS_RAMP_CURVATURE.equals(property)
                || AlignmentController.PROP_AUTO_OPTIMIZE_TWO_TIE.equals(property)
                || AlignmentController.PROP_APPLY_OPTIMIZED_TWO_TIE_PARAMETERS.equals(property)
                || AlignmentController.PROP_SNAP_TO_EXISTING_NODES.equals(property)
                || AlignmentController.PROP_NODE_SNAP_TOLERANCE.equals(property)
                || AlignmentController.PROP_EXTRA_LOOP_TURNS.equals(property)
                || AlignmentController.PROP_TIE_IN_DIRECTION_MODE.equals(property)
                || AlignmentController.PROP_CONTROL_POINTS.equals(property)
                || AlignmentController.PROP_STATUS.equals(property)) {
            updateAllControls();
        }
    }

    private void updateAllControls() {
        updatingControls = true;
        try {
            typeComboBox.setSelectedItem(controller.getFeatureType());
            modeComboBox.setSelectedItem(controller.getAlignmentMode());
            intervalSpinner.setValue(controller.getSampleIntervalMeters());
            radiusSpinner.setValue(controller.getRadiusMeters());
            spiralLengthSpinner.setValue(controller.getSpiralLengthMeters());
            spiralTransitionCheckBox.setSelected(controller.isUseSpiralTransitions());
            continuousRampCurvatureCheckBox.setSelected(controller.isContinuousRampCurvature());
            continuousRampCurvatureCheckBox.setEnabled(controller.isContinuousMode()
                    && controller.isUseSpiralTransitions());
            autoOptimizeTwoTieCheckBox.setSelected(controller.isAutoOptimizeTwoTieRamp());
            applyOptimizedTwoTieParametersCheckBox.setSelected(controller.isApplyOptimizedTwoTieRampParameters());
            applyOptimizedTwoTieParametersCheckBox.setEnabled(controller.isAutoOptimizeTwoTieRamp());
            continuousCheckBox.setSelected(controller.isContinuousMode());
            nodeSnapCheckBox.setSelected(controller.isSnapToExistingNodes());
            nodeSnapToleranceSpinner.setValue(controller.getNodeSnapToleranceMeters());
            extraLoopTurnsSpinner.setValue(controller.getExtraLoopTurns());
            extraLoopTurnsSpinner.setEnabled(controller.getAlignmentMode() == AlignmentMode.LARGE_SWEEP_ARC
                    || controller.getAlignmentMode() == AlignmentMode.RAMP_BETWEEN_SELECTED_WAYS
                    || controller.getAlignmentMode() == AlignmentMode.BASIC_ALIGNMENT);
            tieInDirectionComboBox.setSelectedItem(controller.getTieInDirectionMode());
            tieInDirectionComboBox.setEnabled(controller.getAlignmentMode() == AlignmentMode.RAMP_BETWEEN_SELECTED_WAYS
                    || controller.getAlignmentMode() == AlignmentMode.BASIC_ALIGNMENT);
            updateControlPointLabel();
            updateStatusLabel();
        } finally {
            updatingControls = false;
        }
    }

    private void updateControlPointLabel() {
        controlPointLabel.setText(tr(
                "Control points: {0}/{1}",
                controller.getControlPointCount(),
                controller.getAlignmentMode().getRequiredPointCount()));
    }

    private void updateStatusLabel() {
        String message = controller.getStatusMessage();
        statusLabel.setText(message == null || message.isEmpty() ? tr("Ready") : message);
    }
}
