package com.vesit.openattend.entity.enums;

/**
 * Where the email copy of an in-app notification got to. Null on notification types that are
 * never emailed.
 */
public enum NotificationEmailStatus {
    /** Stored with the attendance; the email is queued to go out once the submission commits. */
    PENDING,
    SENT,
    /** The SMTP server refused or could not be reached; the in-app notification still stands. */
    FAILED,
    /** Email is disabled, no mail server is configured, or the address is not institutional. */
    SKIPPED
}
