package com.vesit.openattend.entity;

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
    name = "lecture_sessions",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_lecture_session",
            columnNames = {"subject_id", "division", "batch", "session_date", "session_index"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LectureSession {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id", nullable = false)
    private User faculty;

    @Column(nullable = false, length = 32)
    private String division;

    @Column(length = 32)
    private String batch;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "session_index", nullable = false)
    @Builder.Default
    private Integer sessionIndex = 1;

    @Column(name = "session_type", nullable = false, length = 32)
    @Builder.Default
    private String sessionType = "LECTURE";

    @Column(name = "topic_covered", length = 500)
    private String topicCovered;

    @Column(name = "total_students", nullable = false)
    @Builder.Default
    private Integer totalStudents = 0;

    @Column(name = "present_count", nullable = false)
    @Builder.Default
    private Integer presentCount = 0;

    @Column(name = "absent_count", nullable = false)
    @Builder.Default
    private Integer absentCount = 0;

    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @UpdateTimestamp
    @Column(name = "last_modified_at", nullable = false)
    private LocalDateTime lastModifiedAt;

    @Column(name = "correction_reason", columnDefinition = "TEXT")
    private String correctionReason;

    /** True when {@link #faculty} took this lecture as a substitute rather than as the allocated teacher. */
    @Column(name = "is_proxy", nullable = false)
    @Builder.Default
    private Boolean isProxy = false;

    /** The allocated subject teacher the lecture was delegated away from; null on a normal session. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_faculty_id")
    private User originalFaculty;

    /** The delegation that authorised the substitute, kept for the audit trail. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proxy_assignment_id")
    private ProxyAssignment proxyAssignment;

    @OneToMany(mappedBy = "lectureSession", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AttendanceRecord> attendanceRecords = new ArrayList<>();
}
