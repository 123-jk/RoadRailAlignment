package org.openstreetmap.josm.plugins.roadrailalignment;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.Arrays;

public enum AlignmentMode {
    STRAIGHT_LINE("两点直线", 2, true),
    PI_CIRCULAR_ARC("三点圆曲线（可带缓和）", 3, true),
    LARGE_SWEEP_ARC("大角度圆曲线/回环", 3, true),
    TRANSITION_SPIRAL("缓和曲线近似", 3, false),
    RAMP_FROM_SELECTED_WAY("从既有线接出匝道", 2, true),
    RAMP_BETWEEN_SELECTED_WAYS("连接两条既有线", 2, true);

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
