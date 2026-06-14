package org.openstreetmap.josm.plugins.roadrailalignment.josm;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.List;
import java.util.Collection;
import java.util.concurrent.locks.Lock;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.roadrailalignment.geometry.CurvatureEstimator;
import org.openstreetmap.josm.plugins.roadrailalignment.geometry.GeometryUtil;
import org.openstreetmap.josm.plugins.roadrailalignment.geometry.Vector2D;
import org.openstreetmap.josm.plugins.roadrailalignment.model.TieInPoint;

public final class ExistingWayAnalyzer {
    private static final double MIN_SEGMENT_LENGTH_METERS = 0.01;
    private static final double ENDPOINT_SNAP_EPSILON_METERS = 0.001;

    private ExistingWayAnalyzer() {
    }

    public static TieInPoint tieInFromSelectedWay(EastNorth clickPoint, double maxDistanceMeters) {
        if (clickPoint == null || !clickPoint.isValid()) {
            throw new IllegalArgumentException(tr("Invalid tie-in point."));
        }

        DataSet dataSet = MainApplication.getLayerManager().getEditDataSet();
        if (dataSet == null) {
            throw new IllegalArgumentException(tr("Select one editable existing way before creating a ramp."));
        }

        Way way = getSingleSelectedWay(dataSet);
        TieInPoint tieInPoint = projectToWay(way, clickPoint);
        if (tieInPoint.getDistanceToClickMeters() > maxDistanceMeters) {
            throw new IllegalArgumentException(tr(
                    "The clicked position is too far from the selected way ({0} m).",
                    Math.round(tieInPoint.getDistanceToClickMeters())));
        }
        return tieInPoint;
    }

    public static TieInPoint tieInFromNearestWay(EastNorth clickPoint, double maxDistanceMeters, Way excludedWay) {
        if (clickPoint == null || !clickPoint.isValid()) {
            throw new IllegalArgumentException(tr("Invalid tie-in point."));
        }

        DataSet dataSet = MainApplication.getLayerManager().getEditDataSet();
        if (dataSet == null) {
            throw new IllegalArgumentException(tr("Load an editable data layer before creating a ramp."));
        }

        // 扫描全部可用 Way，选出点击位置投影最近的那一条。
        TieInPoint bestTieIn = null;
        Collection<Way> candidates = dataSet.getWays();
        org.openstreetmap.josm.data.osm.BBox searchBox = SpatialSearchUtil.bboxAround(clickPoint, maxDistanceMeters);
        Lock readLock = dataSet.getReadLock();
        readLock.lock();
        try {
            if (searchBox != null) {
                candidates = dataSet.searchWays(searchBox);
            }
            for (Way way : candidates) {
                if (!isUsableWay(way) || way == excludedWay) {
                    continue;
                }

                TieInPoint tieInPoint = projectToWay(way, clickPoint);
                if (bestTieIn == null || tieInPoint.getDistanceToClickMeters() < bestTieIn.getDistanceToClickMeters()) {
                    bestTieIn = tieInPoint;
                }
            }
        } finally {
            readLock.unlock();
        }

        if (bestTieIn == null) {
            throw new IllegalArgumentException(tr("No existing way was found for tie-in."));
        }
        if (bestTieIn.getDistanceToClickMeters() > maxDistanceMeters) {
            throw new IllegalArgumentException(tr(
                    "The clicked position is too far from the nearest existing way ({0} m).",
                    Math.round(bestTieIn.getDistanceToClickMeters())));
        }
        dataSet.setSelected(bestTieIn.getSourceWay());
        return bestTieIn;
    }

    public static void selectTieInWays(TieInPoint firstTieIn, TieInPoint secondTieIn) {
        DataSet dataSet = MainApplication.getLayerManager().getEditDataSet();
        if (dataSet == null || firstTieIn == null || secondTieIn == null) {
            return;
        }
        Way firstWay = firstTieIn.getSourceWay();
        Way secondWay = secondTieIn.getSourceWay();
        if (firstWay != null && secondWay != null) {
            dataSet.setSelected(firstWay, secondWay);
        }
    }

    public static TieInPoint tieInFromSelectedWays(
            EastNorth clickPoint,
            double maxDistanceMeters,
            Way excludedWay) {
        if (clickPoint == null || !clickPoint.isValid()) {
            throw new IllegalArgumentException(tr("Invalid tie-in point."));
        }

        DataSet dataSet = MainApplication.getLayerManager().getEditDataSet();
        if (dataSet == null) {
            throw new IllegalArgumentException(tr("Select editable existing ways before creating a ramp."));
        }

        List<Way> ways = getSelectedWays(dataSet);
        if (ways.size() < 2 && excludedWay == null) {
            throw new IllegalArgumentException(tr("A two-end ramp requires two selected existing ways."));
        }

        // 双端匝道只在已选中的候选线里找最近的接入点。
        TieInPoint bestTieIn = null;
        for (Way way : ways) {
            if (excludedWay != null && way == excludedWay) {
                continue;
            }
            TieInPoint tieInPoint = projectToWay(way, clickPoint);
            if (bestTieIn == null || tieInPoint.getDistanceToClickMeters() < bestTieIn.getDistanceToClickMeters()) {
                bestTieIn = tieInPoint;
            }
        }

        if (bestTieIn == null) {
            throw new IllegalArgumentException(tr("No other selected way was found for tie-in."));
        }
        if (bestTieIn.getDistanceToClickMeters() > maxDistanceMeters) {
            throw new IllegalArgumentException(tr(
                    "The clicked position is too far from the selected way ({0} m).",
                    Math.round(bestTieIn.getDistanceToClickMeters())));
        }
        return bestTieIn;
    }

