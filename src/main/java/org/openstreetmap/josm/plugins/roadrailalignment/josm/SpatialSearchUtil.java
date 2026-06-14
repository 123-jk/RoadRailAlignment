package org.openstreetmap.josm.plugins.roadrailalignment.josm;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.projection.Projection;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;

final class SpatialSearchUtil {
    private SpatialSearchUtil() {
    }

    static BBox bboxAround(EastNorth center, double radiusMeters) {
        if (center == null || !center.isValid() || !Double.isFinite(radiusMeters) || radiusMeters < 0.0) {
            return null;
        }

        Projection projection = ProjectionRegistry.getProjection();
        double radius = Math.max(0.001, radiusMeters);
        BBox bbox = null;
        for (EastNorth corner : new EastNorth[] {
                center.add(-radius, -radius),
                center.add(-radius, radius),
                center.add(radius, -radius),
                center.add(radius, radius) }) {
            LatLon latLon = projection.eastNorth2latlon(corner);
            if (latLon == null || !latLon.isValid()) {
                return null;
            }
            if (bbox == null) {
                bbox = new BBox(latLon);
            } else {
                bbox.add(latLon);
            }
        }
        return bbox != null && bbox.isValid() ? bbox : null;
    }
}
