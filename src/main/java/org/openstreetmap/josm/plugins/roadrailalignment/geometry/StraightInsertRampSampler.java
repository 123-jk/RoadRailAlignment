package org.openstreetmap.josm.plugins.roadrailalignment.geometry;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.plugins.roadrailalignment.TieInDirectionMode;
import org.openstreetmap.josm.plugins.roadrailalignment.model.TieInPoint;

public final class StraightInsertRampSampler {
    private static final double EPS = 1e-9;
    private static final double TWO_PI = Math.PI * 2.0;

    private StraightInsertRampSampler() {
    }

    public static List<EastNorth> sample(
            TieInPoint startTieIn,
            TieInPoint endTieIn,
            double radiusMeters,
            double intervalMeters) {
        return sample(startTieIn, endTieIn, radiusMeters, 0, intervalMeters);
    }

    public static List<EastNorth> sample(
            TieInPoint startTieIn,
            TieInPoint endTieIn,
            double radiusMeters,
            int extraLoopTurns,
            double intervalMeters) {
        return sample(startTieIn, endTieIn, radiusMeters, extraLoopTurns, TieInDirectionMode.AUTO_CHORD, intervalMeters);
    }

    public static List<EastNorth> sample(
            TieInPoint startTieIn,
            TieInPoint endTieIn,
            double radiusMeters,
            int extraLoopTurns,
            TieInDirectionMode directionMode,
            double intervalMeters) {
        if (startTieIn == null || endTieIn == null) {
            throw new IllegalArgumentException(tr("Two tie-in points are required."));
        }

        EastNorth start = startTieIn.getPoint();
        EastNorth end = endTieIn.getPoint();
        TieInDirectionUtil.Resolved direction = TieInDirectionUtil.resolve(startTieIn, endTieIn, directionMode);
        Vector2D startTangent = direction.getStartTangent();
        Vector2D endTangent = direction.getEndTangent();
        return sample(start, startTangent, end, endTangent, radiusMeters, extraLoopTurns, intervalMeters);
    }

    public static List<EastNorth> sample(
            EastNorth start,
            Vector2D startTangent,
            EastNorth end,
            Vector2D endTangent,
            double radiusMeters,
            double intervalMeters) {
        return sample(start, startTangent, end, endTangent, radiusMeters, 0, intervalMeters);
    }

    public static List<EastNorth> sample(
            EastNorth start,
            Vector2D startTangent,
            EastNorth end,
            Vector2D endTangent,
            double radiusMeters,
            int extraLoopTurns,
            double intervalMeters) {
        if (start == null || end == null || !start.isValid() || !end.isValid()
                || startTangent == null || endTangent == null) {
            throw new IllegalArgumentException(tr("Invalid tie-in points or tangents for the straight-insert connection."));
        }

        double radius = Math.max(1.0, radiusMeters);
        double dx = end.east() - start.east();
        double dy = end.north() - start.north();
        double distance = Math.hypot(dx, dy);
        if (distance < EPS) {
            throw new IllegalArgumentException(tr("The two tie-in points are too close."));
        }

        double startHeading = Math.atan2(startTangent.y(), startTangent.x());
        double endHeading = Math.atan2(endTangent.y(), endTangent.x());
        double chordHeading = Math.atan2(dy, dx);
        double normalizedDistance = distance / radius;
        double alpha = mod2pi(startHeading - chordHeading);
        double beta = mod2pi(endHeading - chordHeading);

        Path best = null;
        best = better(best, lsl(alpha, beta, normalizedDistance));
        best = better(best, rsr(alpha, beta, normalizedDistance));
        best = better(best, lsr(alpha, beta, normalizedDistance));
        best = better(best, rsl(alpha, beta, normalizedDistance));
        best = better(best, rlr(alpha, beta, normalizedDistance));
        best = better(best, lrl(alpha, beta, normalizedDistance));
        if (best == null) {
            throw new IllegalArgumentException(tr("Could not generate an arc/straight combination for the two-way connection."));
        }
        best = best.withExtraLoops(Math.max(0, extraLoopTurns));

        List<EastNorth> points = samplePath(start, startHeading, radius, best, intervalMeters);
        if (points.get(points.size() - 1).distance(end) > Math.max(0.01, radius * 1e-6)) {
            throw new IllegalArgumentException(tr("The arc/straight two-way connection geometry is invalid."));
        }
        points.set(points.size() - 1, end);
        return points;
    }

    private static Path better(Path first, Path second) {
        if (second == null) {
            return first;
        }
        if (first == null || second.length() < first.length()) {
            return second;
        }
        return first;
    }

    private static Path lsl(double alpha, double beta, double distance) {
        double sinA = Math.sin(alpha);
        double sinB = Math.sin(beta);
        double cosA = Math.cos(alpha);
        double cosB = Math.cos(beta);
        double cosAB = Math.cos(alpha - beta);
        double pSquared = 2.0 + distance * distance - 2.0 * cosAB + 2.0 * distance * (sinA - sinB);
        if (pSquared < -EPS) {
            return null;
        }
        double p = Math.sqrt(Math.max(0.0, pSquared));
        double tmp = Math.atan2(cosB - cosA, distance + sinA - sinB);
        return new Path('L', mod2pi(-alpha + tmp), p, 'L', mod2pi(beta - tmp));
    }

