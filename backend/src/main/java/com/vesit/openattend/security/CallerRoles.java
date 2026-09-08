package com.vesit.openattend.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.stream.Collectors;

/** Reads the caller granted authorities (e.g. {@code ROLE_FACULTY}) off the security context. */
public final class CallerRoles {

    private CallerRoles() {
    }

    /** Authorities of the caller, empty when unauthenticated. */
    public static Set<String> of(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
