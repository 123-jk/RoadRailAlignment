package org.openstreetmap.josm.plugins.roadrailalignment;

import static org.openstreetmap.josm.tools.I18n.tr;

/**
 * Output object type and the default tag written to the generated Way.
 */
public enum FeatureType {
    ROAD("道路", "highway", "road"),
    RAIL("铁路", "railway", "rail");

    private final String displayName;
    private final String tagKey;
    private final String tagValue;

    FeatureType(String displayName, String tagKey, String tagValue) {
        this.displayName = displayName;
        this.tagKey = tagKey;
        this.tagValue = tagValue;
    }

    public String getTagKey() {
        return tagKey;
    }

    public String getTagValue() {
        return tagValue;
    }

    @Override
    public String toString() {
        return tr(displayName);
    }
}
