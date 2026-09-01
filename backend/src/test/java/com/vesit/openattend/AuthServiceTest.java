package com.vesit.openattend;

import com.vesit.openattend.dto.auth.LoginRequest;
import com.vesit.openattend.dto.auth.LoginResponse;
import com.vesit.openattend.service.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Test
    void testValidStudentLogin() {
        LoginRequest request = new LoginRequest("student@ves.ac.in", "password123");
        LoginResponse response = authService.login(request);

        assertTrue(response.isSuccess());
        assertNotNull(response.getSession());
        assertEquals("student@ves.ac.in", response.getSession().getEmail());
        assertEquals("STUDENT", response.getSession().getRole());
        assertEquals("2024CS01", response.getSession().getRollNo());
        assertNotNull(response.getSession().getAccessToken());
    }

    @Test
    void testInvalidEmailDomainRejected() {
        LoginRequest request = new LoginRequest("student@gmail.com", "password123");
        LoginResponse response = authService.login(request);

        assertFalse(response.isSuccess());
        assertNull(response.getSession());
        assertTrue(response.getError().contains("Access restricted"));
    }

    @Test
    void testInvalidPasswordRejected() {
        LoginRequest request = new LoginRequest("student@ves.ac.in", "wrongpassword");
        LoginResponse response = authService.login(request);

        assertFalse(response.isSuccess());
        assertNull(response.getSession());
        assertTrue(response.getError().contains("Invalid credentials"));
    }
}
