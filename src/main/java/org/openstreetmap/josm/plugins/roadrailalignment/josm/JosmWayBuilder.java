package org.openstreetmap.josm.plugins.roadrailalignment.josm;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.ChangeNodesCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.DeleteCommand;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.plugins.roadrailalignment.FeatureType;
import org.openstreetmap.josm.tools.Logging;

public final class JosmWayBuilder {
    private static final double EXACT_REUSE_TOLERANCE_METERS = 0.001;

    private JosmWayBuilder() {
    }

    public static Way createWay(List<EastNorth> eastNorthPoints, FeatureType featureType) {
        return createWay(eastNorthPoints, featureType, false, 0.0, Collections.emptyList());
    }

    public static Way createWay(
            List<EastNorth> eastNorthPoints,
            FeatureType featureType,
            boolean snapToExistingNodes,
            double nodeSnapToleranceMeters,
            List<EastNorth> anchorPoints) {
        if (eastNorthPoints == null || eastNorthPoints.size() < 2) {
            throw new IllegalArgumentException(tr("At least two points are required to generate a way."));
        }

        DataSet dataSet = ensureEditDataSet();
        List<Node> nodes = new ArrayList<>(eastNorthPoints.size());
        List<Node> newNodes = new ArrayList<>(eastNorthPoints.size());
        for (int i = 0; i < eastNorthPoints.size(); i++) {
            EastNorth point = eastNorthPoints.get(i);
            double reuseTolerance = reuseToleranceForPoint(
                    i,
                    eastNorthPoints.size(),
                    point,
                    snapToExistingNodes,
                    nodeSnapToleranceMeters,
                    anchorPoints);
            Node existingNode = NodeSnapper.findNearestNode(dataSet, point, reuseTolerance).orElse(null);
            if (existingNode != null) {
                appendNodeWithoutConsecutiveDuplicate(nodes, existingNode);
            } else {
                Node node = new Node(point);
                if (appendNodeWithoutConsecutiveDuplicate(nodes, node)) {
                    newNodes.add(node);
                }
            }
        }
        if (nodes.size() < 2) {
            throw new IllegalArgumentException(tr("Not enough valid nodes remain after snapping to generate a way."));
        }

        Way way = new Way();
        way.setNodes(nodes);
        featureType.getTags().forEach(way::put);

        List<Command> commands = new ArrayList<>(newNodes.size() + 1);
        for (Node node : newNodes) {
            commands.add(new AddCommand(dataSet, node));
        }
        commands.add(new AddCommand(dataSet, way));

        UndoRedoHandler.getInstance().add(new SequenceCommand(
                tr("Generate road/rail alignment"),
                commands));
        dataSet.setSelected(way);
        MainApplication.getMap().mapView.repaint();
        Logging.info(tr("Road/Rail Alignment: generated a way with {0} nodes.", nodes.size()));
        return way;
    }

    public static void replaceWayNodes(
            Way way,
            List<EastNorth> eastNorthPoints,
            boolean snapToExistingNodes,
            double nodeSnapToleranceMeters,
            List<EastNorth> anchorPoints) {
        if (way == null || way.getDataSet() == null) {
            return;
        }
        if (eastNorthPoints == null || eastNorthPoints.size() < 2) {
            throw new IllegalArgumentException(tr("At least two points are required to generate a way."));
        }

        DataSet dataSet = way.getDataSet();
        List<Node> oldNodes = new ArrayList<>(way.getNodes());
        List<Node> nodes = new ArrayList<>(eastNorthPoints.size());
        List<Node> newNodes = new ArrayList<>(eastNorthPoints.size());
        for (int i = 0; i < eastNorthPoints.size(); i++) {
            EastNorth point = eastNorthPoints.get(i);
            double reuseTolerance = reuseToleranceForPoint(
                    i,
                    eastNorthPoints.size(),
                    point,
                    snapToExistingNodes,
                    nodeSnapToleranceMeters,
                    anchorPoints);
            Node existingNode = NodeSnapper.findNearestNode(dataSet, point, reuseTolerance).orElse(null);
            if (existingNode != null) {
                appendNodeWithoutConsecutiveDuplicate(nodes, existingNode);
            } else {
                Node node = new Node(point);
                if (appendNodeWithoutConsecutiveDuplicate(nodes, node)) {
                    newNodes.add(node);
                }
            }
        }
        if (nodes.size() < 2) {
            throw new IllegalArgumentException(tr("Not enough valid nodes remain after snapping to generate a way."));
        }

        List<Node> obsoleteNodes = findObsoleteOwnedNodes(way, oldNodes, nodes);
        List<Command> commands = new ArrayList<>(newNodes.size() + 2);
        for (Node node : newNodes) {
            commands.add(new AddCommand(dataSet, node));
        }
        commands.add(new ChangeNodesCommand(dataSet, way, nodes));
        if (!obsoleteNodes.isEmpty()) {
            commands.add(new DeleteCommand(dataSet, obsoleteNodes));
        }
        UndoRedoHandler.getInstance().add(new SequenceCommand(
                tr("Generate road/rail alignment"),
                commands));
        dataSet.setSelected(way);
        MainApplication.getMap().mapView.repaint();
        Logging.info(tr(
                "Road/Rail Alignment: updated a way with {0} nodes and removed {1} obsolete nodes.",
                nodes.size(),
                obsoleteNodes.size()));
    }

