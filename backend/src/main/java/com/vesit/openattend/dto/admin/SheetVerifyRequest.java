package com.vesit.openattend.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SheetVerifyRequest {
    @NotBlank(message = "Sheet ID is required")
    private String sheetId;
}
