package org.openstreetmap.josm.plugins.roadrailalignment.geometry;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class RadiusFormatter {
    private static final int MAX_COMPARISON_DECIMALS = 3;

    private RadiusFormatter() {
    }

    static String formatMeters(double radiusMeters) {
        if (!Double.isFinite(radiusMeters)) {
            return "∞";
        }
        return format(radiusMeters, 0);
    }

    static String formatMetersBelowThreshold(double radiusMeters, double thresholdMeters) {
        String radiusText = formatMeters(radiusMeters);
        if (!Double.isFinite(radiusMeters) || !Double.isFinite(thresholdMeters) || radiusMeters >= thresholdMeters) {
            return radiusText;
        }

        String thresholdText = formatMeters(thresholdMeters);
        if (!radiusText.equals(thresholdText)) {
            return radiusText;
        }

        for (int decimals = 1; decimals <= MAX_COMPARISON_DECIMALS; decimals++) {
            radiusText = format(radiusMeters, decimals);
            thresholdText = format(thresholdMeters, decimals);
            if (!radiusText.equals(thresholdText)) {
                return radiusText;
            }
        }
        return "< " + thresholdText;
    }

    static String formatThresholdMeters(double thresholdMeters, double comparedRadiusMeters) {
        String thresholdText = formatMeters(thresholdMeters);
        if (!Double.isFinite(thresholdMeters)
                || !Double.isFinite(comparedRadiusMeters)
                || comparedRadiusMeters >= thresholdMeters) {
            return thresholdText;
        }

        String radiusText = formatMeters(comparedRadiusMeters);
        if (!radiusText.equals(thresholdText)) {
            return thresholdText;
        }

        for (int decimals = 1; decimals <= MAX_COMPARISON_DECIMALS; decimals++) {
            radiusText = format(comparedRadiusMeters, decimals);
            thresholdText = format(thresholdMeters, decimals);
            if (!radiusText.equals(thresholdText)) {
                return thresholdText;
            }
        }
        return thresholdText;
    }

    private static String format(double value, int decimals) {
        return BigDecimal.valueOf(value)
                .setScale(decimals, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }
}