    public static String describeTieIn(TieInPoint tieInPoint) {
        if (tieInPoint == null) {
            return "";
        }
        String radiusText = Double.isFinite(tieInPoint.getEstimatedRadiusMeters())
                ? tr("Estimated radius about {0} m, curvature {1}",
                        Math.round(tieInPoint.getEstimatedRadiusMeters()),
                        String.format("%.6f", tieInPoint.getEstimatedCurvature()))
                : tr("Straight line or unknown radius");
        return tr(
                "Tie-in: segment {0}, station about {1} m, offset {2} m, {3}",
                tieInPoint.getSegmentIndex() + 1,
                Math.round(tieInPoint.getStationMeters()),
                Math.round(tieInPoint.getDistanceToClickMeters()),
                radiusText);
    }

    public static Vector2D endpointExtensionTangent(TieInPoint tieInPoint) {
        if (tieInPoint == null || tieInPoint.getSourceWay() == null || tieInPoint.getPoint() == null) {
            return null;
        }

        // 端头续接只认 Way 的真实首末节点；中间线段投影仍按普通匝道处理。
        Way way = tieInPoint.getSourceWay();
        EastNorth point = tieInPoint.getPoint();
        EastNorth first = eastNorth(way.getNode(0));
        EastNorth last = eastNorth(way.getNode(way.getNodesCount() - 1));
        if (first == null || last == null) {
            return null;
        }

        if (point.distance(first) <= ENDPOINT_SNAP_EPSILON_METERS) {
            // 首端的外延方向与第一段节点顺序相反。
            Vector2D tangent = firstSegmentTangent(way);
            return tangent == null ? null : tangent.reverse();
        }
        if (point.distance(last) <= ENDPOINT_SNAP_EPSILON_METERS) {
            // 末端的外延方向沿最后一段节点顺序继续向前。
            return lastSegmentTangent(way);
        }
        return null;
    }

    private static Way getSingleSelectedWay(DataSet dataSet) {
        List<Way> selectedWays = getSelectedWays(dataSet);

        if (selectedWays.isEmpty()) {
            // 没有显式选中时，沿用最后一次选中的 Way，降低起步成本。
            Way lastSelectedWay = dataSet.getLastSelectedWay();
            if (lastSelectedWay != null && lastSelectedWay.isUsable()) {
                selectedWays.add(lastSelectedWay);
            }
        }

        if (selectedWays.size() != 1) {
            throw new IllegalArgumentException(tr("Select only one existing way as the ramp start."));
        }
        Way way = selectedWays.get(0);
        if (!isUsableWay(way)) {
            throw new IllegalArgumentException(tr("The selected way needs at least two complete nodes."));
        }
        return way;
    }

    private static List<Way> getSelectedWays(DataSet dataSet) {
        List<Way> selectedWays = new ArrayList<>();
        for (OsmPrimitive primitive : dataSet.getSelectedNodesAndWays()) {
            if (primitive instanceof Way && primitive.isUsable()) {
                selectedWays.add((Way) primitive);
            }
        }
        return selectedWays;
    }

    private static boolean isUsableWay(Way way) {
        return way != null && way.isUsable() && way.getNodesCount() >= 2 && !way.hasIncompleteNodes();
    }

    private static Vector2D firstSegmentTangent(Way way) {
        // 跳过重复节点或极短线段，取首端附近第一个有效方向。
        for (int i = 0; i < way.getNodesCount() - 1; i++) {
            Vector2D tangent = segmentTangent(way.getNode(i), way.getNode(i + 1));
            if (tangent != null) {
                return tangent;
            }
        }
        return null;
    }

    private static Vector2D lastSegmentTangent(Way way) {
        // 跳过重复节点或极短线段，取末端附近最后一个有效方向。
        for (int i = way.getNodesCount() - 2; i >= 0; i--) {
            Vector2D tangent = segmentTangent(way.getNode(i), way.getNode(i + 1));
            if (tangent != null) {
                return tangent;
            }
        }
        return null;
    }

