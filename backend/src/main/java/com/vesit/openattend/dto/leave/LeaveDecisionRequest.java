package com.vesit.openattend.dto.leave;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The note a teacher attaches when approving or rejecting. Optional on an approval — the click
 * is the decision — but the reject path requires it, enforced in the service so the rule holds
 * for every caller.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveDecisionRequest {

    @Size(max = 2000, message = "decisionNote must be at most 2000 characters")
    private String decisionNote;
}
