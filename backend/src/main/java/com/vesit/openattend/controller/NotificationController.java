package com.vesit.openattend.controller;

import com.vesit.openattend.dto.notification.NotificationListResponse;
import com.vesit.openattend.security.JwtTokenProvider;
import com.vesit.openattend.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping
    public ResponseEntity<NotificationListResponse> getNotifications(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "false") boolean unreadOnly
    ) {
        String rollNo = extractRollNo(authHeader);
        NotificationListResponse response = notificationService.getNotifications(rollNo, unreadOnly);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable String id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Notification marked as read"
        ));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllRead(
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        String rollNo = extractRollNo(authHeader);
        notificationService.markAllAsRead(rollNo);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "All notifications marked as read"
        ));
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
