package org.openstreetmap.josm.plugins.roadrailalignment;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JDialog;
import javax.swing.WindowConstants;

import org.openstreetmap.josm.gui.MainApplication;

public final class AlignmentWindow extends JDialog {
    public AlignmentWindow(AlignmentController controller) {
        this(controller, null, null);
    }

    public AlignmentWindow(AlignmentController controller, Runnable clearAction) {
        this(controller, clearAction, null);
    }

    public AlignmentWindow(AlignmentController controller, Runnable clearAction, Runnable undoAction) {
        super(MainApplication.getMainFrame(), tr("Road/Rail Alignment - Standalone Window"), false);
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(new AlignmentPanel(controller, null, clearAction, undoAction), BorderLayout.CENTER);
        setMinimumSize(new Dimension(300, 360));
        pack();
        setLocationRelativeTo(MainApplication.getMainFrame());
    }

    public void openWindow() {
        if (!isVisible()) {
            setVisible(true);
        }
        toFront();
        requestFocus();
    }
}
