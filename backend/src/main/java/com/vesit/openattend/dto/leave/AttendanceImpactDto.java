package com.vesit.openattend.dto.leave;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * What an approval did to the student's numbers, measured either side of the adjustment.
 *
 * <p>Returned with the decision so the approving teacher sees the consequence of the click
 * immediately, rather than having to re-run the defaulter report to find out.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceImpactDto {

    /** Records flipped from ABSENT to DUTY_PRESENT inside the leave window. */
    private int sessionsAdjusted;

    /** Denominator — unchanged by an approval; OD credits a lecture, it does not remove it. */
    private int totalConducted;

    private int attendedBefore;
    private int attendedAfter;

    private BigDecimal percentageBefore;
    private BigDecimal percentageAfter;

    /** Threshold the defaulter flags below were evaluated at. */
    private BigDecimal threshold;

    private boolean defaulterBefore;
    private boolean defaulterAfter;

    /** True when this approval is what lifted the student off the defaulter list. */
    private boolean clearedFromDefaulterList;
}
