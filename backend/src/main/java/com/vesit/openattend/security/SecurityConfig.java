package com.vesit.openattend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/assets/**",
                                "/favicon.ico",
                                "/sw.js",
                                "/manifest.json",
                                "/api/v1/auth/**",
                                "/api/v1/health/**",
                                "/actuator/**"
                        ).permitAll()
                        .requestMatchers("/api/v1/faculty/**").hasAnyRole("FACULTY", "CLASS_TEACHER", "ADMIN", "SUPER_ADMIN")
                        // Leave requests carry medical certificates and event letters, and the
                        // proof endpoint streams them back — never anonymous.
                        .requestMatchers("/api/v1/leave/**").authenticated()
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        // Defaulter lists carry the whole cohort's names, roll numbers and
                        // percentages — restricted to the roles that publish them.
                        .requestMatchers("/api/v1/reports/**").hasAnyRole("CLASS_TEACHER", "ADMIN", "SUPER_ADMIN")
                        .anyRequest().permitAll() // Allow student endpoints to resolve user from Bearer token
                )
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(contentType -> {})
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
