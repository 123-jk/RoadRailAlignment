package org.openstreetmap.josm.plugins.roadrailalignment.geometry;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.plugins.roadrailalignment.TieInDirectionMode;
import org.openstreetmap.josm.plugins.roadrailalignment.model.TieInPoint;

final class TieInDirectionUtil {
    private static final double STATION_EPS = 1e-6;

    private TieInDirectionUtil() {
    }

    static Resolved resolve(TieInPoint startTieIn, TieInPoint endTieIn, TieInDirectionMode directionMode) {
        EastNorth start = startTieIn.getPoint();
        EastNorth end = endTieIn.getPoint();
        Vector2D chord = Vector2D.between(start, end);
        Vector2D originalStartTangent = startTieIn.getTangent().normalize();
        Vector2D originalEndTangent = endTieIn.getTangent().normalize();
        double startCurvature = signedCurvatureFromTieIn(startTieIn);
        double endCurvature = signedCurvatureFromTieIn(endTieIn);

        TieInDirectionMode mode = directionMode == null ? TieInDirectionMode.AUTO_CHORD : directionMode;
        if (mode == TieInDirectionMode.AUTO_CHORD) {
            Vector2D startTangent = GeometryUtil.orientToward(originalStartTangent, chord);
            Vector2D endTangent = GeometryUtil.orientToward(originalEndTangent, chord);
            if (startTangent.dot(originalStartTangent) < 0.0) {
                startCurvature = -startCurvature;
            }
            if (endTangent.dot(originalEndTangent) < 0.0) {
                endCurvature = -endCurvature;
            }
            return new Resolved(startTangent, endTangent, startCurvature, endCurvature, false);
        }

        int directionSign = sourceWayDirectionSign(startTieIn, endTieIn);
        if (mode == TieInDirectionMode.REVERSE_SOURCE_WAY) {
            directionSign = -directionSign;
        }
        Vector2D startTangent = directionSign > 0 ? originalStartTangent : originalStartTangent.reverse();
        Vector2D endTangent = directionSign > 0 ? originalEndTangent : originalEndTangent.reverse();
        if (directionSign < 0) {
            startCurvature = -startCurvature;
            endCurvature = -endCurvature;
        }
        return new Resolved(startTangent, endTangent, startCurvature, endCurvature, true);
    }

    private static int sourceWayDirectionSign(TieInPoint startTieIn, TieInPoint endTieIn) {
        if (startTieIn.getSourceWay() != null
                && startTieIn.getSourceWay() == endTieIn.getSourceWay()
                && Double.isFinite(startTieIn.getStationMeters())
                && Double.isFinite(endTieIn.getStationMeters())
                && Math.abs(endTieIn.getStationMeters() - startTieIn.getStationMeters()) > STATION_EPS) {
            return endTieIn.getStationMeters() >= startTieIn.getStationMeters() ? 1 : -1;
        }
        return 1;
    }

    private static double signedCurvatureFromTieIn(TieInPoint tieInPoint) {
        return tieInPoint == null || !Double.isFinite(tieInPoint.getEstimatedCurvature())
                ? 0.0
                : tieInPoint.getEstimatedCurvature();
    }

    static final class Resolved {
        private final Vector2D startTangent;
        private final Vector2D endTangent;
        private final double startCurvature;
        private final double endCurvature;
        private final boolean sourceWayDirection;

        private Resolved(
                Vector2D startTangent,
                Vector2D endTangent,
                double startCurvature,
                double endCurvature,
                boolean sourceWayDirection) {
            this.startTangent = startTangent;
            this.endTangent = endTangent;
            this.startCurvature = startCurvature;
            this.endCurvature = endCurvature;
            this.sourceWayDirection = sourceWayDirection;
        }

        Vector2D getStartTangent() {
            return startTangent;
        }

        Vector2D getEndTangent() {
            return endTangent;
        }

        double getStartCurvature() {
            return startCurvature;
        }

        double getEndCurvature() {
            return endCurvature;
        }

        boolean isSourceWayDirection() {
            return sourceWayDirection;
        }
    }
}
