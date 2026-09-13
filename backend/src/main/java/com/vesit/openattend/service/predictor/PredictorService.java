package com.vesit.openattend.service.predictor;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Safe-skip and recovery predictions (PRD Module 7).
 *
 * <p>Every decision is made in exact integer arithmetic on the threshold held as a fraction — never
 * on a rounded percentage or a floating-point product. At 75% the formulas reduce to the PRD's:
 * <ul>
 *   <li>Safe skips, when {@code 4A >= 3T}: {@code S = floor((4A - 3T) / 3)}, the most lectures that
 *       can be missed with {@code A / (T + S)} still at or above 75%.</li>
 *   <li>Must attend, when {@code 4A < 3T}: {@code R = 3T - 4A}, the fewest consecutive lectures that
 *       bring {@code (A + R) / (T + R)} back up to 75%.</li>
 * </ul>
 *
 * <p>The percentage is rounded to one decimal place for display only: 377 / 503 reads "75.0%" but
 * is 74.95%, so it is {@code RISK} with one lecture to recover.
 */
@Service
public class PredictorService {

    public static final double DEFAULT_THRESHOLD = 0.75; // 75%
    public static final double HONOR_THRESHOLD = 0.85;   // 85%

    private static final Threshold INSTITUTIONAL = Threshold.of(DEFAULT_THRESHOLD);
    private static final Threshold HONOR = Threshold.of(HONOR_THRESHOLD);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * @param present         lectures attended, A
     * @param total           lectures conducted, T
     * @param targetThreshold target as a fraction in (0, 1); anything else falls back to 75%
     * @param totalPlanned    lectures planned for the term, or null when unknown
     * @throws IllegalArgumentException when the counts are negative or {@code present > total}
     */
    public PredictorResult calculate(int present, int total, Double targetThreshold, Integer totalPlanned) {
        if (present < 0 || total < 0 || present > total) {
            throw new IllegalArgumentException(
                    "Attendance counts must satisfy 0 <= present <= total, got " + present + "/" + total);
        }

        Threshold threshold = (targetThreshold != null && targetThreshold > 0 && targetThreshold < 1.0)
                ? Threshold.of(targetThreshold)
                : INSTITUTIONAL;

        boolean safe = threshold.isMetBy(present, total);
        int safeSkips = safe ? threshold.safeSkips(present, total) : 0;
        int mustAttend = safe ? 0 : threshold.mustAttend(present, total);

        boolean honorSafe = HONOR.isMetBy(present, total);
        int honorSafeSkips = honorSafe ? HONOR.safeSkips(present, total) : 0;
        int honorMustAttend = honorSafe ? 0 : HONOR.mustAttend(present, total);

        // Defaulter status is the institutional 75% line, whatever target the student is exploring.
        boolean isDefaulter = !INSTITUTIONAL.isMetBy(present, total);

        boolean isRecoverable = true;
        Double maxPossiblePct = null;
        Integer remainingPlanned = null;

        if (totalPlanned != null && totalPlanned >= total) {
            remainingPlanned = totalPlanned - total;
            maxPossiblePct = percentage(present + remainingPlanned, totalPlanned);
            isRecoverable = mustAttend <= remainingPlanned;
        }

        String status = safe ? "SAFE" : "RISK";
        String target = threshold.label() + "%";
        String message;

        if (total == 0) {
            message = "No attendance records recorded yet.";
        } else if (!isRecoverable) {
            message = "Attendance cannot reach " + target + " threshold with remaining planned lectures (" + remainingPlanned + " left).";
        } else if (safe) {
            if (safeSkips == 0) {
                message = "You are right on the edge of the " + target + " threshold. Attend the next lecture to build a buffer.";
            } else {
                message = "Safe to skip " + safeSkips + " more lecture(s) while remaining at or above " + target + " threshold.";
            }
        } else {
            message = "Must attend " + mustAttend + " consecutive lecture(s) to reach " + target + " threshold.";
        }

        return PredictorResult.builder()
                .present(present)
                .total(total)
                .pct(percentage(present, total))
                .threshold(threshold.percent().doubleValue())
                .status(status)
                .safeSkips(safeSkips)
                .mustAttend(mustAttend)
                .isDefaulter(isDefaulter)
                .isRecoverable(isRecoverable)
                .message(message)
                .totalPlanned(totalPlanned)
                .remainingPlanned(remainingPlanned)
                .maxPossiblePct(maxPossiblePct)
                .honorSafeSkips(honorSafeSkips)
                .honorMustAttend(honorMustAttend)
                .build();
    }

    /** {@code attended / conducted} as a percentage at one decimal place; 0.0 when nothing was conducted. */
    private static double percentage(int attended, int conducted) {
        if (conducted == 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(attended)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(conducted), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * A threshold held as the exact fraction {@code numerator / denominator} — 0.75 as 75/100 — so
     * {@code A / T >= threshold} is decided as {@code denominator * A >= numerator * T}.
     */
    private record Threshold(BigInteger numerator, BigInteger denominator, BigDecimal percent) {

        static Threshold of(double fraction) {
            BigDecimal exact = BigDecimal.valueOf(fraction);
            return new Threshold(exact.unscaledValue(), BigInteger.TEN.pow(exact.scale()), exact.movePointRight(2));
        }

        boolean isMetBy(int attended, int conducted) {
            return denominator.multiply(BigInteger.valueOf(attended))
                    .compareTo(numerator.multiply(BigInteger.valueOf(conducted))) >= 0;
        }

        /** The largest S with {@code A / (T + S) >= threshold}; only defined while the threshold is met. */
        int safeSkips(int attended, int conducted) {
            BigInteger surplus = denominator.multiply(BigInteger.valueOf(attended))
                    .subtract(numerator.multiply(BigInteger.valueOf(conducted)));
            // surplus >= 0 here, so truncating division is the floor
            return saturate(surplus.divide(numerator));
        }

        /** The smallest R with {@code (A + R) / (T + R) >= threshold}; only defined while the threshold is missed. */
        int mustAttend(int attended, int conducted) {
            BigInteger deficit = numerator.multiply(BigInteger.valueOf(conducted))
                    .subtract(denominator.multiply(BigInteger.valueOf(attended)));
            BigInteger[] quotientAndRemainder = deficit.divideAndRemainder(denominator.subtract(numerator));
            // deficit > 0 here, so a non-zero remainder rounds the quotient up to the ceiling
            BigInteger lectures = quotientAndRemainder[1].signum() == 0
                    ? quotientAndRemainder[0]
                    : quotientAndRemainder[0].add(BigInteger.ONE);
            return saturate(lectures);
        }

        /** "75", "72.5" — the percentage as a student reads it. */
        String label() {
            return percent.stripTrailingZeros().toPlainString();
        }

        private static int saturate(BigInteger value) {
            return value.bitLength() < Integer.SIZE ? value.intValue() : Integer.MAX_VALUE;
        }
    }
}
