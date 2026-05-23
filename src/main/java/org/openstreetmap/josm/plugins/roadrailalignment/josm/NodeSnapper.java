package org.openstreetmap.josm.plugins.roadrailalignment.josm;

import java.util.Optional;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.gui.MainApplication;

public final class NodeSnapper {
    private NodeSnapper() {
    }

    public static Optional<EastNorth> snapToExistingNode(EastNorth point, double toleranceMeters) {
        return findNearestNode(point, toleranceMeters)
                .map(node -> node.getEastNorth(ProjectionRegistry.getProjection()))
                .filter(nodePoint -> nodePoint != null && nodePoint.isValid());
    }

    public static Optional<Node> findNearestNode(EastNorth point, double toleranceMeters) {
        DataSet dataSet = MainApplication.getLayerManager().getEditDataSet();
        return findNearestNode(dataSet, point, toleranceMeters);
    }

    public static Optional<Node> findNearestNode(DataSet dataSet, EastNorth point, double toleranceMeters) {
        if (point == null || !point.isValid() || toleranceMeters <= 0.0) {
            return Optional.empty();
        }
        if (dataSet == null) {
            return Optional.empty();
        }

        Node best = null;
        double bestDistance = toleranceMeters;
        for (Node node : dataSet.getNodes()) {
            if (!node.isUsable()) {
                continue;
            }
            EastNorth nodePoint = node.getEastNorth(ProjectionRegistry.getProjection());
            if (nodePoint == null || !nodePoint.isValid()) {
                continue;
            }
            double distance = nodePoint.distance(point);
            if (distance <= bestDistance) {
                best = node;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }
}