    private static DataSet ensureEditDataSet() {
        DataSet dataSet = MainApplication.getLayerManager().getEditDataSet();
        if (dataSet != null) {
            return dataSet;
        }

        OsmDataLayer layer = new OsmDataLayer(new DataSet(), tr("Road/Rail Alignment"), null);
        MainApplication.getLayerManager().addLayer(layer);
        MainApplication.getLayerManager().setActiveLayer(layer);
        return layer.getDataSet();
    }

    private static double reuseToleranceForPoint(
            int index,
            int pointCount,
            EastNorth point,
            boolean snapToExistingNodes,
            double nodeSnapToleranceMeters,
            List<EastNorth> anchorPoints) {
        if (!snapToExistingNodes) {
            return EXACT_REUSE_TOLERANCE_METERS;
        }
        if (index == 0 || index == pointCount - 1 || isControlAnchorPoint(point, anchorPoints)) {
            return Math.max(EXACT_REUSE_TOLERANCE_METERS, nodeSnapToleranceMeters);
        }
        return EXACT_REUSE_TOLERANCE_METERS;
    }

    private static boolean isControlAnchorPoint(EastNorth point, List<EastNorth> anchorPoints) {
        if (point == null || anchorPoints == null) {
            return false;
        }
        for (EastNorth anchorPoint : anchorPoints) {
            if (anchorPoint != null && anchorPoint.isValid()
                    && point.distance(anchorPoint) <= EXACT_REUSE_TOLERANCE_METERS) {
                return true;
            }
        }
        return false;
    }

    private static boolean appendNodeWithoutConsecutiveDuplicate(List<Node> nodes, Node node) {
        if (nodes.isEmpty()) {
            nodes.add(node);
            return true;
        }

        Node previous = nodes.get(nodes.size() - 1);
        if (previous == node) {
            return false;
        }
        EastNorth previousPoint = previous.getEastNorth(ProjectionRegistry.getProjection());
        EastNorth currentPoint = node.getEastNorth(ProjectionRegistry.getProjection());
        if (previousPoint != null && previousPoint.isValid()
                && currentPoint != null && currentPoint.isValid()
                && previousPoint.distance(currentPoint) <= EXACT_REUSE_TOLERANCE_METERS) {
            return false;
        }
        nodes.add(node);
        return true;
    }

    private static List<Node> findObsoleteOwnedNodes(Way way, List<Node> oldNodes, List<Node> replacementNodes) {
        List<Node> obsoleteNodes = new ArrayList<>();
        for (Node node : oldNodes) {
            if (node == null
                    || replacementNodes.contains(node)
                    || obsoleteNodes.contains(node)
                    || node.isDeleted()
                    || node.isIncomplete()
                    || node.isTagged()
                    || hasReferrerOtherThan(node, way)) {
                continue;
            }
            obsoleteNodes.add(node);
        }
        return obsoleteNodes;
    }

    private static boolean hasReferrerOtherThan(Node node, Way way) {
        for (OsmPrimitive referrer : node.getReferrers()) {
            if (referrer != way) {
                return true;
            }
        }
        return false;
    }
}
