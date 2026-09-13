package com.vesit.openattend.controller;

import com.vesit.openattend.dto.leave.LeaveDecisionRequest;
import com.vesit.openattend.dto.leave.LeaveDecisionResponse;
import com.vesit.openattend.dto.leave.LeaveRequestDto;
import com.vesit.openattend.entity.enums.LeaveRequestStatus;
import com.vesit.openattend.service.leave.LeaveRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Teacher / coordinator side of the leave workflow: one screen listing what is waiting, and a
 * single click to approve or reject each one.
 *
 * <p>Access is already limited to teaching roles by the {@code /api/v1/faculty/**} rule in the
 * security config. An OD spans every subject a student is enrolled in, so the decision is not
 * narrowed to the subject teacher; instead every decision is stamped with the approver email and
 * carried on the record, and each attendance record it flips gets its own audit event.
 */
@RestController
@RequestMapping("/api/v1/faculty/leave")
@RequiredArgsConstructor
public class LeaveApprovalController {

    private final LeaveRequestService leaveRequestService;

    /**
     * The approval queue, oldest first. Defaults to {@code PENDING} - the only thing a teacher
     * needs to act on; pass {@code status=ALL} for the decided history.
     */
    @GetMapping("/requests")
    public ResponseEntity<Map<String, Object>> queue(
            @RequestParam(required = false, defaultValue = "PENDING") String status,
            @RequestParam(required = false) String division
    ) {
        LeaveRequestStatus filter = parseStatus(status);
        List<LeaveRequestDto> requests = leaveRequestService.queue(filter, division);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "status", filter != null ? filter.name() : "ALL",
                "count", requests.size(),
                "requests", requests
        ));
    }

    /** 1-click approve. Recalculates the student attendance before it returns. */
    @PostMapping("/requests/{requestId}/approve")
    public ResponseEntity<LeaveDecisionResponse> approve(
            @PathVariable String requestId,
            @Valid @RequestBody(required = false) LeaveDecisionRequest decision,
            Authentication authentication
    ) {
        return ResponseEntity.ok(leaveRequestService.approve(callerEmail(authentication), requestId, decision));
    }

    /** 1-click reject. Requires a note saying why; attendance is left untouched. */
    @PostMapping("/requests/{requestId}/reject")
    public ResponseEntity<LeaveDecisionResponse> reject(
            @PathVariable String requestId,
            @Valid @RequestBody(required = false) LeaveDecisionRequest decision,
            Authentication authentication
    ) {
        return ResponseEntity.ok(leaveRequestService.reject(callerEmail(authentication), requestId, decision));
    }

    /** {@code ALL} (or blank) widens the queue to every status. */
    private LeaveRequestStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim())) {
            return null;
        }
        try {
            return LeaveRequestStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported status '" + status
                    + "'. Supported: PENDING, APPROVED, REJECTED, ALL.");
        }
    }

    private String callerEmail(Authentication authentication) {
        return authentication != null ? authentication.getName() : "faculty@ves.ac.in";
    }
}
