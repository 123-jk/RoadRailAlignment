package org.openstreetmap.josm.plugins.roadrailalignment;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.Arrays;

public enum AlignmentMode {
    BASIC_ALIGNMENT("Basic smart alignment", 2, true),
    STRAIGHT_LINE("Two-point straight line", 2, true),
    PI_CIRCULAR_ARC("Three-point circular curve (with transitions)", 3, true),
    LARGE_SWEEP_ARC("Large-sweep circular curve/loop", 3, true),
    TRANSITION_SPIRAL("Transition spiral approximation", 3, false),
    RAMP_FROM_SELECTED_WAY("Ramp from existing way", 2, true),
    RAMP_BETWEEN_SELECTED_WAYS("Connect two existing ways", 2, true);

    private final String displayName;
    private final int requiredPointCount;
    private final boolean userVisible;

    AlignmentMode(String displayName, int requiredPointCount, boolean userVisible) {
        this.displayName = displayName;
        this.requiredPointCount = requiredPointCount;
        this.userVisible = userVisible;
    }

    public int getRequiredPointCount() {
        return requiredPointCount;
    }

    public boolean isUserVisible() {
        return userVisible;
    }

    public static AlignmentMode[] userVisibleValues() {
        return Arrays.stream(values())
                .filter(AlignmentMode::isUserVisible)
                .toArray(AlignmentMode[]::new);
    }

    @Override
    public String toString() {
        return tr(displayName);
    }
}
