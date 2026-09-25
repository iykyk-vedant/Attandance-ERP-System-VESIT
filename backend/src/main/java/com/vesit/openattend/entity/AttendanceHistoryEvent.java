package com.vesit.openattend.entity;

import com.vesit.openattend.entity.enums.AttendanceStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_history_events")
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

    @Column(name = "modified_by", nullable = false, length = 255, updatable = false)
    private String modifiedBy;

    @Column(name = "mandatory_reason", nullable = false, length = 1000, updatable = false)
    private String mandatoryReason;

    @Column(name = "sync_log_id", length = 36)
    private String syncLogId;
}
