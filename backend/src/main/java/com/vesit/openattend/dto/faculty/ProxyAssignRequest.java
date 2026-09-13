package com.vesit.openattend.dto.faculty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxyAssignRequest {

    @NotBlank(message = "Subject ID is required")
    private String subjectId;

    @NotBlank(message = "Division is required")
    private String division;

    private String batch;

    @NotNull(message = "Lecture date is required")
    private LocalDate lectureDate;

    /** Optional: narrows the delegation to one timetable slot instead of the whole day. */
    private Integer sessionIndex;

    @NotBlank(message = "Proxy faculty email is required")
    private String proxyFacultyEmail;

    @NotBlank(message = "Reason is mandatory")
    @Size(min = 10, message = "Reason must be at least 10 characters explaining the delegation")
    private String reason;
}
