package org.openstreetmap.josm.plugins.roadrailalignment.josm;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.MapViewPaintable;

public final class PreviewPainter implements MapViewPaintable {
    private final Color lineColor = new Color(35, 125, 255, 210);
    private final Color pointColor = new Color(255, 180, 35, 230);
    private List<EastNorth> previewPoints = Collections.emptyList();
    private List<EastNorth> controlPoints = Collections.emptyList();

    public void setPreview(List<EastNorth> previewPoints, List<EastNorth> controlPoints) {
        this.previewPoints = previewPoints == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(previewPoints));
        this.controlPoints = controlPoints == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(controlPoints));
    }

    public void clear() {
        setPreview(Collections.emptyList(), Collections.emptyList());
    }

    @Override
    public void paint(Graphics2D graphics, MapView mapView, Bounds bbox) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (previewPoints.size() >= 2) {
                g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(lineColor);
                Point previous = mapView.getPoint(previewPoints.get(0));
                for (int i = 1; i < previewPoints.size(); i++) {
                    Point current = mapView.getPoint(previewPoints.get(i));
                    g.drawLine(previous.x, previous.y, current.x, current.y);
                    previous = current;
                }
            }

            g.setColor(pointColor);
            for (EastNorth point : controlPoints) {
                Point screenPoint = mapView.getPoint(point);
                g.fillOval(screenPoint.x - 4, screenPoint.y - 4, 8, 8);
            }
        } finally {
            g.dispose();
        }
    }
}

