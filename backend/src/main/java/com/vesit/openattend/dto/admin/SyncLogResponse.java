package com.vesit.openattend.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncLogResponse {
    private String id;
    private String timestamp;
    private String sheet;
    private String status;
    private int rowsRead;
    private int rowsUpserted;
    private Integer durationMs;
    private String detail;
}
