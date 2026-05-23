package org.openstreetmap.josm.plugins.roadrailalignment;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.event.KeyEvent;

import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.tools.Shortcut;

public final class AlignmentDialog extends ToggleDialog {
    public AlignmentDialog(AlignmentController controller, Runnable openWindowAction) {
        this(controller, openWindowAction, null, null);
    }

    public AlignmentDialog(AlignmentController controller, Runnable openWindowAction, Runnable clearAction) {
        this(controller, openWindowAction, clearAction, null);
    }

    public AlignmentDialog(
            AlignmentController controller,
            Runnable openWindowAction,
            Runnable clearAction,
            Runnable undoAction) {
        super(
                tr("道路/铁路线形"),
                "roadrailalignment",
                tr("绘制道路、铁路和匝道平面线形"),
                Shortcut.registerShortcut(
                        "subwindow:roadrailalignment",
                        tr("开关：{0}", tr("道路/铁路线形")),
                        KeyEvent.VK_R,
                        Shortcut.ALT_CTRL),
                220);
        setLayout(new BorderLayout());
        add(new AlignmentPanel(controller, openWindowAction, clearAction, undoAction), BorderLayout.NORTH);
    }
}
