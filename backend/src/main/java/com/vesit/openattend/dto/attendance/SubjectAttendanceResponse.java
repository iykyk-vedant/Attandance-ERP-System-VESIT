package com.vesit.openattend.dto.attendance;

import com.vesit.openattend.service.predictor.PredictorResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubjectAttendanceResponse {
    private boolean success;
    private List<SubjectCardDto> subjects;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SubjectCardDto {
        private String code;
        private String name;
        private int present;
        private int total;
        private double pct;
        private PredictorResult predictor;
    }
}
