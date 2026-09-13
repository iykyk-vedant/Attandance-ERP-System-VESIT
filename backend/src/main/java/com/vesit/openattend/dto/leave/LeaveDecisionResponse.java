package com.vesit.openattend.dto.leave;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The outcome of a 1-click approve / reject, with the attendance recalculation it triggered. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveDecisionResponse {

    @Builder.Default
    private boolean success = true;

    private String message;

    private LeaveRequestDto request;

    /** Null on a rejection — nothing is adjusted when a request is turned down. */
    private AttendanceImpactDto attendanceImpact;
}
