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
public class AttendanceHistoryResponse {
    private boolean success;
    private List<HistoryItemDto> history;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HistoryItemDto {
        private String date;
        private String subject;
        private String subjectName;
        private String status;
        private String faculty;
        private String remarks;
    }
}
