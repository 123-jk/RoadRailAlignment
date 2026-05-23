package org.openstreetmap.josm.plugins.roadrailalignment;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Output object type and the default tags written to the generated Way.
 */
public enum FeatureType {
    ROAD("Road", tags("highway", "road")),
    MOTORWAY("Motorway", tags("highway", "motorway")),
    MOTORWAY_LINK("Motorway link", tags("highway", "motorway_link")),
    TRUNK_ROAD("Trunk road", tags("highway", "trunk")),
    PRIMARY_ROAD("Primary road", tags("highway", "primary")),
    RAIL("Rail", tags("railway", "rail")),
    HIGH_SPEED_RAIL("High-speed rail", tags("railway", "rail", "highspeed", "yes"));

    private final String displayName;
    private final Map<String, String> tags;

    FeatureType(String displayName, Map<String, String> tags) {
        this.displayName = displayName;
        this.tags = tags;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public String getTagKey() {
        return tags.keySet().iterator().next();
    }

    public String getTagValue() {
        return tags.values().iterator().next();
    }

    @Override
    public String toString() {
        return tr(displayName);
    }

    private static Map<String, String> tags(String... keysAndValues) {
        if (keysAndValues.length == 0 || keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("Tags must be key/value pairs.");
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            result.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return Collections.unmodifiableMap(result);
    }
}
