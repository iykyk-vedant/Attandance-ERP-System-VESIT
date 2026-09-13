package com.vesit.openattend.service.notification;

import com.vesit.openattend.service.attendance.AttendancePercentage;

import java.math.BigDecimal;

/**
 * The two boundaries a defaulter alert fires on, and the notification type each one is stored as.
 */
public enum DefaulterAlertLevel {

    /** Dropped below the institutional 75% pass mark. */
    WARNING("threshold_breach", AttendancePercentage.DEFAULT_THRESHOLD),

    /** Dropped below 65%. Bypasses the warning cooldown, but is itself sent at most once a week. */
    CRITICAL("threshold_critical", new BigDecimal("65.00"));

    private final String notificationType;
    private final BigDecimal boundary;

    DefaulterAlertLevel(String notificationType, BigDecimal boundary) {
        this.notificationType = notificationType;
        this.boundary = boundary;
    }

    public String notificationType() {
        return notificationType;
    }

    public BigDecimal boundary() {
        return boundary;
    }

    /**
     * The most severe boundary crossed on the way from {@code before} to {@code after}, or null
     * when no boundary was crossed downwards. A student already below 75% who slips further is not
     * warned again unless the slip also takes them under 65%.
     */
    public static DefaulterAlertLevel crossed(BigDecimal before, BigDecimal after) {
        if (crossedDownwards(before, after, CRITICAL.boundary)) {
            return CRITICAL;
        }
        if (crossedDownwards(before, after, WARNING.boundary)) {
            return WARNING;
        }
        return null;
    }

    private static boolean crossedDownwards(BigDecimal before, BigDecimal after, BigDecimal boundary) {
        return !AttendancePercentage.isDefaulter(before, boundary) && AttendancePercentage.isDefaulter(after, boundary);
    }
}
