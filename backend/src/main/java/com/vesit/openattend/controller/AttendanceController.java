package com.vesit.openattend.controller;

import com.vesit.openattend.dto.attendance.AttendanceAnalyticsResponse;
import com.vesit.openattend.dto.attendance.AttendanceHistoryResponse;
import com.vesit.openattend.dto.attendance.OverallAttendanceResponse;
import com.vesit.openattend.dto.attendance.SubjectAttendanceResponse;
import com.vesit.openattend.security.JwtTokenProvider;
import com.vesit.openattend.service.attendance.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/overall")
    public ResponseEntity<OverallAttendanceResponse> getOverallAttendance(
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        String rollNo = extractRollNo(authHeader);
        OverallAttendanceResponse response = attendanceService.getOverallAttendance(rollNo);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/subjects")
    public ResponseEntity<SubjectAttendanceResponse> getSubjectsAttendance(
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        String rollNo = extractRollNo(authHeader);
        SubjectAttendanceResponse response = attendanceService.getSubjectsAttendance(rollNo);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    public ResponseEntity<AttendanceHistoryResponse> getAttendanceHistory(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String status
    ) {
        String rollNo = extractRollNo(authHeader);
        AttendanceHistoryResponse response = attendanceService.getAttendanceHistory(rollNo, subject, status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/analytics")
    public ResponseEntity<AttendanceAnalyticsResponse> getAttendanceAnalytics(
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        String rollNo = extractRollNo(authHeader);
        AttendanceAnalyticsResponse response = attendanceService.getAttendanceAnalytics(rollNo);
        return ResponseEntity.ok(response);
    }

    private String extractRollNo(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            if (jwtTokenProvider.validateToken(token)) {
                return jwtTokenProvider.getRollNo(token);
            }
        }
        return "2024CS01";
    }
}
