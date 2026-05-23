package org.openstreetmap.josm.plugins.roadrailalignment.geometry;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.coor.EastNorth;

public final class PiArcSampler {
    private static final double EPS = 1e-9;

    private PiArcSampler() {
    }

    public static List<EastNorth> sample(
            EastNorth start,
            EastNorth pi,
            EastNorth end,
            double radiusMeters,
            double intervalMeters) {
        return sample(start, pi, end, radiusMeters, intervalMeters, true);
    }

    public static List<EastNorth> sample(
            EastNorth start,
            EastNorth pi,
            EastNorth end,
            double radiusMeters,
            double intervalMeters,
            boolean includeExitTangent) {
        validate(start, pi, end, radiusMeters);

        Vector fromPiToStart = Vector.between(pi, start).normalize();
        Vector fromPiToEnd = Vector.between(pi, end).normalize();
        Vector incomingTangent = fromPiToStart.scale(-1.0);
        Vector outgoingTangent = fromPiToEnd;

        double turnAngle = angleBetween(incomingTangent, outgoingTangent);
        if (turnAngle < Math.toRadians(1.0) || Math.PI - turnAngle < Math.toRadians(1.0)) {
            throw new IllegalArgumentException(tr("PI 转角过小或接近 180 度，无法生成圆曲线。"));
        }

        double radius = Math.max(1.0, radiusMeters);
        double tangentLength = radius * Math.tan(turnAngle / 2.0);
        double startLegLength = start.distance(pi);
        double endLegLength = pi.distance(end);
        if (tangentLength >= startLegLength || tangentLength >= endLegLength) {
            throw new IllegalArgumentException(tr(
                    "圆曲线半径过大，切线长超过 PI 两侧线段长度。请减小半径或拉远控制点。"));
        }

        EastNorth tangentStart = fromPiToStart.pointFrom(pi, tangentLength);
        EastNorth tangentEnd = fromPiToEnd.pointFrom(pi, tangentLength);
        double turnSign = Math.signum(incomingTangent.cross(outgoingTangent));
        if (Math.abs(turnSign) < EPS) {
            throw new IllegalArgumentException(tr("所选 PI 点无法形成有效转向。"));
        }

        Vector centerOffsetAtStart = turnSign > 0
                ? incomingTangent.leftNormal().scale(radius)
                : incomingTangent.rightNormal().scale(radius);
        Vector centerOffsetAtEnd = turnSign > 0
                ? outgoingTangent.leftNormal().scale(radius)
                : outgoingTangent.rightNormal().scale(radius);

        EastNorth centerFromStart = centerOffsetAtStart.pointFrom(tangentStart, 1.0);
        EastNorth centerFromEnd = centerOffsetAtEnd.pointFrom(tangentEnd, 1.0);
        EastNorth center = midpoint(centerFromStart, centerFromEnd);

        List<EastNorth> result = new ArrayList<>();
        appendWithoutDuplicate(result, LineSampler.sample(start, tangentStart, intervalMeters));
        appendWithoutDuplicate(result, sampleArc(center, tangentStart, tangentEnd, radius, turnSign, intervalMeters));
        if (includeExitTangent) {
            appendWithoutDuplicate(result, LineSampler.sample(tangentEnd, end, intervalMeters));
        }
        return result;
    }

    private static void validate(EastNorth start, EastNorth pi, EastNorth end, double radiusMeters) {
        if (start == null || pi == null || end == null || !start.isValid() || !pi.isValid() || !end.isValid()) {
            throw new IllegalArgumentException(tr("PI 控制点无效。"));
        }
        if (start.distance(pi) < 0.01 || pi.distance(end) < 0.01) {
            throw new IllegalArgumentException(tr("PI 两侧线段过短。"));
        }
        if (radiusMeters <= 0.0) {
            throw new IllegalArgumentException(tr("圆曲线半径必须大于 0。"));
        }
    }

    private static List<EastNorth> sampleArc(
            EastNorth center,
            EastNorth start,
            EastNorth end,
            double radius,
            double turnSign,
            double intervalMeters) {
        double startAngle = Math.atan2(start.north() - center.north(), start.east() - center.east());
        double endAngle = Math.atan2(end.north() - center.north(), end.east() - center.east());
        double sweep = endAngle - startAngle;
        if (turnSign > 0 && sweep < 0) {
            sweep += Math.PI * 2.0;
        } else if (turnSign < 0 && sweep > 0) {
            sweep -= Math.PI * 2.0;
        }

        double arcLength = Math.abs(sweep) * radius;
        int segmentCount = Math.max(1, (int) Math.ceil(arcLength / Math.max(1.0, intervalMeters)));
        List<EastNorth> points = new ArrayList<>(segmentCount + 1);
        for (int i = 0; i <= segmentCount; i++) {
            double t = (double) i / segmentCount;
            double angle = startAngle + sweep * t;
            points.add(new EastNorth(
                    center.east() + Math.cos(angle) * radius,
                    center.north() + Math.sin(angle) * radius));
        }
        return points;
    }

    private static void appendWithoutDuplicate(List<EastNorth> target, List<EastNorth> source) {
        for (EastNorth point : source) {
            if (target.isEmpty() || target.get(target.size() - 1).distance(point) > 1e-6) {
                target.add(point);
            }
        }
    }

    private static EastNorth midpoint(EastNorth first, EastNorth second) {
        return new EastNorth(
                (first.east() + second.east()) / 2.0,
                (first.north() + second.north()) / 2.0);
    }

    private static double angleBetween(Vector first, Vector second) {
        double dot = Math.max(-1.0, Math.min(1.0, first.dot(second)));
        return Math.acos(dot);
    }

    private static final class Vector {
        private final double x;
        private final double y;

        private Vector(double x, double y) {
            this.x = x;
            this.y = y;
        }

        private static Vector between(EastNorth from, EastNorth to) {
            return new Vector(to.east() - from.east(), to.north() - from.north());
        }

        private Vector normalize() {
            double length = Math.hypot(x, y);
            if (length < EPS) {
                throw new IllegalArgumentException(tr("PI 线段长度为 0。"));
            }
            return new Vector(x / length, y / length);
        }

        private Vector scale(double factor) {
            return new Vector(x * factor, y * factor);
        }

        private Vector leftNormal() {
            return new Vector(-y, x);
        }

        private Vector rightNormal() {
            return new Vector(y, -x);
        }

        private double dot(Vector other) {
            return x * other.x + y * other.y;
        }

        private double cross(Vector other) {
            return x * other.y - y * other.x;
        }

        private EastNorth pointFrom(EastNorth origin, double distance) {
            return new EastNorth(origin.east() + x * distance, origin.north() + y * distance);
        }
    }
}
