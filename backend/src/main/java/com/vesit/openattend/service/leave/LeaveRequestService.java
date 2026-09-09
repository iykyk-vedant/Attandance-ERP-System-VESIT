package com.vesit.openattend.service.leave;

import com.vesit.openattend.dto.leave.*;
import com.vesit.openattend.entity.LeaveProofDocument;
import com.vesit.openattend.entity.LeaveRequest;
import com.vesit.openattend.entity.Notification;
import com.vesit.openattend.entity.Student;
import com.vesit.openattend.entity.enums.LeaveRequestStatus;
import com.vesit.openattend.exception.LeaveRequestDeniedException;
import com.vesit.openattend.repository.LeaveProofDocumentRepository;
import com.vesit.openattend.repository.LeaveRequestRepository;
import com.vesit.openattend.repository.NotificationRepository;
import com.vesit.openattend.repository.StudentRepository;
import com.vesit.openattend.service.attendance.AttendancePercentage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The paperless OD / medical leave workflow.
 *
 * <p>A student files one application with its proof attached; a teacher approves or rejects it in
 * one click. An approval is not just a status change - it runs {@link DutyAttendanceAdjuster} over
 * the leave window, so the student percentage and their place on the defaulter list are correct
 * the moment the button is pressed, with no separate recalculation step to remember.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveRequestService {

    /** Longest window a single application may cover; anything longer is a term break, not a leave. */
    public static final int MAX_LEAVE_DAYS = 30;

    /** A rejection has to say why - the student is being sent back to a paper process otherwise. */
    private static final int MIN_REJECTION_NOTE_LENGTH = 10;

    /** Requests that still hold a claim on the student attendance, so a new one may not overlap. */
    private static final EnumSet<LeaveRequestStatus> BLOCKING_STATUSES =
            EnumSet.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.APPROVED);

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveProofDocumentRepository proofRepository;
    private final StudentRepository studentRepository;
    private final NotificationRepository notificationRepository;
    private final LeaveProofStorageService proofStorageService;
    private final DutyAttendanceAdjuster dutyAttendanceAdjuster;

    // ------------------------------------------------------------------ student portal

    @Transactional
    public LeaveRequestDto submit(String studentEmail, LeaveSubmissionForm form) {
        Student student = requireStudent(studentEmail);

        LocalDate from = form.getFromDate();
        LocalDate to = form.getToDate();

        if (to.isBefore(from)) {
            throw new IllegalArgumentException("toDate (" + to + ") must not be before fromDate (" + from + ")");
        }

        int days = (int) (to.toEpochDay() - from.toEpochDay()) + 1;
        if (days > MAX_LEAVE_DAYS) {
            throw new IllegalArgumentException("A leave application may span at most " + MAX_LEAVE_DAYS
                    + " days; this one covers " + days + ". Split it into separate applications.");
        }

        rejectOverlapping(student, from, to);

        LeaveRequest request = LeaveRequest.builder()
                .id(UUID.randomUUID().toString())
                .student(student)
                .leaveType(form.getLeaveType())
                .eventName(form.getEventName().trim())
                .reason(trimToNull(form.getReason()))
                .fromDate(from)
                .toDate(to)
                .status(LeaveRequestStatus.PENDING)
                .submittedBy(studentEmail)
                .sessionsAdjusted(0)
                .build();

        // Validate the attachment before the request row exists, so a rejected upload leaves
        // nothing behind for a teacher to find in the queue without its proof.
        LeaveProofDocument proof = proofStorageService.accept(request, form.getProof());

        leaveRequestRepository.saveAndFlush(request);
        proofRepository.save(proof);

        log.info("Leave request {} filed by {} ({}): {} {}..{} with {} ({} bytes)",
                request.getId(), student.getRollNo(), studentEmail, form.getLeaveType(), from, to,
                proof.getFileName(), proof.getSizeBytes());

        return toDto(request, new LeaveProofMetadata(request.getId(), proof.getFileName(),
                proof.getContentType(), proof.getSizeBytes(), proof.getSha256()));
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestDto> myRequests(String studentEmail) {
        Student student = requireStudent(studentEmail);
        return toDtos(leaveRequestRepository.findForStudent(student.getId()));
    }

    // ------------------------------------------------------------ approval dashboard

    /** The queue a teacher works through. A null status or division widens the view. */
    @Transactional(readOnly = true)
    public List<LeaveRequestDto> queue(LeaveRequestStatus status, String division) {
        String normalisedDivision = (division == null || division.isBlank()) ? null : division.trim().toUpperCase();
        return toDtos(leaveRequestRepository.findForApprovalQueue(status, normalisedDivision));
    }

    /**
     * Approves a request and immediately transitions the student ABSENT records in the window to
     * {@code DUTY_PRESENT}.
     *
     * <p>Re-approving an already-approved request returns the original decision untouched rather
     * than adjusting a second time - a double-tapped Approve button must not double-count.
     */
    @Transactional
    public LeaveDecisionResponse approve(String approverEmail, String requestId, LeaveDecisionRequest decision) {
        LeaveRequest request = requireRequest(requestId);

        if (request.getStatus() == LeaveRequestStatus.APPROVED) {
            return alreadyDecided(request, "Leave request was already approved by " + request.getDecidedBy());
        }
        requirePending(request, "approve");

        AttendancePercentage.Tally before = dutyAttendanceAdjuster.overallTally(request.getStudent().getId());
        int adjusted = dutyAttendanceAdjuster.applyApprovedLeave(request, approverEmail);
        AttendancePercentage.Tally after = dutyAttendanceAdjuster.overallTally(request.getStudent().getId());

        request.setStatus(LeaveRequestStatus.APPROVED);
        request.setDecidedBy(approverEmail);
        request.setDecidedAt(LocalDateTime.now());
        request.setDecisionNote(trimToNull(decision != null ? decision.getDecisionNote() : null));
        request.setSessionsAdjusted(adjusted);
        leaveRequestRepository.save(request);

        AttendanceImpactDto impact = impactOf(before, after, adjusted);
        notifyStudent(request, "Approved: " + request.getEventName() + " (" + request.getFromDate()
                + " to " + request.getToDate() + "). " + adjusted
                + " lecture(s) credited as duty-present; attendance is now " + impact.getPercentageAfter() + "%.");

        return LeaveDecisionResponse.builder()
                .success(true)
                .message(adjusted > 0
                        ? "Approved. " + adjusted + " attendance record(s) moved to DUTY_PRESENT and percentages recalculated."
                        : "Approved. No attendance has been marked in this window yet - lectures marked during it will be credited automatically.")
                .request(toDto(request, proofMetadata(request.getId())))
                .attendanceImpact(impact)
                .build();
    }

    /** Rejects a request. Nothing about the student attendance changes. */
    @Transactional
    public LeaveDecisionResponse reject(String approverEmail, String requestId, LeaveDecisionRequest decision) {
        LeaveRequest request = requireRequest(requestId);

        if (request.getStatus() == LeaveRequestStatus.REJECTED) {
            return alreadyDecided(request, "Leave request was already rejected by " + request.getDecidedBy());
        }
        requirePending(request, "reject");

        String note = trimToNull(decision != null ? decision.getDecisionNote() : null);
        if (note == null || note.length() < MIN_REJECTION_NOTE_LENGTH) {
            throw new IllegalArgumentException("A rejection must state why, in at least "
                    + MIN_REJECTION_NOTE_LENGTH + " characters, so the student knows what to fix.");
        }

        request.setStatus(LeaveRequestStatus.REJECTED);
        request.setDecidedBy(approverEmail);
        request.setDecidedAt(LocalDateTime.now());
        request.setDecisionNote(note);
        leaveRequestRepository.save(request);

        log.info("Leave request {} rejected by {}", request.getId(), approverEmail);
        notifyStudent(request, "Rejected: " + request.getEventName() + ". " + note);

        return LeaveDecisionResponse.builder()
                .success(true)
                .message("Rejected. Attendance is unchanged.")
                .request(toDto(request, proofMetadata(request.getId())))
                .build();
    }

    // -------------------------------------------------------------------- attachment

    /**
     * The stored proof, for the owning student or for teaching staff reviewing it.
     *
     * @param teachingStaff true when the caller holds a teaching role, granting access to any
     *                      proof in the queue; a student may only open their own
     */
    @Transactional(readOnly = true)
    public LeaveProofDocument proofFor(String callerEmail, boolean teachingStaff, String requestId) {
        LeaveRequest request = requireRequest(requestId);

        if (!teachingStaff && !request.getSubmittedBy().equalsIgnoreCase(callerEmail)) {
            throw new LeaveRequestDeniedException("This leave request belongs to another student.");
        }

        return proofRepository.findByLeaveRequestId(requestId)
                .orElseThrow(() -> new IllegalArgumentException("No proof is attached to leave request " + requestId));
    }

    // ------------------------------------------------------------------------ helpers

    private void rejectOverlapping(Student student, LocalDate from, LocalDate to) {
        for (LeaveRequest existing : leaveRequestRepository.findByStudentIdAndStatusIn(student.getId(), BLOCKING_STATUSES)) {
            if (existing.overlaps(from, to)) {
                throw new IllegalStateException("A " + existing.getStatus() + " leave request already covers "
                        + existing.getFromDate() + " to " + existing.getToDate()
                        + " (" + existing.getEventName() + "). It has to be settled before another can overlap it.");
            }
        }
    }

    private void requirePending(LeaveRequest request, String action) {
        if (request.getStatus() != LeaveRequestStatus.PENDING) {
            throw new IllegalStateException("Cannot " + action + " a leave request that is already "
                    + request.getStatus() + " (decided by " + request.getDecidedBy() + ").");
        }
    }

    /** A repeat of a decision already taken: report the existing state, change nothing. */
    private LeaveDecisionResponse alreadyDecided(LeaveRequest request, String message) {
        return LeaveDecisionResponse.builder()
                .success(true)
                .message(message + " - no further changes were made.")
                .request(toDto(request, proofMetadata(request.getId())))
                .build();
    }

    private AttendanceImpactDto impactOf(AttendancePercentage.Tally before, AttendancePercentage.Tally after, int adjusted) {
        BigDecimal threshold = AttendancePercentage.DEFAULT_THRESHOLD;
        BigDecimal pctBefore = before.percentage();
        BigDecimal pctAfter = after.percentage();

        boolean defaulterBefore = AttendancePercentage.isDefaulter(pctBefore, threshold);
        boolean defaulterAfter = AttendancePercentage.isDefaulter(pctAfter, threshold);

        return AttendanceImpactDto.builder()
                .sessionsAdjusted(adjusted)
                .totalConducted(after.conducted())
                .attendedBefore(before.attended())
                .attendedAfter(after.attended())
                .percentageBefore(pctBefore)
                .percentageAfter(pctAfter)
                .threshold(threshold)
                .defaulterBefore(defaulterBefore)
                .defaulterAfter(defaulterAfter)
                .clearedFromDefaulterList(defaulterBefore && !defaulterAfter)
                .build();
    }

    /**
     * Tells the student the outcome through the existing notification feed. The request id doubles
     * as the deduplication key, so one decision can only ever produce one notification.
     */
    private void notifyStudent(LeaveRequest request, String message) {
        notificationRepository.save(Notification.builder()
                .id(UUID.randomUUID().toString())
                .student(request.getStudent())
                .type("leave_decision")
                .message(message)
                .isRead(false)
                .syncLogId(request.getId())
                .build());
    }

    private Student requireStudent(String email) {
        return studentRepository.findByUserEmail(email)
                .orElseThrow(() -> new LeaveRequestDeniedException(
                        "No student record is linked to " + email + ". Only students can file leave requests."));
    }

    private LeaveRequest requireRequest(String requestId) {
        return leaveRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found: " + requestId));
    }

    private LeaveProofMetadata proofMetadata(String requestId) {
        List<LeaveProofMetadata> found = proofRepository.findMetadataFor(List.of(requestId));
        return found.isEmpty() ? null : found.get(0);
    }

    /** Maps a page of requests, resolving every attachment metadata row in one extra query. */
    private List<LeaveRequestDto> toDtos(List<LeaveRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }

        Map<String, LeaveProofMetadata> proofs = new HashMap<>();
        List<String> ids = requests.stream().map(LeaveRequest::getId).toList();
        for (LeaveProofMetadata metadata : proofRepository.findMetadataFor(ids)) {
            proofs.put(metadata.leaveRequestId(), metadata);
        }

        List<LeaveRequestDto> dtos = new ArrayList<>(requests.size());
        for (LeaveRequest request : requests) {
            dtos.add(toDto(request, proofs.get(request.getId())));
        }
        return dtos;
    }

    private LeaveRequestDto toDto(LeaveRequest request, LeaveProofMetadata proof) {
        Student student = request.getStudent();

        return LeaveRequestDto.builder()
                .id(request.getId())
                .studentId(student.getId())
                .rollNo(student.getRollNo())
                .studentName(student.getName())
                .division(student.getDivision())
                .batch(student.getBatch())
                .leaveType(request.getLeaveType().name())
                .eventName(request.getEventName())
                .reason(request.getReason())
                .fromDate(request.getFromDate().toString())
                .toDate(request.getToDate().toString())
                .days(request.getDays())
                .status(request.getStatus().name())
                .submittedBy(request.getSubmittedBy())
                .submittedAt(request.getCreatedAt() != null ? request.getCreatedAt().toString() : null)
                .decidedBy(request.getDecidedBy())
                .decidedAt(request.getDecidedAt() != null ? request.getDecidedAt().toString() : null)
                .decisionNote(request.getDecisionNote())
                .sessionsAdjusted(request.getSessionsAdjusted() != null ? request.getSessionsAdjusted() : 0)
                .proof(proof == null ? null : LeaveProofDto.builder()
                        .fileName(proof.fileName())
                        .contentType(proof.contentType())
                        .sizeBytes(proof.sizeBytes() != null ? proof.sizeBytes() : 0L)
                        .sha256(proof.sha256())
                        .downloadUrl("/api/v1/leave/requests/" + request.getId() + "/proof")
                        .build())
                .build();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
