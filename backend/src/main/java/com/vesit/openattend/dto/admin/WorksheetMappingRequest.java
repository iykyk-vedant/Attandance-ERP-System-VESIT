package com.vesit.openattend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorksheetMappingRequest {
    @NotBlank(message = "Subject Code is required")
    private String subjectCode;

    private String subjectName;

    @NotBlank(message = "Sheet ID is required")
    private String sheetId;

    @NotBlank(message = "Worksheet Name is required")
    private String worksheetName;

    @NotBlank(message = "Range is required")
    private String range;

    private Map<String, String> columnRoles;
}
