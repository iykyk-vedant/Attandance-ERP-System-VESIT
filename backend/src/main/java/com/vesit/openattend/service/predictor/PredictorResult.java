package com.vesit.openattend.service.predictor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictorResult {
    private int present;
    private int total;
    private double pct;
    private double threshold;
    private String status; // "SAFE" or "RISK"
    private int safeSkips;
    private int mustAttend;
    private boolean isDefaulter;
    private boolean isRecoverable;
    private String message;
    private Integer totalPlanned;
    private Integer remainingPlanned;
    private Double maxPossiblePct;
    private Integer honorSafeSkips;
    private Integer honorMustAttend;
}
