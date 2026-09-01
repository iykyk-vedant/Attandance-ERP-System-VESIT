package com.vesit.openattend.dto.attendance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceAnalyticsResponse {
    private boolean success;
    private List<WeeklyTrendDto> weeklyTrends;
    private List<SubjectDeltaDto> subjectComparison;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WeeklyTrendDto {
        private String period;
        private double pct;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SubjectDeltaDto {
        private String code;
        private String name;
        private double pct;
        private double delta;
        private String status; // "SAFE" or "RISK"
    }
}
