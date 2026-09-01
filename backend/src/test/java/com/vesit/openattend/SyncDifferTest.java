package com.vesit.openattend;

import com.vesit.openattend.service.sync.SyncDiffer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SyncDifferTest {

    @Test
    void testComputeRowHashDeterministic() {
        List<Object> row1 = List.of("2026-08-05", "2024CS01", "Present", "Dr. Rao");
        List<Object> row2 = List.of("2026-08-05", "2024CS01", "Present", "Dr. Rao");

        String hash1 = SyncDiffer.computeRowHash(row1);
        String hash2 = SyncDiffer.computeRowHash(row2);

        assertNotNull(hash1);
        assertEquals(hash1, hash2);
    }

    @Test
    void testComputeRowHashSensitivityToChanges() {
        List<Object> rowOriginal = List.of("2026-08-05", "2024CS01", "Present", "Dr. Rao");
        List<Object> rowChanged = List.of("2026-08-05", "2024CS01", "Absent", "Dr. Rao");

        String hash1 = SyncDiffer.computeRowHash(rowOriginal);
        String hash2 = SyncDiffer.computeRowHash(rowChanged);

        assertNotEquals(hash1, hash2);
    }

    @Test
    void testComputeRangeHash() {
        List<List<Object>> values = List.of(
                List.of("Date", "RollNo", "Status"),
                List.of("2026-08-05", "2024CS01", "Present"),
                List.of("2026-08-05", "2024CS02", "Absent")
        );

        String rangeHash = SyncDiffer.computeRangeHash(values);
        assertNotNull(rangeHash);
        assertEquals(64, rangeHash.length()); // SHA-256 is 64 hex characters
    }
}
