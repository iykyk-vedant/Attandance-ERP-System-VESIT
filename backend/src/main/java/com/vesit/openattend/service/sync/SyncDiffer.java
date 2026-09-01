package com.vesit.openattend.service.sync;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;

public class SyncDiffer {

    public static String computeHash(String input) {
        if (input == null) {
            input = "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedHash.length);
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public static String computeRowHash(List<Object> row) {
        if (row == null || row.isEmpty()) {
            return computeHash("");
        }
        String canonical = row.stream()
                .map(cell -> cell == null ? "" : cell.toString().trim())
                .collect(Collectors.joining("|"));
        return computeHash(canonical);
    }

    public static String computeRangeHash(List<List<Object>> values) {
        if (values == null || values.isEmpty()) {
            return computeHash("");
        }
        StringBuilder canonical = new StringBuilder();
        for (List<Object> row : values) {
            if (row != null) {
                String rowString = row.stream()
                        .map(cell -> cell == null ? "" : cell.toString().trim())
                        .collect(Collectors.joining("|"));
                canonical.append(rowString).append("\n");
            }
        }
        return computeHash(canonical.toString());
    }
}
