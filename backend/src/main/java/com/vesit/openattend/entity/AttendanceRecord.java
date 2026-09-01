package com.vesit.openattend.entity;

import com.vesit.openattend.entity.enums.AttendanceStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "attendance_records",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "attendance_natural_key",
            columnNames = {"student_id", "subject_id", "lecture_date", "session_index"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceRecord {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(name = "lecture_date", nullable = false)
    private LocalDate lectureDate;

    @Column(name = "session_index", nullable = false)
    @Builder.Default
    private Integer sessionIndex = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AttendanceStatus status;

    @Column(length = 255)
    private String faculty;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(name = "source_row_hash", nullable = false, length = 64)
    private String sourceRowHash;

    @CreationTimestamp
    @Column(name = "synced_at", nullable = false, updatable = false)
    private LocalDateTime syncedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "attendanceRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AttendanceHistoryEvent> historyEvents = new ArrayList<>();
}
