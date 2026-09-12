package com.vesit.openattend.service.notification;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One stored defaulter alert, carried to the mailer as plain values so the email can be built on
 * another thread without reopening the submission's persistence context.
 *
 * @param notificationId    the in-app notification this email copies
 * @param scopeSubjectCode  the subject whose percentage dropped, or null for aggregate attendance
 * @param lecturesToRecover consecutive lectures needed to climb back to 75%
 * @param missedSubjectCode the lecture the student was marked absent from, which caused the drop
 */
public record DefaulterAlert(
        String notificationId,
        String studentEmail,
        String studentName,
        String rollNo,
        DefaulterAlertLevel level,
        String scopeSubjectCode,
        String scopeSubjectName,
        BigDecimal percentage,
        int attended,
        int conducted,
        int lecturesToRecover,
        String missedSubjectCode,
        String missedSubjectName,
        LocalDate lectureDate,
        int sessionIndex
) {

    public boolean isAggregate() {
        return scopeSubjectCode == null;
    }
}
