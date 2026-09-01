package com.vesit.openattend.service.sync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RowMappingConfig {
    @Builder.Default
    private Map<String, String> columnRoles = new HashMap<>(); // e.g. {"date": "A", "rollNo": "B", "status": "C", "faculty": "D", "remarks": "E"}

    public static int columnLetterToIndex(String letter) {
        if (letter == null || letter.trim().isEmpty()) {
            return -1;
        }
        String clean = letter.trim().toUpperCase();
        int index = 0;
        for (int i = 0; i < clean.length(); i++) {
            index = index * 26 + (clean.charAt(i) - 'A' + 1);
        }
        return index - 1;
    }
}
