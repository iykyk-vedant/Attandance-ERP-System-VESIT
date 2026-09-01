package com.vesit.openattend;

import com.vesit.openattend.service.predictor.PredictorResult;
import com.vesit.openattend.service.predictor.PredictorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PredictorServiceTest {

    private PredictorService predictorService;

    @BeforeEach
    void setUp() {
        predictorService = new PredictorService();
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
    }

    @Test
    void testSafeAttendanceState() {
        // 36 present out of 42 = 85.7% -> safe skips = floor((36 - 31.5) / 0.75) = floor(4.5 / 0.75) = 6
        PredictorResult result = predictorService.calculate(36, 42, 0.75, null);
        assertEquals(85.7, result.getPct());
        assertEquals("SAFE", result.getStatus());
        assertEquals(6, result.getSafeSkips());
        assertEquals(0, result.getMustAttend());
        assertFalse(result.isDefaulter());
    }

    @Test
    void testRiskAttendanceState() {
        // 29 present out of 40 = 72.5% -> must attend = ceil((0.75*40 - 29) / 0.25) = ceil((30 - 29)/0.25) = 4
        PredictorResult result = predictorService.calculate(29, 40, 0.75, null);
        assertEquals(72.5, result.getPct());
        assertEquals("RISK", result.getStatus());
        assertEquals(0, result.getSafeSkips());
        assertEquals(4, result.getMustAttend());
        assertTrue(result.isDefaulter());
    }

    @Test
    void testOverallAttendanceReferenceVector() {
        // 101 present out of 126 = 80.2% -> safe skips = floor((101 - (126*0.75)) / 0.75) = floor(6.5 / 0.75) = 8
        PredictorResult result = predictorService.calculate(101, 126, 0.75, null);
        assertEquals(80.2, result.getPct());
        assertEquals("SAFE", result.getStatus());
        assertEquals(8, result.getSafeSkips());
        assertEquals(0, result.getMustAttend());
        assertFalse(result.isDefaulter());
    }

    @Test
    void testPerfectAttendanceState() {
        // 40 present out of 40 = 100.0% -> safe skips = floor((40 - 30) / 0.75) = floor(10 / 0.75) = 13
        PredictorResult result = predictorService.calculate(40, 40, 0.75, null);
        assertEquals(100.0, result.getPct());
        assertEquals("SAFE", result.getStatus());
        assertEquals(13, result.getSafeSkips());
        assertEquals(0, result.getMustAttend());
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
    void testCustomThreshold() {
        // Target 80% threshold (0.80) -> 35 present out of 40 = 87.5% -> safe skips = floor((35 - 32) / 0.8) = floor(3.75) = 3
        PredictorResult result = predictorService.calculate(35, 40, 0.80, null);
        assertEquals(87.5, result.getPct());
        assertEquals("SAFE", result.getStatus());
        assertEquals(3, result.getSafeSkips());
        assertEquals(0, result.getMustAttend());
        assertEquals(80.0, result.getThreshold());
    }
}
