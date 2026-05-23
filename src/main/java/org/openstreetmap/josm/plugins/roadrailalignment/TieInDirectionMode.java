package org.openstreetmap.josm.plugins.roadrailalignment;

import static org.openstreetmap.josm.tools.I18n.tr;

public enum TieInDirectionMode {
    AUTO_CHORD("自动朝目标"),
    SOURCE_WAY("按所在线方向"),
    REVERSE_SOURCE_WAY("反向所在线");

    private final String displayName;

    TieInDirectionMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return tr(displayName);
    }
}
