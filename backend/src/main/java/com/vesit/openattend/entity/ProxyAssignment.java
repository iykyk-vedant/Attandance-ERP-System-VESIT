package com.vesit.openattend.entity;

import com.vesit.openattend.entity.enums.ProxyAssignmentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Temporary marking authorisation handed to a substitute ("proxy") teacher for a single
 * lecture slot. The slot is keyed by subject + cohort + date, optionally narrowed to one
 * timetable slot via {@link #sessionIndex}; a null session index covers every slot of that
 * subject and cohort on that date.
 */
@Entity
@Table(name = "proxy_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProxyAssignment {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(nullable = false, length = 32)
    private String division;

    @Column(length = 32)
    private String batch;

    @Column(name = "lecture_date", nullable = false)
    private LocalDate lectureDate;

    /** Null delegates every slot of the cohort on that date. */
    @Column(name = "session_index")
    private Integer sessionIndex;

    /** The allocated subject teacher the lecture is delegated away from. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_faculty_id", nullable = false)
    private User originalFaculty;

    /** The substitute who may mark this slot while the delegation is active. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proxy_faculty_id", nullable = false)
    private User proxyFaculty;

    /** Email of whoever raised the delegation — the subject teacher, class teacher or an admin. */
    @Column(name = "assigned_by", nullable = false, length = 255)
    private String assignedBy;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private ProxyAssignmentStatus status = ProxyAssignmentStatus.ACTIVE;

    @Column(name = "revoked_by", length = 255)
    private String revokedBy;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
