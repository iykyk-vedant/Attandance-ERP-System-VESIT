package com.vesit.openattend.entity;

import com.vesit.openattend.entity.enums.AttendanceStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * Append-only audit row. Once written, a history event is never updated or deleted —
 * {@link Immutable} makes Hibernate ignore any dirty state on a loaded instance.
 */
@Entity
@Table(name = "attendance_history_events")
@Immutable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceHistoryEvent {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_record_id", nullable = false)
    private AttendanceRecord attendanceRecord;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 32)
    private AttendanceStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 32)
    private AttendanceStatus newStatus;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    /** Email of the user who authored this change; null for automated sync writes. */
    @Column(name = "modified_by", length = 255)
    private String modifiedBy;

    /** Mandatory justification captured on a faculty correction (min 10 chars). */
    @Column(name = "mandatory_reason", columnDefinition = "TEXT")
    private String mandatoryReason;

    @Column(name = "sync_log_id", length = 36)
    private String syncLogId;
}
