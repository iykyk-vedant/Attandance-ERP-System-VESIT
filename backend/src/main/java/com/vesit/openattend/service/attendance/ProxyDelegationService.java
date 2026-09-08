package com.vesit.openattend.service.attendance;

import com.vesit.openattend.dto.faculty.ProxyAssignRequest;
import com.vesit.openattend.dto.faculty.ProxyAssignmentDto;
import com.vesit.openattend.entity.FacultySubjectAllocation;
import com.vesit.openattend.entity.ProxyAssignment;
import com.vesit.openattend.entity.Subject;
import com.vesit.openattend.entity.User;
import com.vesit.openattend.entity.enums.ProxyAssignmentStatus;
import com.vesit.openattend.entity.enums.Role;
import com.vesit.openattend.exception.ProxyDelegationDeniedException;
import com.vesit.openattend.repository.FacultySubjectAllocationRepository;
import com.vesit.openattend.repository.LectureSessionRepository;
import com.vesit.openattend.repository.ProxyAssignmentRepository;
import com.vesit.openattend.repository.SubjectRepository;
import com.vesit.openattend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Proxy / substitute lecture delegation.
 *
 * <p>A delegation hands temporary marking authorisation for one lecture slot to a colleague
 * while leaving the allocated subject teacher on the record. Nothing is deleted: revoking is
 * a status change, and every session marked under a delegation is stamped with
 * {@code isProxy = true} plus the original teacher, so the audit trail always names both.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProxyDelegationService {

    /** Roles that may delegate or revoke any slot, not just the ones they are allocated to. */
    private static final Set<String> DELEGATION_OVERRIDE_ROLES = Set.of(
            "ROLE_CLASS_TEACHER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN"
    );

    /** A substitute must be teaching staff — a student can never receive a delegation. */
    private static final Set<Role> ELIGIBLE_PROXY_ROLES = EnumSet.of(
            Role.FACULTY, Role.CLASS_TEACHER, Role.ADMIN, Role.SUPER_ADMIN
    );

    private final ProxyAssignmentRepository proxyAssignmentRepository;
    private final FacultySubjectAllocationRepository allocationRepository;
    private final LectureSessionRepository lectureSessionRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;

    @Transactional
    public ProxyAssignmentDto assignProxy(String callerEmail, Collection<String> callerRoles, ProxyAssignRequest request) {
        Subject subject = subjectRepository.findById(request.getSubjectId())
                .orElseThrow(() -> new IllegalArgumentException("Subject not found: " + request.getSubjectId()));

        String division = request.getDivision().trim();
        String batch = normalise(request.getBatch());
        LocalDate lectureDate = request.getLectureDate();
        Integer sessionIndex = request.getSessionIndex();
        String reason = request.getReason().trim();

        if (lectureDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot delegate a lecture in the past: " + lectureDate);
        }

        User proxyFaculty = userRepository.findByEmail(request.getProxyFacultyEmail().trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Proxy faculty not found: " + request.getProxyFacultyEmail()));

        if (Boolean.FALSE.equals(proxyFaculty.getIsActive())) {
            throw new IllegalArgumentException("Proxy faculty account is deactivated: " + proxyFaculty.getEmail());
        }
        if (!ELIGIBLE_PROXY_ROLES.contains(proxyFaculty.getRole())) {
            throw new IllegalArgumentException(
                    "Only teaching staff can take a proxy lecture: " + proxyFaculty.getEmail()
                            + " is a " + proxyFaculty.getRole());
        }

        // The delegation must come from someone who owns the slot — its allocated teacher —
        // or from a class teacher / admin acting on their behalf.
        List<FacultySubjectAllocation> owners = allocationsCovering(subject.getId(), division, batch);
        if (owners.isEmpty()) {
            throw new IllegalArgumentException(
                    "No subject teacher is allocated to " + subject.getCode() + " (" + cohortLabel(division, batch)
                            + "). Allocate the subject before delegating it.");
        }

        boolean callerOwnsSlot = owners.stream()
                .anyMatch(alloc -> alloc.getFaculty().getEmail().equalsIgnoreCase(callerEmail));
        if (!callerOwnsSlot && !hasOverride(callerRoles)) {
            throw new ProxyDelegationDeniedException(
                    "Only the allocated subject teacher, the class teacher or an admin can delegate "
                            + subject.getCode() + " (" + cohortLabel(division, batch) + ").");
        }

        User originalFaculty = owners.stream()
                .filter(alloc -> alloc.getFaculty().getEmail().equalsIgnoreCase(callerEmail))
                .findFirst()
                .orElse(owners.get(0))
                .getFaculty();

        if (originalFaculty.getId().equals(proxyFaculty.getId())) {
            throw new IllegalArgumentException(
                    "The allocated subject teacher cannot be their own substitute: " + proxyFaculty.getEmail());
        }

        // One active delegation per slot. Re-posting the same substitute is idempotent so a
        // retried request never produces a second authorisation row.
        List<ProxyAssignment> active = proxyAssignmentRepository
                .findBySubjectIdAndDivisionAndLectureDateAndStatus(
                        subject.getId(), division, lectureDate, ProxyAssignmentStatus.ACTIVE);

        for (ProxyAssignment existing : active) {
            if (!overlaps(existing, batch, sessionIndex)) {
                continue;
            }
            if (existing.getProxyFaculty().getId().equals(proxyFaculty.getId())
                    && Objects.equals(existing.getSessionIndex(), sessionIndex)
                    && Objects.equals(existing.getBatch(), batch)) {
                log.info("Proxy delegation {} already active for {} — returning the existing assignment",
                        existing.getId(), proxyFaculty.getEmail());
                return toDto(existing);
            }
            throw new IllegalStateException(
                    "A proxy delegation for " + subject.getCode() + " (" + cohortLabel(division, batch) + ") on "
                            + lectureDate + slotLabel(existing.getSessionIndex())
                            + " already exists for " + existing.getProxyFaculty().getEmail()
                            + ". Revoke it before assigning a different substitute.");
        }

        if (sessionIndex != null && sessionAlreadySubmitted(subject.getId(), division, batch, lectureDate, sessionIndex)) {
            throw new IllegalStateException(
                    "Attendance for " + subject.getCode() + " (" + cohortLabel(division, batch) + ") on "
                            + lectureDate + slotLabel(sessionIndex)
                            + " has already been submitted — there is nothing left to delegate.");
        }

        // Flushed on write so the generated created_at is present in the audit response and
        // the one-active-delegation-per-slot index rejects a racing duplicate immediately.
        ProxyAssignment assignment = proxyAssignmentRepository.saveAndFlush(ProxyAssignment.builder()
                .id(UUID.randomUUID().toString())
                .subject(subject)
                .division(division)
                .batch(batch)
                .lectureDate(lectureDate)
                .sessionIndex(sessionIndex)
                .originalFaculty(originalFaculty)
                .proxyFaculty(proxyFaculty)
                .assignedBy(callerEmail)
                .reason(reason)
                .status(ProxyAssignmentStatus.ACTIVE)
                .build());

        log.info("Proxy delegation {}: {} ({}) on {}{} delegated from {} to {} by {}",
                assignment.getId(), subject.getCode(), cohortLabel(division, batch), lectureDate,
                slotLabel(sessionIndex), originalFaculty.getEmail(), proxyFaculty.getEmail(), callerEmail);

        return toDto(assignment);
    }

    /** Delegations where the caller is the substitute, over an inclusive date window. */
    @Transactional(readOnly = true)
    public List<ProxyAssignmentDto> getDelegationsReceived(String proxyEmail, LocalDate from, LocalDate to) {
        User proxy = userRepository.findByEmail(proxyEmail).orElse(null);
        if (proxy == null) {
            return List.of();
        }
        return proxyAssignmentRepository
                .findByProxyFacultyIdAndLectureDateBetweenOrderByLectureDateAscCreatedAtAsc(proxy.getId(), from, to)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /** Delegations raised against the lectures the caller is allocated to — their side of the trail. */
    @Transactional(readOnly = true)
    public List<ProxyAssignmentDto> getDelegationsGivenAway(String facultyEmail) {
        User faculty = userRepository.findByEmail(facultyEmail).orElse(null);
        if (faculty == null) {
            return List.of();
        }
        return proxyAssignmentRepository
                .findByOriginalFacultyIdOrderByLectureDateDescCreatedAtDesc(faculty.getId())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProxyAssignmentDto revoke(String callerEmail, Collection<String> callerRoles, String assignmentId) {
        ProxyAssignment assignment = proxyAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new IllegalArgumentException("Proxy assignment not found: " + assignmentId));

        boolean callerRaisedIt = assignment.getAssignedBy().equalsIgnoreCase(callerEmail)
                || assignment.getOriginalFaculty().getEmail().equalsIgnoreCase(callerEmail);
        if (!callerRaisedIt && !hasOverride(callerRoles)) {
            throw new ProxyDelegationDeniedException(
                    "Only the original subject teacher, the assigner or an admin can revoke this delegation.");
        }

        if (assignment.getStatus() == ProxyAssignmentStatus.REVOKED) {
            return toDto(assignment);
        }

        assignment.setStatus(ProxyAssignmentStatus.REVOKED);
        assignment.setRevokedBy(callerEmail);
        assignment.setRevokedAt(LocalDateTime.now());
        proxyAssignmentRepository.save(assignment);

        log.info("Proxy delegation {} revoked by {}", assignment.getId(), callerEmail);
        return toDto(assignment);
    }

    /**
     * The active delegation authorising {@code facultyId} to mark this exact slot, if any.
     * The marking path uses it to grant a substitute write access for that slot only.
     */
    @Transactional(readOnly = true)
    public Optional<ProxyAssignment> findActiveDelegation(String facultyId, String subjectId, String division,
                                                          String batch, LocalDate lectureDate, Integer sessionIndex) {
        return proxyAssignmentRepository
                .findBySubjectIdAndDivisionAndLectureDateAndStatus(
                        subjectId, division, lectureDate, ProxyAssignmentStatus.ACTIVE)
                .stream()
                .filter(a -> a.getProxyFaculty().getId().equals(facultyId))
                .filter(a -> covers(a, batch, sessionIndex))
                .findFirst();
    }

    /** Active delegations the faculty has to take on a given date — their dashboard feed. */
    @Transactional(readOnly = true)
    public List<ProxyAssignment> activeDelegationsOn(String facultyId, LocalDate date) {
        return proxyAssignmentRepository.findByProxyFacultyIdAndLectureDateAndStatus(
                facultyId, date, ProxyAssignmentStatus.ACTIVE);
    }

    /** Allocations that own the requested cohort — a division-wide allocation covers every batch. */
    private List<FacultySubjectAllocation> allocationsCovering(String subjectId, String division, String batch) {
        return allocationRepository.findBySubjectIdAndDivision(subjectId, division).stream()
                .filter(alloc -> alloc.getBatch() == null || alloc.getBatch().equals(batch))
                .collect(Collectors.toList());
    }

    /** True when the delegation scope contains the slot being marked. */
    private boolean covers(ProxyAssignment assignment, String batch, Integer sessionIndex) {
        boolean batchCovered = assignment.getBatch() == null || assignment.getBatch().equals(batch);
        boolean slotCovered = assignment.getSessionIndex() == null
                || assignment.getSessionIndex().equals(sessionIndex);
        return batchCovered && slotCovered;
    }

    /** True when two delegation scopes intersect — either could end up authorising the same slot. */
    private boolean overlaps(ProxyAssignment assignment, String batch, Integer sessionIndex) {
        boolean batchOverlap = assignment.getBatch() == null || batch == null || assignment.getBatch().equals(batch);
        boolean slotOverlap = assignment.getSessionIndex() == null || sessionIndex == null
                || assignment.getSessionIndex().equals(sessionIndex);
        return batchOverlap && slotOverlap;
    }

    private boolean sessionAlreadySubmitted(String subjectId, String division, String batch,
                                            LocalDate lectureDate, Integer sessionIndex) {
        if (batch != null) {
            return lectureSessionRepository
                    .findBySubjectIdAndDivisionAndBatchAndSessionDateAndSessionIndex(
                            subjectId, division, batch, lectureDate, sessionIndex)
                    .isPresent();
        }
        return lectureSessionRepository
                .findBySubjectIdAndDivisionAndSessionDateAndSessionIndexAndBatchIsNull(
                        subjectId, division, lectureDate, sessionIndex)
                .isPresent();
    }

    private boolean hasOverride(Collection<String> callerRoles) {
        if (callerRoles == null) {
            return false;
        }
        return callerRoles.stream()
                .filter(Objects::nonNull)
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .anyMatch(DELEGATION_OVERRIDE_ROLES::contains);
    }

    private static String normalise(String value) {
        return (value != null && !value.trim().isEmpty()) ? value.trim() : null;
    }

    private static String cohortLabel(String division, String batch) {
        return batch != null ? division + " • " + batch : division;
    }

    private static String slotLabel(Integer sessionIndex) {
        return sessionIndex != null ? " [Slot " + sessionIndex + "]" : " [all slots]";
    }

    public ProxyAssignmentDto toDto(ProxyAssignment assignment) {
        Subject subject = assignment.getSubject();
        return ProxyAssignmentDto.builder()
                .id(assignment.getId())
                .subjectId(subject.getId())
                .subjectCode(subject.getCode())
                .subjectName(subject.getName())
                .division(assignment.getDivision())
                .batch(assignment.getBatch())
                .lectureDate(assignment.getLectureDate().toString())
                .sessionIndex(assignment.getSessionIndex())
                .originalFacultyEmail(assignment.getOriginalFaculty().getEmail())
                .proxyFacultyEmail(assignment.getProxyFaculty().getEmail())
                .assignedBy(assignment.getAssignedBy())
                .reason(assignment.getReason())
                .status(assignment.getStatus().name())
                .revokedBy(assignment.getRevokedBy())
                .revokedAt(assignment.getRevokedAt() != null ? assignment.getRevokedAt().toString() : null)
                .createdAt(assignment.getCreatedAt() != null ? assignment.getCreatedAt().toString() : null)
                .build();
    }
}
