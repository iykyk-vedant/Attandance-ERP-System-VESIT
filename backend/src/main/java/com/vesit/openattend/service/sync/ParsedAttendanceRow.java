package com.vesit.openattend.service.sync;

import com.vesit.openattend.entity.enums.AttendanceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParsedAttendanceRow {
    private String studentRollNo;
    private LocalDate lectureDate;
    private Integer sessionIndex;
    private AttendanceStatus status;
    private String faculty;
    private String remarks;
    private String sourceRowHash;
}
