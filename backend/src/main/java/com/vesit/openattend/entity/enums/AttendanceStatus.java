package com.vesit.openattend.entity.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * How one lecture counts towards a student's attendance percentage.
 *
 * <p>The percentage is {@code (PRESENT + DUTY_PRESENT) / (PRESENT + ABSENT + DUTY_PRESENT)}. An
 * approved Official Duty or medical leave keeps the lecture in the denominator and credits it in
 * the numerator, so a student representing the college is neither penalised for missing the
 * lecture nor handed a shorter denominator than the classmates who sat through it.
 *
 * <p>{@link #NA} is the only status that is not accountable at all — it is how a student who was
 * never on the roster for a lecture is kept out of their own denominator.
 *
 * <p>This enum is the single definition of that counting rule: the defaulter query, the student
 * dashboard and the leave approver all read {@link #attended()} / {@link #conducted()} rather
 * than restating which statuses count.
 */
public enum AttendanceStatus {
    PRESENT,
    ABSENT,
    /** Credited by an approved OD / medical leave rather than by physical presence. */
    DUTY_PRESENT,
    NA;

    private static final Set<AttendanceStatus> ATTENDED = EnumSet.of(PRESENT, DUTY_PRESENT);
    private static final Set<AttendanceStatus> CONDUCTED = EnumSet.of(PRESENT, ABSENT, DUTY_PRESENT);

    /** Statuses that count towards the numerator. */
    public static Set<AttendanceStatus> attended() {
        return ATTENDED;
    }

    /** Statuses that count towards the denominator — every lecture the student was accountable for. */
    public static Set<AttendanceStatus> conducted() {
        return CONDUCTED;
    }

    public boolean isAttended() {
        return ATTENDED.contains(this);
    }

    public boolean isConducted() {
        return CONDUCTED.contains(this);
    }
}
