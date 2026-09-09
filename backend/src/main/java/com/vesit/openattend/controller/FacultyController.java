package com.vesit.openattend.controller;

import com.vesit.openattend.dto.faculty.*;
import com.vesit.openattend.security.CallerRoles;
import com.vesit.openattend.service.faculty.FacultyAttendanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/faculty")
@RequiredArgsConstructor
public class FacultyController {

    private final FacultyAttendanceService facultyAttendanceService;

    @GetMapping("/my-subjects")
    public ResponseEntity<Map<String, Object>> getMySubjects(Authentication authentication) {
        String email = authentication != null ? authentication.getName() : "faculty@ves.ac.in";
        List<FacultySubjectDto> subjects = facultyAttendanceService.getMySubjects(email);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "subjects", subjects
        ));
    }

    @GetMapping("/roster")
    public ResponseEntity<Map<String, Object>> getRoster(
            @RequestParam(required = false) String subjectId,
            @RequestParam(required = false, defaultValue = "D12B") String division,
            @RequestParam(required = false) String batch
    ) {
        List<RosterStudentDto> roster = facultyAttendanceService.getRoster(subjectId, division, batch);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "count", roster.size(),
                "roster", roster
        ));
    }

    @PostMapping("/attendance/submit")
    public ResponseEntity<Map<String, Object>> submitAttendance(
            @Valid @RequestBody AttendanceSubmissionRequest request,
            Authentication authentication
    ) {
        String email = authentication != null ? authentication.getName() : "faculty@ves.ac.in";
        LectureSessionResponse session = facultyAttendanceService.submitAttendance(email, CallerRoles.of(authentication), request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Attendance recorded successfully",
                "session", session
        ));
    }

    @PutMapping("/attendance/{sessionId}/correct")
    public ResponseEntity<Map<String, Object>> correctAttendance(
            @PathVariable String sessionId,
            @Valid @RequestBody AttendanceCorrectionRequest request,
            Authentication authentication
    ) {
        String email = authentication != null ? authentication.getName() : "faculty@ves.ac.in";
        LectureSessionResponse session = facultyAttendanceService.correctAttendance(email, CallerRoles.of(authentication), sessionId, request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Attendance corrected successfully",
                "session", session
        ));
    }

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> getSessionHistory(
            @RequestParam(required = false) String subjectId,
            Authentication authentication
    ) {
        String email = authentication != null ? authentication.getName() : "faculty@ves.ac.in";
        List<LectureSessionResponse> sessions = facultyAttendanceService.getSessionHistory(email, subjectId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "sessions", sessions
        ));
    }
}
