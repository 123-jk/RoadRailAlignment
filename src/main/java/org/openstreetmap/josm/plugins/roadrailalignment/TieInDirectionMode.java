package org.openstreetmap.josm.plugins.roadrailalignment;

import static org.openstreetmap.josm.tools.I18n.tr;

public enum TieInDirectionMode {
    AUTO_CHORD("Automatic toward target"),
    SOURCE_WAY("Along source way direction"),
    REVERSE_SOURCE_WAY("Reverse source way direction");

    private final String displayName;

    TieInDirectionMode(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return tr(displayName);
    }
}
