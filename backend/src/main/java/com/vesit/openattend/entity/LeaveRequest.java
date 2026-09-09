package com.vesit.openattend.entity;

import com.vesit.openattend.entity.enums.LeaveRequestStatus;
import com.vesit.openattend.entity.enums.LeaveType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A paperless Official Duty / medical leave application covering an inclusive date range.
 *
 * <p>Replaces the physical letter a student otherwise carries round campus: the proof is
 * attached once as {@link LeaveProofDocument}, a teacher approves or rejects it in one click,
 * and an approval transitions the student's ABSENT records in the window to
 * {@code DUTY_PRESENT} so their percentage recovers automatically.
 */
@Entity
@Table(name = "leave_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequest {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 32)
    private LeaveType leaveType;

    /** The hackathon, sports meet, fest or illness the leave is for. */
    @Column(name = "event_name", nullable = false)
    private String eventName;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    /** Inclusive — a single-day leave has {@code toDate == fromDate}. */
    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private LeaveRequestStatus status = LeaveRequestStatus.PENDING;

    /** Email of the account that filed the request — always the student themselves. */
    @Column(name = "submitted_by", nullable = false, length = 255)
    private String submittedBy;

    /** Email of the teacher / coordinator who approved or rejected it. */
    @Column(name = "decided_by", length = 255)
    private String decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    /**
     * How many attendance records the approval flipped to {@code DUTY_PRESENT}.
     *
     * <p>There is deliberately no association to {@link LeaveProofDocument} here: a {@code mappedBy}
     * one-to-one cannot be lazy without bytecode enhancement, so mapping it would drag every proof
     * file into memory each time the approval queue is listed. The proof is loaded on its own, by id.
     */
    @Column(name = "sessions_adjusted", nullable = false)
    @Builder.Default
    private Integer sessionsAdjusted = 0;


    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Inclusive span in days — 1 for a single-day leave. */
    public int getDays() {
        return (int) (toDate.toEpochDay() - fromDate.toEpochDay()) + 1;
    }

    /** True when {@code date} falls inside the inclusive leave window. */
    public boolean covers(LocalDate date) {
        return !date.isBefore(fromDate) && !date.isAfter(toDate);
    }

    /** True when this request's window intersects {@code [from, to]}. */
    public boolean overlaps(LocalDate from, LocalDate to) {
        return !fromDate.isAfter(to) && !toDate.isBefore(from);
    }
}
