package com.vesit.openattend.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RosterPreviewResponse {
    private boolean success;
    private Map<String, Integer> summary; // {"total": X, "create": Y, "update": Z, "error": W}
    private List<RosterRowDto> rows;
}
