package org.openstreetmap.josm.plugins.roadrailalignment.geometry;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.coor.EastNorth;

public final class PiArcSampler {
    private static final double EPS = 1e-9;
    private static final int MIN_ARC_SEGMENTS = 6;

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
            throw new IllegalArgumentException(tr("The PI deflection angle is too small or close to 180 degrees; a circular curve cannot be generated."));
        }

        double radius = Math.max(1.0, radiusMeters);
        double tangentLength = radius * Math.tan(turnAngle / 2.0);
        double startLegLength = start.distance(pi);
        double endLegLength = pi.distance(end);
        if (tangentLength >= startLegLength || tangentLength >= endLegLength) {
            throw new IllegalArgumentException(tr(
                    "The circular curve radius is too large; tangent length exceeds the segments on both sides of the PI. Reduce the radius or move the control points farther apart."));
        }

        EastNorth tangentStart = fromPiToStart.pointFrom(pi, tangentLength);
        EastNorth tangentEnd = fromPiToEnd.pointFrom(pi, tangentLength);
        double turnSign = Math.signum(incomingTangent.cross(outgoingTangent));
        if (Math.abs(turnSign) < EPS) {
            throw new IllegalArgumentException(tr("The selected PI point cannot form a valid turn."));
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

    public static Vector2D endTangent(EastNorth start, EastNorth pi, EastNorth end, double radiusMeters) {
        validate(start, pi, end, radiusMeters);

        Vector incomingTangent = Vector.between(pi, start).normalize().scale(-1.0);
        Vector outgoingTangent = Vector.between(pi, end).normalize();
        double turnAngle = angleBetween(incomingTangent, outgoingTangent);
        if (turnAngle < Math.toRadians(1.0) || Math.PI - turnAngle < Math.toRadians(1.0)) {
            throw new IllegalArgumentException(tr("The PI deflection angle is too small or close to 180 degrees; a circular curve cannot be generated."));
        }

        double radius = Math.max(1.0, radiusMeters);
        double tangentLength = radius * Math.tan(turnAngle / 2.0);
        if (tangentLength >= start.distance(pi) || tangentLength >= pi.distance(end)) {
            throw new IllegalArgumentException(tr(
                    "The circular curve radius is too large; tangent length exceeds the segments on both sides of the PI. Reduce the radius or move the control points farther apart."));
        }

        return new Vector2D(outgoingTangent.x, outgoingTangent.y);
    }

    private static void validate(EastNorth start, EastNorth pi, EastNorth end, double radiusMeters) {
        if (start == null || pi == null || end == null || !start.isValid() || !pi.isValid() || !end.isValid()) {
            throw new IllegalArgumentException(tr("Invalid PI control points."));
        }
        if (start.distance(pi) < 0.01 || pi.distance(end) < 0.01) {
            throw new IllegalArgumentException(tr("The segments on both sides of the PI are too short."));
        }
        if (radiusMeters <= 0.0) {
            throw new IllegalArgumentException(tr("The circular curve radius must be greater than 0."));
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
        int segmentCount = GeometryUtil.segmentCountForLength(
                arcLength,
                intervalMeters,
                MIN_ARC_SEGMENTS,
                tr("The PI circular arc"));
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
                throw new IllegalArgumentException(tr("The PI segment length is 0."));
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
