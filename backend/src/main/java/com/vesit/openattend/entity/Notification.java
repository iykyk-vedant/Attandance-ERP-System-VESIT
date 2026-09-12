package com.vesit.openattend.entity;

import com.vesit.openattend.entity.enums.NotificationEmailStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "notifications",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "notification_dedup_key",
            columnNames = {"student_id", "subject_id", "type", "sync_log_id"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "sync_log_id", length = 36)
    private String syncLogId;

    /** Delivery of the email copy; null for notification types that are in-app only. */
    @Enumerated(EnumType.STRING)
    @Column(name = "email_status", length = 16)
    private NotificationEmailStatus emailStatus;

    @Column(name = "emailed_at")
    private LocalDateTime emailedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