    private static Vector2D segmentTangent(Node firstNode, Node secondNode) {
        EastNorth first = eastNorth(firstNode);
        EastNorth second = eastNorth(secondNode);
        if (first == null || second == null) {
            return null;
        }
        Vector2D segment = Vector2D.between(first, second);
        if (segment.lengthSquared() < MIN_SEGMENT_LENGTH_METERS * MIN_SEGMENT_LENGTH_METERS) {
            return null;
        }
        return segment.normalize();
    }

    private static EastNorth eastNorth(Node node) {
        if (node == null) {
            return null;
        }
        EastNorth point = node.getEastNorth(ProjectionRegistry.getProjection());
        return point != null && point.isValid() ? point : null;
    }

    public static TieInPoint projectToWay(Way way, EastNorth clickPoint) {
        EastNorth bestPoint = null;
        Vector2D bestTangent = null;
        int bestSegmentIndex = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        double bestStation = 0.0;
        double stationAtSegmentStart = 0.0;

        // 对每一段做正交投影，保留距离点击点最近的结果作为接入点。
        for (int i = 0; i < way.getNodesCount() - 1; i++) {
            Node firstNode = way.getNode(i);
            Node secondNode = way.getNode(i + 1);
            EastNorth first = firstNode.getEastNorth(ProjectionRegistry.getProjection());
            EastNorth second = secondNode.getEastNorth(ProjectionRegistry.getProjection());
            if (first == null || second == null || !first.isValid() || !second.isValid()) {
                continue;
            }

            Vector2D segment = Vector2D.between(first, second);
            double lengthSquared = segment.lengthSquared();
            if (lengthSquared < MIN_SEGMENT_LENGTH_METERS * MIN_SEGMENT_LENGTH_METERS) {
                continue;
            }

            Vector2D fromFirstToClick = Vector2D.between(first, clickPoint);
            double t = GeometryUtil.clamp(fromFirstToClick.dot(segment) / lengthSquared, 0.0, 1.0);
            EastNorth projected = new EastNorth(
                    first.east() + segment.x() * t,
                    first.north() + segment.y() * t);
            double distance = projected.distance(clickPoint);
            if (distance < bestDistance) {
                bestPoint = projected;
                bestTangent = segment.normalize();
                bestSegmentIndex = i;
                bestDistance = distance;
                bestStation = stationAtSegmentStart + Math.sqrt(lengthSquared) * t;
            }
            stationAtSegmentStart += Math.sqrt(lengthSquared);
        }

        if (bestPoint == null || bestTangent == null) {
            throw new IllegalArgumentException(tr("The selected way has no usable segments."));
        }

        return new TieInPoint(
                bestPoint,
                bestTangent,
                way,
                bestSegmentIndex,
                bestDistance,
                bestStation,
                estimateLocalRadius(way, bestSegmentIndex),
                estimateLocalCurvature(way, bestSegmentIndex));
    }

    private static double estimateLocalRadius(Way way, int segmentIndex) {
        // 用命中的线段及其邻点估算局部半径，给匝道方向和曲率判断用。
        EastNorth[] points = getLocalTriplet(way, segmentIndex);
        if (points == null) {
            return Double.NaN;
        }
        double radius = CurvatureEstimator.radiusFromThreePoints(points[0], points[1], points[2]);
        return Double.isFinite(radius) ? radius : Double.NaN;
    }

    private static double estimateLocalCurvature(Way way, int segmentIndex) {
        EastNorth[] points = getLocalTriplet(way, segmentIndex);
        if (points == null) {
            return 0.0;
        }
        double radius = CurvatureEstimator.radiusFromThreePoints(points[0], points[1], points[2]);
        if (!Double.isFinite(radius) || radius <= 1.0) {
            return 0.0;
        }
        Vector2D in = Vector2D.between(points[0], points[1]).normalize();
        Vector2D out = Vector2D.between(points[1], points[2]).normalize();
        double sign = Math.signum(in.cross(out));
        return sign == 0.0 ? 0.0 : sign / radius;
    }

    private static EastNorth[] getLocalTriplet(Way way, int segmentIndex) {
        if (way == null || way.getNodesCount() < 3) {
            return null;
        }
        // 在折线边界处夹紧中间点，尽量取到命中段附近的三个有效节点。
        int nodeCount = way.getNodesCount();
        int middle = Math.max(1, Math.min(nodeCount - 2, segmentIndex));
        int firstIndex = middle - 1;
        int secondIndex = middle;
        int thirdIndex = middle + 1;
        if (firstIndex < 0 || thirdIndex >= nodeCount) {
            return null;
        }
        EastNorth first = way.getNode(firstIndex).getEastNorth(ProjectionRegistry.getProjection());
        EastNorth second = way.getNode(secondIndex).getEastNorth(ProjectionRegistry.getProjection());
        EastNorth third = way.getNode(thirdIndex).getEastNorth(ProjectionRegistry.getProjection());
        if (first == null || second == null || third == null
                || !first.isValid() || !second.isValid() || !third.isValid()) {
            return null;
        }
        return new EastNorth[] { first, second, third };
    }
}