    private static Path rsr(double alpha, double beta, double distance) {
        double sinA = Math.sin(alpha);
        double sinB = Math.sin(beta);
        double cosA = Math.cos(alpha);
        double cosB = Math.cos(beta);
        double cosAB = Math.cos(alpha - beta);
        double pSquared = 2.0 + distance * distance - 2.0 * cosAB + 2.0 * distance * (sinB - sinA);
        if (pSquared < -EPS) {
            return null;
        }
        double p = Math.sqrt(Math.max(0.0, pSquared));
        double tmp = Math.atan2(cosA - cosB, distance - sinA + sinB);
        return new Path('R', mod2pi(alpha - tmp), p, 'R', mod2pi(-beta + tmp));
    }

    private static Path lsr(double alpha, double beta, double distance) {
        double sinA = Math.sin(alpha);
        double sinB = Math.sin(beta);
        double cosA = Math.cos(alpha);
        double cosB = Math.cos(beta);
        double cosAB = Math.cos(alpha - beta);
        double pSquared = -2.0 + distance * distance + 2.0 * cosAB + 2.0 * distance * (sinA + sinB);
        if (pSquared < -EPS) {
            return null;
        }
        double p = Math.sqrt(Math.max(0.0, pSquared));
        double tmp = Math.atan2(-cosA - cosB, distance + sinA + sinB) - Math.atan2(-2.0, p);
        return new Path('L', mod2pi(-alpha + tmp), p, 'R', mod2pi(-beta + tmp));
    }

    private static Path rsl(double alpha, double beta, double distance) {
        double sinA = Math.sin(alpha);
        double sinB = Math.sin(beta);
        double cosA = Math.cos(alpha);
        double cosB = Math.cos(beta);
        double cosAB = Math.cos(alpha - beta);
        double pSquared = distance * distance - 2.0 + 2.0 * cosAB - 2.0 * distance * (sinA + sinB);
        if (pSquared < -EPS) {
            return null;
        }
        double p = Math.sqrt(Math.max(0.0, pSquared));
        double tmp = Math.atan2(cosA + cosB, distance - sinA - sinB) - Math.atan2(2.0, p);
        return new Path('R', mod2pi(alpha - tmp), p, 'L', mod2pi(beta - tmp));
    }

    private static Path rlr(double alpha, double beta, double distance) {
        double sinA = Math.sin(alpha);
        double sinB = Math.sin(beta);
        double cosA = Math.cos(alpha);
        double cosB = Math.cos(beta);
        double cosAB = Math.cos(alpha - beta);
        double tmp = (6.0 - distance * distance + 2.0 * cosAB + 2.0 * distance * (sinA - sinB)) / 8.0;
        if (Math.abs(tmp) > 1.0) {
            return null;
        }
        double middle = mod2pi(TWO_PI - Math.acos(GeometryUtil.clamp(tmp, -1.0, 1.0)));
        double first = mod2pi(
                alpha - Math.atan2(cosA - cosB, distance - sinA + sinB) + middle / 2.0);
        double third = mod2pi(alpha - beta - first + middle);
        return new Path('R', first, 'L', middle, 'R', third);
    }

    private static Path lrl(double alpha, double beta, double distance) {
        double sinA = Math.sin(alpha);
        double sinB = Math.sin(beta);
        double cosA = Math.cos(alpha);
        double cosB = Math.cos(beta);
        double cosAB = Math.cos(alpha - beta);
        double tmp = (6.0 - distance * distance + 2.0 * cosAB + 2.0 * distance * (-sinA + sinB)) / 8.0;
        if (Math.abs(tmp) > 1.0) {
            return null;
        }
        double middle = mod2pi(TWO_PI - Math.acos(GeometryUtil.clamp(tmp, -1.0, 1.0)));
        double first = mod2pi(
                -alpha - Math.atan2(cosA - cosB, distance + sinA - sinB) + middle / 2.0);
        double third = mod2pi(beta - alpha - first + middle);
        return new Path('L', first, 'R', middle, 'L', third);
    }

    private static List<EastNorth> samplePath(
            EastNorth start,
            double startHeading,
            double radius,
            Path path,
            double intervalMeters) {
        List<EastNorth> points = new ArrayList<>();
        points.add(start);
        Pose pose = new Pose(start, startHeading);
        pose = appendArc(points, pose, path.firstTurn, path.firstArcRadians, radius, intervalMeters);
        if (path.hasThirdArc()) {
            pose = appendArc(points, pose, path.secondTurn, path.secondArcRadians, radius, intervalMeters);
            appendArc(points, pose, path.thirdTurn, path.thirdArcRadians, radius, intervalMeters);
        } else {
            pose = appendStraight(points, pose, path.straightLengthRadiusUnits * radius, intervalMeters);
            appendArc(points, pose, path.secondTurn, path.secondArcRadians, radius, intervalMeters);
        }
        return points;
    }

