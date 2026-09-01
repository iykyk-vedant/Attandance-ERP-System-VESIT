package com.vesit.openattend.controller;

import com.vesit.openattend.dto.auth.LoginRequest;
import com.vesit.openattend.dto.auth.LoginResponse;
import com.vesit.openattend.dto.auth.UserSessionDto;
import com.vesit.openattend.security.JwtTokenProvider;
import com.vesit.openattend.service.auth.AuthService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        if (!response.isSuccess()) {
            if (response.getError() != null && response.getError().contains("Access restricted")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "error", "Unauthorized or expired session"
            ));
        }

        String token = authHeader.substring(7).trim();
        if (!jwtTokenProvider.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "error", "Unauthorized or expired session"
            ));
        }

        Claims claims = jwtTokenProvider.getClaims(token);
        UserSessionDto session = UserSessionDto.builder()
                .id(claims.get("userId", String.class))
                .email(claims.getSubject())
                .name(claims.get("name", String.class))
                .role(claims.get("role", String.class))
                .rollNo(claims.get("rollNo", String.class))
                .accessToken(token)
                .build();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "user", session
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Logged out successfully"
        ));
    }
}
