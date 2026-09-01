package com.vesit.openattend.dto.attendance;

import com.vesit.openattend.service.predictor.PredictorResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OverallAttendanceResponse {
    private boolean success;
    private String rollNo;
    private double overallPct;
    private int present;
    private int total;
    private int absent;
    private boolean isDefaulter;
    private PredictorResult predictor;
}
