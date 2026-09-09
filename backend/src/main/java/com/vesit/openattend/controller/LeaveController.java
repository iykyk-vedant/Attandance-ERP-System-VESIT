package com.vesit.openattend.controller;

import com.vesit.openattend.dto.leave.LeaveRequestDto;
import com.vesit.openattend.dto.leave.LeaveSubmissionForm;
import com.vesit.openattend.entity.LeaveProofDocument;
import com.vesit.openattend.security.CallerRoles;
import com.vesit.openattend.service.leave.LeaveRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Student side of the paperless OD / medical leave workflow: file an application with its proof,
 * and watch it move through the queue.
 *
 * <p>The whole prefix requires authentication (see the security config) because a proof is a
 * medical certificate as often as it is an event invite.
 */
@RestController
@RequestMapping("/api/v1/leave")
@RequiredArgsConstructor
public class LeaveController {

    /** Roles that may open any student's proof while reviewing the queue. */
    private static final Set<String> TEACHING_ROLES = Set.of(
            "ROLE_FACULTY", "ROLE_CLASS_TEACHER", "ROLE_ADMIN", "ROLE_SUPER_ADMIN"
    );

    private final LeaveRequestService leaveRequestService;

    /**
     * Files one application. Sent as {@code multipart/form-data} so the event letter, invite or
     * medical certificate travels with the form fields in a single request.
     */
    @PostMapping(path = "/requests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> submit(
            @Valid @ModelAttribute LeaveSubmissionForm form,
            Authentication authentication
    ) {
        LeaveRequestDto request = leaveRequestService.submit(callerEmail(authentication), form);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Leave request submitted for approval - no paper letter needed.",
                "request", request
        ));
    }

    /** The student's own applications, newest first. */
    @GetMapping("/requests/mine")
    public ResponseEntity<Map<String, Object>> myRequests(Authentication authentication) {
        List<LeaveRequestDto> requests = leaveRequestService.myRequests(callerEmail(authentication));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "count", requests.size(),
                "requests", requests
        ));
    }

    /**
     * Streams the attached proof. Served inline so a teacher can eyeball a certificate without
     * downloading it, and always under the type the file actually is rather than the one the
     * browser claimed at upload.
     */
    @GetMapping("/requests/{requestId}/proof")
    public ResponseEntity<byte[]> proof(@PathVariable String requestId, Authentication authentication) {
        LeaveProofDocument proof = leaveRequestService.proofFor(
                callerEmail(authentication), isTeachingStaff(authentication), requestId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(proof.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + proof.getFileName() + "\"")
                // A proof is only ever served to its owner or to reviewing staff.
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .contentLength(proof.getContent().length)
                .body(proof.getContent());
    }

    private boolean isTeachingStaff(Authentication authentication) {
        return CallerRoles.of(authentication).stream().anyMatch(TEACHING_ROLES::contains);
    }

    private String callerEmail(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }
}
