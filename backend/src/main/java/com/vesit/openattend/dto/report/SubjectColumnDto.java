package com.vesit.openattend.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A subject column present in the report, in export column order. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectColumnDto {
    private String code;
    private String name;
}
