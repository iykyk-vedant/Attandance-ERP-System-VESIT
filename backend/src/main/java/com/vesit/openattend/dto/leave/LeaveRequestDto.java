package com.vesit.openattend.dto.leave;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One leave application as the student portal and the approval dashboard render it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveRequestDto {

    private String id;

    private String studentId;
    private String rollNo;
    private String studentName;
    private String division;
    private String batch;

    private String leaveType;
    private String eventName;
    private String reason;

    private String fromDate;
    private String toDate;

    /** Inclusive span in days. */
    private int days;

    private String status;

    private String submittedBy;
    private String submittedAt;

    private String decidedBy;
    private String decidedAt;
    private String decisionNote;

    /** Records this approval flipped to DUTY_PRESENT; 0 until the request is approved. */
    private int sessionsAdjusted;

    private LeaveProofDto proof;
}
