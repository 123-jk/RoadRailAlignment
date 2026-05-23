package org.openstreetmap.josm.plugins.roadrailalignment.model;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.roadrailalignment.geometry.Vector2D;

public final class TieInPoint {
    private final EastNorth point;
    private final Vector2D tangent;
    private final Way sourceWay;
    private final int segmentIndex;
    private final double distanceToClickMeters;
    private final double stationMeters;
    private final double estimatedRadiusMeters;
    private final double estimatedCurvature;

    public TieInPoint(
            EastNorth point,
            Vector2D tangent,
            Way sourceWay,
            int segmentIndex,
            double distanceToClickMeters) {
        this(point, tangent, sourceWay, segmentIndex, distanceToClickMeters, Double.NaN, Double.NaN);
    }

    public TieInPoint(
            EastNorth point,
            Vector2D tangent,
            Way sourceWay,
            int segmentIndex,
            double distanceToClickMeters,
            double stationMeters,
            double estimatedRadiusMeters) {
        this(point, tangent, sourceWay, segmentIndex, distanceToClickMeters, stationMeters, estimatedRadiusMeters,
                Double.isFinite(estimatedRadiusMeters) && estimatedRadiusMeters > 0.0 ? 1.0 / estimatedRadiusMeters : 0.0);
    }

    public TieInPoint(
            EastNorth point,
            Vector2D tangent,
            Way sourceWay,
            int segmentIndex,
            double distanceToClickMeters,
            double stationMeters,
            double estimatedRadiusMeters,
            double estimatedCurvature) {
        this.point = point;
        this.tangent = tangent;
        this.sourceWay = sourceWay;
        this.segmentIndex = segmentIndex;
        this.distanceToClickMeters = distanceToClickMeters;
        this.stationMeters = stationMeters;
        this.estimatedRadiusMeters = estimatedRadiusMeters;
        this.estimatedCurvature = estimatedCurvature;
    }

    public EastNorth getPoint() {
        return point;
    }

    public Vector2D getTangent() {
        return tangent;
    }

    public Way getSourceWay() {
        return sourceWay;
    }

    public int getSegmentIndex() {
        return segmentIndex;
    }

    public double getDistanceToClickMeters() {
        return distanceToClickMeters;
    }

    public double getStationMeters() {
        return stationMeters;
    }

    public double getEstimatedRadiusMeters() {
        return estimatedRadiusMeters;
    }

    public double getEstimatedCurvature() {
        return estimatedCurvature;
    }
}
