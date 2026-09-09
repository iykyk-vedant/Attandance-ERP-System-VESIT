package com.vesit.openattend.dto.leave;

import com.vesit.openattend.entity.enums.LeaveType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * A student's OD / medical leave application, submitted as {@code multipart/form-data} so the
 * proof travels with the form in one request — the whole point being that nothing is carried
 * round campus on paper afterwards.
 */
@Data
public class LeaveSubmissionForm {

    @NotNull(message = "leaveType is required (OFFICIAL_DUTY or MEDICAL)")
    private LeaveType leaveType;

    @NotBlank(message = "eventName is required")
    @Size(max = 255, message = "eventName must be at most 255 characters")
    private String eventName;

    @Size(max = 2000, message = "reason must be at most 2000 characters")
    private String reason;

    @NotNull(message = "fromDate is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    /** Inclusive. Equal to {@link #fromDate} for a single-day leave. */
    @NotNull(message = "toDate is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;

    /** The event invite, selection letter or medical certificate. PDF or image, required. */
    @NotNull(message = "proof attachment is required")
    private MultipartFile proof;
}
