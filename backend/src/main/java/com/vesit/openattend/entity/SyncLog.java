package com.vesit.openattend.entity;

import com.vesit.openattend.entity.enums.SyncRunStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "sync_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncLog {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worksheet_mapping_id")
    private WorksheetMapping worksheetMapping;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SyncRunStatus status;

    @Column(name = "rows_read", nullable = false)
    @Builder.Default
    private Integer rowsRead = 0;

    @Column(name = "rows_upserted", nullable = false)
    @Builder.Default
    private Integer rowsUpserted = 0;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "duration_ms")
    private Integer durationMs;
}
