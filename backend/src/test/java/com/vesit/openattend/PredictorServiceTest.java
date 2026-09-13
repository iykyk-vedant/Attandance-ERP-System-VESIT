package com.vesit.openattend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vesit.openattend.service.predictor.PredictorResult;
import com.vesit.openattend.service.predictor.PredictorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class PredictorServiceTest {

    private PredictorService predictorService;

    @BeforeEach
    void setUp() {
        predictorService = new PredictorService();
    }

    @ParameterizedTest(name = "A={0}, T={1} -> {2}% {3}, S={4}, R={5}")
    @CsvSource({
            // T = 0: nothing conducted yet
            "0,   0,   0.0,   SAFE, 0,  0",
            // A = 0
            "0,   1,   0.0,   RISK, 0,  3",
            "0,   4,   0.0,   RISK, 0,  12",
            "0,   40,  0.0,   RISK, 0,  120",
            // A = T
            "1,   1,   100.0, SAFE, 0,  0",
            "3,   3,   100.0, SAFE, 1,  0",
            "4,   4,   100.0, SAFE, 1,  0",
            "40,  40,  100.0, SAFE, 13, 0",
            // exactly 75.0%: safe, with not one lecture to spare
            "3,   4,   75.0,  SAFE, 0,  0",
            "30,  40,  75.0,  SAFE, 0,  0",
            "75,  100, 75.0,  SAFE, 0,  0",
            // one lecture either side of the line
            "31,  40,  77.5,  SAFE, 1,  0",
            "2,   3,   66.7,  RISK, 0,  1",
            "29,  40,  72.5,  RISK, 0,  4",
            // 74.95% displays as 75.0% but is below the line
            "377, 503, 75.0,  RISK, 0,  1",
            // reference vectors from the student dashboard
            "36,  42,  85.7,  SAFE, 6,  0",
            "36,  44,  81.8,  SAFE, 4,  0",
            "101, 126, 80.2,  SAFE, 8,  0",
            "33,  40,  82.5,  SAFE, 4,  0"
    })
    void predictsAtTheInstitutionalThreshold(int attended, int conducted, double pct, String status,
                                             int safeSkips, int mustAttend) {
        PredictorResult result = predictorService.calculate(attended, conducted, 0.75, null);

        assertEquals(pct, result.getCurrentPercentage());
        assertEquals(status, result.getStatus());
        assertEquals(safeSkips, result.getSafeSkips());
        assertEquals(mustAttend, result.getMustAttend());
        assertEquals("RISK".equals(status), result.isDefaulter());
        assertEquals(75.0, result.getThreshold());
    }

    @Test
    void matchesThePrdFormulasForEveryCountUpToOneThousandLectures() {
        for (int t = 0; t <= 1000; t++) {
            for (int a = 0; a <= t; a++) {
                PredictorResult result = predictorService.calculate(a, t, 0.75, null);

                boolean safe = 4 * a >= 3 * t;
                String expectedStatus = safe ? "SAFE" : "RISK";
                int expectedSkips = safe ? Math.floorDiv(4 * a - 3 * t, 3) : 0;
                int expectedMustAttend = safe ? 0 : 3 * t - 4 * a;

                if (!expectedStatus.equals(result.getStatus())
                        || expectedSkips != result.getSafeSkips()
                        || expectedMustAttend != result.getMustAttend()
                        || safe == result.isDefaulter()) {
                    fail("A=" + a + ", T=" + t + ": expected " + expectedStatus + " S=" + expectedSkips
                            + " R=" + expectedMustAttend + ", got " + result);
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.6, 0.65, 0.7, 0.725, 0.75, 0.8, 0.85, 0.9, 0.95})
    void safeSkipsAndMustAttendAreTheExactExtremesAtAnyThreshold(double threshold) {
        BigDecimal target = BigDecimal.valueOf(threshold);
        BigDecimal honor = BigDecimal.valueOf(PredictorService.HONOR_THRESHOLD);

        for (int t = 0; t <= 300; t++) {
            for (int a = 0; a <= t; a++) {
                PredictorResult result = predictorService.calculate(a, t, threshold, null);

                assertEquals(meets(a, t, target) ? "SAFE" : "RISK", result.getStatus());
                assertExtremes(a, t, target, result.getSafeSkips(), result.getMustAttend());
                assertExtremes(a, t, honor, result.getHonorSafeSkips(), result.getHonorMustAttend());
            }
        }
    }

    @Test
    void customThresholdsAreFreeOfFloatingPointDrift() {
        // 1 - 0.8 is 0.19999999999999996 in floating point, which once pushed this to 5
        assertEquals(4, predictorService.calculate(0, 1, 0.80, null).getMustAttend());
        // 17 / 20 is exactly 85%
        assertEquals(6, predictorService.calculate(11, 14, 0.75, null).getHonorMustAttend());
    }

    @Test
    void testCustomThreshold() {
        // Target 80% threshold (0.80) -> 35 present out of 40 = 87.5% -> safe skips = floor((35 - 32) / 0.8) = floor(3.75) = 3
        PredictorResult result = predictorService.calculate(35, 40, 0.80, null);
        assertEquals(87.5, result.getPct());
        assertEquals("SAFE", result.getStatus());
        assertEquals(3, result.getSafeSkips());
        assertEquals(0, result.getMustAttend());
        assertEquals(80.0, result.getThreshold());
    }

    @Test
    void fractionalThresholdIsMetExactlyAndLabelledAsWritten() {
        // 29 / 40 is exactly 72.5%
        PredictorResult result = predictorService.calculate(29, 40, 0.725, null);
        assertEquals("SAFE", result.getStatus());
        assertEquals(0, result.getSafeSkips());
        assertEquals(72.5, result.getThreshold());
        assertTrue(result.getMessage().contains("72.5%"), result.getMessage());
        // the institutional line still decides defaulter status
        assertTrue(result.isDefaulter());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(doubles = {0.0, -0.5, 1.0, 1.5, Double.NaN})
    void fallsBackToSeventyFivePercentForAnUnusableThreshold(Double threshold) {
        PredictorResult result = predictorService.calculate(29, 40, threshold, null);
        assertEquals(75.0, result.getThreshold());
        assertEquals(4, result.getMustAttend());
    }

    @ParameterizedTest(name = "A={0}, T={1}")
    @CsvSource({"-1, 0", "0, -1", "-3, -1", "5, 4", "1, 0"})
    void rejectsImpossibleCounts(int attended, int conducted) {
        assertThrows(IllegalArgumentException.class,
                () -> predictorService.calculate(attended, conducted, 0.75, null));
    }

    @Test
    void testZeroTotalLectures() {
        PredictorResult result = predictorService.calculate(0, 0, 0.75, null);
        assertEquals(0.0, result.getPct());
        assertEquals(0, result.getSafeSkips());
        assertEquals(0, result.getMustAttend());
        assertEquals("SAFE", result.getStatus());
        assertFalse(result.isDefaulter());
        assertTrue(result.isRecoverable());
        assertEquals("No attendance records recorded yet.", result.getMessage());
    }

    @Test
    void messagesDescribeTheBufferOrTheRecovery() {
        assertTrue(predictorService.calculate(30, 40, 0.75, null).getMessage().contains("right on the edge of the 75%"));
        assertTrue(predictorService.calculate(33, 40, 0.75, null).getMessage().contains("Safe to skip 4 more"));
        assertTrue(predictorService.calculate(29, 40, 0.75, null).getMessage().contains("Must attend 4 consecutive"));
    }

    @Test
    void testUnrecoverableState() {
        // 10 present out of 40, totalPlanned = 50 -> remaining = 10 -> max = (10+10)/50 = 40.0% < 75%
        PredictorResult result = predictorService.calculate(10, 40, 0.75, 50);
        assertEquals(25.0, result.getPct());
        assertEquals("RISK", result.getStatus());
        assertFalse(result.isRecoverable());
        assertEquals(40.0, result.getMaxPossiblePct());
        assertTrue(result.getMessage().contains("cannot reach"));
    }

    @Test
    void recoverableOnlyWhileEnoughPlannedLecturesRemain() {
        // 29 / 40 needs 4 more: 44 planned leaves exactly 4
        PredictorResult justEnough = predictorService.calculate(29, 40, 0.75, 44);
        assertTrue(justEnough.isRecoverable());
        assertEquals(4, justEnough.getRemainingPlanned());
        assertEquals(75.0, justEnough.getMaxPossiblePct());

        PredictorResult oneShort = predictorService.calculate(29, 40, 0.75, 43);
        assertFalse(oneShort.isRecoverable());
        assertEquals(74.4, oneShort.getMaxPossiblePct());

        PredictorResult termOverAtRisk = predictorService.calculate(29, 40, 0.75, 40);
        assertFalse(termOverAtRisk.isRecoverable());
        assertEquals(0, termOverAtRisk.getRemainingPlanned());

        PredictorResult termOverSafe = predictorService.calculate(30, 40, 0.75, 40);
        assertTrue(termOverSafe.isRecoverable());

        // a plan smaller than what was already conducted is ignored
        PredictorResult stalePlan = predictorService.calculate(29, 40, 0.75, 30);
        assertTrue(stalePlan.isRecoverable());
        assertNull(stalePlan.getRemainingPlanned());
        assertNull(stalePlan.getMaxPossiblePct());
    }

    @Test
    void serialisesTheStructuredPredictorContract() {
        JsonNode json = new ObjectMapper().valueToTree(predictorService.calculate(33, 40, 0.75, null));

        assertEquals(82.5, json.get("currentPercentage").asDouble());
        assertEquals(4, json.get("safeSkips").asInt());
        assertEquals(0, json.get("mustAttend").asInt());
        assertEquals("SAFE", json.get("status").asText());
    }

    /**
     * Checks S and R by their definition — the most lectures that can be missed, the fewest that must
     * be attended — without reusing any formula from the service.
     */
    private static void assertExtremes(int a, int t, BigDecimal target, int safeSkips, int mustAttend) {
        String at = "A=" + a + ", T=" + t + " @ " + target;
        if (meets(a, t, target)) {
            assertEquals(0, mustAttend, at);
            assertTrue(meets(a, t + safeSkips, target), at + ": safe skips drop below the threshold");
            assertFalse(meets(a, t + safeSkips + 1, target), at + ": one more skip would still be safe");
        } else {
            assertEquals(0, safeSkips, at);
            assertTrue(meets(a + mustAttend, t + mustAttend, target), at + ": must-attend falls short");
            assertFalse(meets(a + mustAttend - 1, t + mustAttend - 1, target), at + ": one fewer would do");
        }
    }

    private static boolean meets(long attended, long conducted, BigDecimal target) {
        return BigDecimal.valueOf(attended).compareTo(target.multiply(BigDecimal.valueOf(conducted))) >= 0;
    }
}
