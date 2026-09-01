package com.vesit.openattend.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RosterRowDto {
    private String rollNo;
    private String name;
    private String email;
    private String division;
    private String batch;
    private String status; // "CREATE", "UPDATE", "ERROR"
    private String reason;
}
