package com.vesit.openattend.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSessionDto {
    private String id;
    private String email;
    private String name;
    private String role;
    private String rollNo;
    private String division;
    private String batch;
    private String accessToken;
}
