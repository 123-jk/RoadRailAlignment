package org.openstreetmap.josm.plugins.roadrailalignment.geometry;

import static org.openstreetmap.josm.tools.I18n.tr;

import org.openstreetmap.josm.data.coor.EastNorth;

public final class Vector2D {
    private static final double EPS = 1e-12;

    private final double x;
    private final double y;

    public Vector2D(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public static Vector2D between(EastNorth from, EastNorth to) {
        return new Vector2D(to.east() - from.east(), to.north() - from.north());
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double length() {
        return Math.hypot(x, y);
    }

    public double lengthSquared() {
        return x * x + y * y;
    }

    public Vector2D normalize() {
        double length = length();
        if (length < EPS) {
            throw new IllegalArgumentException(tr("零长度方向无法计算。"));
        }
        return new Vector2D(x / length, y / length);
    }

    public Vector2D scale(double factor) {
        return new Vector2D(x * factor, y * factor);
    }

    public Vector2D reverse() {
        return new Vector2D(-x, -y);
    }

    public Vector2D leftNormal() {
        return new Vector2D(-y, x);
    }

    public Vector2D rightNormal() {
        return new Vector2D(y, -x);
    }

    public double dot(Vector2D other) {
        return x * other.x + y * other.y;
    }

    public double cross(Vector2D other) {
        return x * other.y - y * other.x;
    }

    public EastNorth pointFrom(EastNorth origin, double distance) {
        return new EastNorth(origin.east() + x * distance, origin.north() + y * distance);
    }
}