    private static Pose appendArc(
            List<EastNorth> points,
            Pose pose,
            char turn,
            double angleRadians,
            double radius,
            double intervalMeters) {
        if (angleRadians < EPS) {
            return pose;
        }

        double turnSign = turn == 'L' ? 1.0 : -1.0;
        Vector2D tangent = new Vector2D(Math.cos(pose.headingRadians), Math.sin(pose.headingRadians));
        EastNorth center = (turnSign > 0.0 ? tangent.leftNormal() : tangent.rightNormal())
                .pointFrom(pose.point, radius);
        Vector2D startRadius = Vector2D.between(center, pose.point);
        int segmentCount = Math.max(1, (int) Math.ceil(angleRadians * radius / Math.max(1.0, intervalMeters)));

        EastNorth point = pose.point;
        for (int i = 1; i <= segmentCount; i++) {
            double angle = turnSign * angleRadians * i / segmentCount;
            Vector2D rotated = rotate(startRadius, angle);
            point = new EastNorth(center.east() + rotated.x(), center.north() + rotated.y());
            appendWithoutDuplicate(points, point);
        }
        return new Pose(point, pose.headingRadians + turnSign * angleRadians);
    }

    private static Pose appendStraight(
            List<EastNorth> points,
            Pose pose,
            double lengthMeters,
            double intervalMeters) {
        if (lengthMeters < EPS) {
            return pose;
        }

        int segmentCount = Math.max(1, (int) Math.ceil(lengthMeters / Math.max(1.0, intervalMeters)));
        Vector2D tangent = new Vector2D(Math.cos(pose.headingRadians), Math.sin(pose.headingRadians));
        EastNorth point = pose.point;
        for (int i = 1; i <= segmentCount; i++) {
            point = tangent.pointFrom(pose.point, lengthMeters * i / segmentCount);
            appendWithoutDuplicate(points, point);
        }
        return new Pose(point, pose.headingRadians);
    }

    private static void appendWithoutDuplicate(List<EastNorth> points, EastNorth point) {
        if (points.isEmpty() || points.get(points.size() - 1).distance(point) > 1e-6) {
            points.add(point);
        }
    }

    private static Vector2D rotate(Vector2D vector, double angleRadians) {
        double cos = Math.cos(angleRadians);
        double sin = Math.sin(angleRadians);
        return new Vector2D(
                vector.x() * cos - vector.y() * sin,
                vector.x() * sin + vector.y() * cos);
    }

    private static double mod2pi(double angleRadians) {
        double result = angleRadians % TWO_PI;
        return result < 0.0 ? result + TWO_PI : result;
    }

    private static final class Pose {
        private final EastNorth point;
        private final double headingRadians;

        private Pose(EastNorth point, double headingRadians) {
            this.point = point;
            this.headingRadians = headingRadians;
        }
    }

    private static final class Path {
        private final char firstTurn;
        private final double firstArcRadians;
        private final double straightLengthRadiusUnits;
        private final char secondTurn;
        private final double secondArcRadians;
        private final char thirdTurn;
        private final double thirdArcRadians;

        private Path(
                char firstTurn,
                double firstArcRadians,
                double straightLengthRadiusUnits,
                char secondTurn,
                double secondArcRadians) {
            this(firstTurn, firstArcRadians, straightLengthRadiusUnits, secondTurn, secondArcRadians, '\0', 0.0);
        }

        private Path(
                char firstTurn,
                double firstArcRadians,
                char secondTurn,
                double secondArcRadians,
                char thirdTurn,
                double thirdArcRadians) {
            this(firstTurn, firstArcRadians, 0.0, secondTurn, secondArcRadians, thirdTurn, thirdArcRadians);
        }

        private Path(
                char firstTurn,
                double firstArcRadians,
                double straightLengthRadiusUnits,
                char secondTurn,
                double secondArcRadians,
                char thirdTurn,
                double thirdArcRadians) {
            this.firstTurn = firstTurn;
            this.firstArcRadians = firstArcRadians;
            this.straightLengthRadiusUnits = straightLengthRadiusUnits;
            this.secondTurn = secondTurn;
            this.secondArcRadians = secondArcRadians;
            this.thirdTurn = thirdTurn;
            this.thirdArcRadians = thirdArcRadians;
        }

        private boolean hasThirdArc() {
            return thirdTurn == 'L' || thirdTurn == 'R';
        }

        private Path withExtraLoops(int extraLoopTurns) {
            if (extraLoopTurns <= 0) {
                return this;
            }
            double extraRadians = TWO_PI * extraLoopTurns;
            if (hasThirdArc()) {
                return new Path(
                        firstTurn,
                        firstArcRadians,
                        straightLengthRadiusUnits,
                        secondTurn,
                        secondArcRadians + extraRadians,
                        thirdTurn,
                        thirdArcRadians);
            }
            return new Path(
                    firstTurn,
                    firstArcRadians + extraRadians,
                    straightLengthRadiusUnits,
                    secondTurn,
                    secondArcRadians);
        }

        private double length() {
            return firstArcRadians + straightLengthRadiusUnits + secondArcRadians + thirdArcRadians;
        }
    }
}
