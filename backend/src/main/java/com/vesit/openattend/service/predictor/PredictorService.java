package com.vesit.openattend.service.predictor;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PredictorService {

    public static final double DEFAULT_THRESHOLD = 0.75; // 75%
    public static final double HONOR_THRESHOLD = 0.85;   // 85%

    public PredictorResult calculate(int present, int total, Double targetThreshold, Integer totalPlanned) {
        double threshold = (targetThreshold != null && targetThreshold > 0 && targetThreshold <= 1.0)
                ? targetThreshold
                : DEFAULT_THRESHOLD;

        double currentPct = 0.0;
        if (total > 0) {
            currentPct = ((double) present / total) * 100.0;
            currentPct = BigDecimal.valueOf(currentPct).setScale(1, RoundingMode.HALF_UP).doubleValue();
        }

        int safeSkips = 0;
        int mustAttend = 0;

        if (total > 0) {
            if (currentPct >= (threshold * 100.0)) {
                // Safe skips formula: floor((P - (T * threshold)) / threshold)
                safeSkips = (int) Math.floor((present - (total * threshold)) / threshold);
                safeSkips = Math.max(0, safeSkips);
            } else {
                // Must attend formula: ceil((threshold * T - P) / (1 - threshold))
                mustAttend = (int) Math.ceil(((threshold * total) - present) / (1.0 - threshold));
                mustAttend = Math.max(0, mustAttend);
            }
        }

        // Honor Threshold (85%) calculations
        int honorSafeSkips = 0;
        int honorMustAttend = 0;
        if (total > 0) {
            if (currentPct >= (HONOR_THRESHOLD * 100.0)) {
                honorSafeSkips = Math.max(0, (int) Math.floor((present - (total * HONOR_THRESHOLD)) / HONOR_THRESHOLD));
            } else {
                honorMustAttend = Math.max(0, (int) Math.ceil(((HONOR_THRESHOLD * total) - present) / (1.0 - HONOR_THRESHOLD)));
            }
        }

        boolean isDefaulter = currentPct < 75.0 && total > 0;
        boolean isRecoverable = true;
        Double maxPossiblePct = null;
        Integer remainingPlanned = null;

        if (totalPlanned != null && totalPlanned > total) {
            remainingPlanned = totalPlanned - total;
            double maxPct = ((double) (present + remainingPlanned) / totalPlanned) * 100.0;
            maxPossiblePct = BigDecimal.valueOf(maxPct).setScale(1, RoundingMode.HALF_UP).doubleValue();
            if (maxPossiblePct < (threshold * 100.0)) {
                isRecoverable = false;
            }
        }

        String status = (total == 0 || currentPct >= (threshold * 100.0)) ? "SAFE" : "RISK";
        String message;

        if (total == 0) {
            message = "No attendance records recorded yet.";
        } else if (!isRecoverable) {
            message = "Attendance cannot reach " + (int) (threshold * 100) + "% threshold with remaining planned lectures (" + remainingPlanned + " left).";
        } else if ("SAFE".equals(status)) {
            if (safeSkips == 0) {
                message = "You are right on the edge of the " + (int) (threshold * 100) + "% threshold. Attend the next lecture to build a buffer.";
            } else {
                message = "Safe to skip " + safeSkips + " more lecture(s) while remaining above " + (int) (threshold * 100) + "% threshold.";
            }
        } else {
            message = "Must attend " + mustAttend + " consecutive lecture(s) to reach " + (int) (threshold * 100) + "% threshold.";
        }

        return PredictorResult.builder()
                .present(present)
                .total(total)
                .pct(currentPct)
                .threshold(threshold * 100.0)
                .status(status)
                .safeSkips(safeSkips)
                .mustAttend(mustAttend)
                .isDefaulter(isDefaulter)
                .isRecoverable(isRecoverable)
                .message(message)
                .totalPlanned(totalPlanned)
                .remainingPlanned(remainingPlanned)
                .maxPossiblePct(maxPossiblePct)
                .honorSafeSkips(honorSafeSkips)
                .honorMustAttend(honorMustAttend)
                .build();
    }
}
