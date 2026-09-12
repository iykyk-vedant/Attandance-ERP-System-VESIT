package com.vesit.openattend.controller;

import com.vesit.openattend.dto.faculty.ProxyAssignRequest;
import com.vesit.openattend.dto.faculty.ProxyAssignmentDto;
import com.vesit.openattend.security.CallerRoles;
import com.vesit.openattend.service.attendance.ProxyDelegationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Proxy / substitute lecture delegation. Access is already limited to teaching roles by the
 * {@code /api/v1/faculty/**} rule in the security config; slot-level ownership is enforced
 * inside {@link ProxyDelegationService}.
 */
@RestController
@RequestMapping("/api/v1/faculty/proxy")
@RequiredArgsConstructor
public class ProxyDelegationController {

    private final ProxyDelegationService proxyDelegationService;

    @PostMapping("/assign")
    public ResponseEntity<Map<String, Object>> assign(
            @Valid @RequestBody ProxyAssignRequest request,
            Authentication authentication
    ) {
        ProxyAssignmentDto assignment = proxyDelegationService.assignProxy(
                callerEmail(authentication), CallerRoles.of(authentication), request);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Lecture delegated to " + assignment.getProxyFacultyEmail(),
                "assignment", assignment
        ));
    }

    /** Lectures delegated TO the caller — the substitute dashboard feed. Defaults to today. */
    @GetMapping("/my-assignments")
    public ResponseEntity<Map<String, Object>> myAssignments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication
    ) {
        LocalDate start = from != null ? from : (date != null ? date : LocalDate.now());
        LocalDate end = to != null ? to : (date != null ? date : start);

        List<ProxyAssignmentDto> assignments =
                proxyDelegationService.getDelegationsReceived(callerEmail(authentication), start, end);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "from", start.toString(),
                "to", end.toString(),
                "count", assignments.size(),
                "assignments", assignments
        ));
    }

    /** Lectures the caller has delegated AWAY — their side of the audit trail. */
    @GetMapping("/delegated-by-me")
    public ResponseEntity<Map<String, Object>> delegatedByMe(Authentication authentication) {
        List<ProxyAssignmentDto> assignments =
                proxyDelegationService.getDelegationsGivenAway(callerEmail(authentication));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "count", assignments.size(),
                "assignments", assignments
        ));
    }

    @DeleteMapping("/{assignmentId}")
    public ResponseEntity<Map<String, Object>> revoke(
            @PathVariable String assignmentId,
            Authentication authentication
    ) {
        ProxyAssignmentDto assignment = proxyDelegationService.revoke(
                callerEmail(authentication), CallerRoles.of(authentication), assignmentId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Proxy delegation revoked",
                "assignment", assignment
        ));
    }

    private String callerEmail(Authentication authentication) {
        return authentication != null ? authentication.getName() : "faculty@ves.ac.in";
    }
}
