package com.vesit.openattend.service.attendance;

import com.vesit.openattend.entity.AttendanceRecord;
import com.vesit.openattend.entity.enums.AttendanceStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * The one place attendance percentages are turned into a number.
 *
 * <p>Percentages are rounded — and compared against the threshold — at {@link #SCALE} decimal
 * places, so the figure a class teacher reads is exactly the figure that decided defaulter
 * status: 74.99% is flagged, 75.00% is not.
 */
public final class AttendancePercentage {

    /** Percentages are rounded, and compared, at two decimal places. */
    public static final int SCALE = 2;

    /** The institutional pass mark. */
    public static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("75.00");

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private AttendancePercentage() {
    }

    /**
     * {@code attended / conducted} as a percentage. A student with no accountable lectures scores
     * 0% on an empty denominator; callers filter those out rather than reporting them as
     * defaulters.
     */
    public static BigDecimal of(int attended, int conducted) {
        if (conducted <= 0) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(attended)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(conducted), SCALE, RoundingMode.HALF_UP);
    }

    /** True when {@code percentage} falls below {@code threshold} at the comparison scale. */
    public static boolean isDefaulter(BigDecimal percentage, BigDecimal threshold) {
        BigDecimal limit = threshold != null ? threshold : DEFAULT_THRESHOLD;
        return percentage.compareTo(limit.setScale(SCALE, RoundingMode.HALF_UP)) < 0;
    }

    /**
     * Counts a set of records by the rule on {@link AttendanceStatus} — {@code DUTY_PRESENT}
     * counts as attended, {@code NA} counts as nothing at all.
     */
    public static Tally tally(Collection<AttendanceRecord> records) {
        int attended = 0;
        int conducted = 0;
        int duty = 0;

        for (AttendanceRecord record : records) {
            AttendanceStatus status = record.getStatus();
            if (status == null) {
                continue;
            }
            if (status.isConducted()) {
                conducted++;
            }
            if (status.isAttended()) {
                attended++;
            }
            if (status == AttendanceStatus.DUTY_PRESENT) {
                duty++;
            }
        }
        return new Tally(attended, conducted, duty);
    }

    /**
     * @param attended  numerator: physically present plus approved duty
     * @param conducted denominator: every lecture the student was accountable for
     * @param duty      the {@code DUTY_PRESENT} share of {@code attended}, reported so the
     *                  contribution of approved leave is visible rather than silent
     */
    public record Tally(int attended, int conducted, int duty) {

        public BigDecimal percentage() {
            return of(attended, conducted);
        }

        public int absent() {
            return conducted - attended;
        }
    }
}
