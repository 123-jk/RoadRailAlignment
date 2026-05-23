package org.openstreetmap.josm.plugins.roadrailalignment;

import org.openstreetmap.josm.gui.IconToggleButton;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

public final class RoadRailPlugin extends Plugin {
    private final AlignmentController controller = new AlignmentController();
    private AlignmentDialog dialog;
    private AlignmentWindow window;
    private AlignmentMapMode mapMode;

    public RoadRailPlugin(PluginInformation info) {
        super(info);
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        super.mapFrameInitialized(oldFrame, newFrame);
        controller.clearControlPoints();
        disposeWindow();

        if (newFrame == null) {
            dialog = null;
            mapMode = null;
            return;
        }

        dialog = new AlignmentDialog(
                controller,
                this::openAlignmentWindow,
                this::clearAlignmentInput,
                this::undoAlignmentInput);
        mapMode = new AlignmentMapMode(controller, this::openAlignmentWindow);
        newFrame.addToggleDialog(dialog);
        newFrame.addMapMode(new IconToggleButton(mapMode));
    }

    private void openAlignmentWindow() {
        if (window == null) {
            window = new AlignmentWindow(controller, this::clearAlignmentInput, this::undoAlignmentInput);
        }
        window.openWindow();
    }

    private void clearAlignmentInput() {
        if (mapMode != null) {
            mapMode.clearCurrentInput();
        } else {
            controller.clearControlPoints();
        }
    }

    private void undoAlignmentInput() {
        if (mapMode != null) {
            mapMode.undoLastStep();
        } else {
            controller.removeLastControlPoint();
        }
    }

    private void disposeWindow() {
        if (window != null) {
            window.dispose();
            window = null;
        }
    }
}
