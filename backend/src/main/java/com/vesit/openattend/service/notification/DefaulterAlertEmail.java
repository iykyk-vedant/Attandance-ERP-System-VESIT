package com.vesit.openattend.service.notification;

import org.springframework.web.util.HtmlUtils;

import java.util.Comparator;
import java.util.List;

/**
 * The defaulter warning email: one message per student per roll call, with a row for each
 * percentage that dropped (the subject missed, and overall attendance when that fell too).
 *
 * <p>Inline styles and table layout only — institutional mail clients strip stylesheets.
 */
final class DefaulterAlertEmail {

    private static final String WARNING_ACCENT = "#b45309";
    private static final String CRITICAL_ACCENT = "#b91c1c";

    private DefaulterAlertEmail() {
    }

    /** Subject rows first, then aggregate — the lecture missed is what the student recognises. */
    static List<DefaulterAlert> ordered(List<DefaulterAlert> alerts) {
        return alerts.stream()
                .sorted(Comparator.comparing(DefaulterAlert::isAggregate))
                .toList();
    }

    static String subject(List<DefaulterAlert> alerts) {
        DefaulterAlert lead = lead(alerts);
        String label = lead.level() == DefaulterAlertLevel.CRITICAL ? "Critical attendance alert" : "Attendance warning";
        String scope = lead.isAggregate() ? "overall attendance" : lead.scopeSubjectCode();
        return "[OpenAttend] " + label + ": " + scope + " at " + lead.percentage().toPlainString() + "%";
    }

    static String html(List<DefaulterAlert> alerts) {
        DefaulterAlert lead = lead(alerts);
        boolean critical = lead.level() == DefaulterAlertLevel.CRITICAL;
        String accent = critical ? CRITICAL_ACCENT : WARNING_ACCENT;

        StringBuilder rows = new StringBuilder();
        for (DefaulterAlert alert : ordered(alerts)) {
            String rowAccent = alert.level() == DefaulterAlertLevel.CRITICAL ? CRITICAL_ACCENT : WARNING_ACCENT;
            rows.append("<tr>")
                    .append("<td style=\"padding:10px 8px;border-top:1px solid #e5e7eb;\">")
                    .append(esc(scopeLabel(alert)))
                    .append("<br><span style=\"color:#6b7280;font-size:12px;\">")
                    .append(alert.level() == DefaulterAlertLevel.CRITICAL ? "Critical &middot; below 65%" : "Warning &middot; below 75%")
                    .append("</span></td>")
                    .append("<td align=\"right\" style=\"padding:10px 8px;border-top:1px solid #e5e7eb;font-weight:bold;color:")
                    .append(rowAccent).append(";\">").append(alert.percentage().toPlainString()).append("%</td>")
                    .append("<td align=\"right\" style=\"padding:10px 8px;border-top:1px solid #e5e7eb;\">")
                    .append(alert.attended()).append(" / ").append(alert.conducted()).append("</td>")
                    .append("<td align=\"right\" style=\"padding:10px 8px;border-top:1px solid #e5e7eb;\"><strong>")
                    .append(alert.lecturesToRecover()).append("</strong> ")
                    .append(lectures(alert.lecturesToRecover())).append("</td>")
                    .append("</tr>");
        }

        return "<div style=\"margin:0;padding:24px 12px;background:#f4f5f7;font-family:Arial,Helvetica,sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" "
                + "style=\"max-width:560px;margin:0 auto;background:#ffffff;border-radius:8px;border-collapse:separate;\">"
                + "<tr><td style=\"background:" + accent + ";color:#ffffff;padding:16px 24px;font-size:18px;font-weight:bold;border-radius:8px 8px 0 0;\">"
                + (critical ? "Critical attendance alert" : "Attendance warning")
                + "</td></tr>"
                + "<tr><td style=\"padding:24px;color:#1f2933;font-size:14px;line-height:1.55;\">"
                + "<p style=\"margin:0 0 12px;\">Dear " + esc(lead.studentName()) + " (" + esc(lead.rollNo()) + "),</p>"
                + "<p style=\"margin:0 0 16px;\">You were marked absent for <strong>" + esc(lead.missedSubjectCode())
                + " &ndash; " + esc(lead.missedSubjectName()) + "</strong> on " + lead.lectureDate()
                + " (slot " + lead.sessionIndex() + "). Your attendance is now below "
                + (critical ? "<strong>65%</strong>" : "the <strong>75%</strong> requirement") + ".</p>"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"border-collapse:collapse;font-size:14px;\">"
                + "<tr style=\"background:#f4f5f7;color:#374151;font-size:12px;text-transform:uppercase;\">"
                + "<th align=\"left\" style=\"padding:8px;\">Attendance</th>"
                + "<th align=\"right\" style=\"padding:8px;\">Current</th>"
                + "<th align=\"right\" style=\"padding:8px;\">Attended</th>"
                + "<th align=\"right\" style=\"padding:8px;\">To recover</th></tr>"
                + rows
                + "</table>"
                + "<p style=\"margin:16px 0 0;\">&ldquo;To recover&rdquo; is how many lectures you need to attend "
                + "<strong>in a row, without missing any</strong>, to get back to 75%.</p>"
                + "<p style=\"margin:16px 0 0;color:#6b7280;font-size:12px;\">If a lecture was marked wrongly, speak to your "
                + "subject teacher &mdash; it can be corrected within 48 hours. You will get at most one warning a week per "
                + "subject; alerts below 65% are sent straight away.</p>"
                + "</td></tr>"
                + "<tr><td style=\"padding:12px 24px;border-top:1px solid #e5e7eb;color:#9ca3af;font-size:11px;\">"
                + "Automated message from OpenAttend, VESIT. Please do not reply."
                + "</td></tr>"
                + "</table></div>";
    }

    static String plainText(List<DefaulterAlert> alerts) {
        DefaulterAlert lead = lead(alerts);
        boolean critical = lead.level() == DefaulterAlertLevel.CRITICAL;

        StringBuilder text = new StringBuilder()
                .append(critical ? "CRITICAL ATTENDANCE ALERT" : "ATTENDANCE WARNING").append("\n\n")
                .append("Dear ").append(lead.studentName()).append(" (").append(lead.rollNo()).append("),\n\n")
                .append("You were marked absent for ").append(lead.missedSubjectCode()).append(" - ")
                .append(lead.missedSubjectName()).append(" on ").append(lead.lectureDate())
                .append(" (slot ").append(lead.sessionIndex()).append("). Your attendance is now below ")
                .append(critical ? "65%" : "the 75% requirement").append(".\n\n");

        for (DefaulterAlert alert : ordered(alerts)) {
            text.append("- ").append(scopeLabel(alert)).append(": ")
                    .append(alert.percentage().toPlainString()).append("% (")
                    .append(alert.attended()).append(" of ").append(alert.conducted()).append(" lectures). ")
                    .append("Attend the next ").append(alert.lecturesToRecover()).append(" ")
                    .append(lectures(alert.lecturesToRecover())).append(" in a row to get back to 75%.\n");
        }

        return text.append("\nIf a lecture was marked wrongly, speak to your subject teacher - it can be corrected ")
                .append("within 48 hours. You will get at most one warning a week per subject; alerts below 65% ")
                .append("are sent straight away.\n\n-- \nAutomated message from OpenAttend, VESIT. Please do not reply.\n")
                .toString();
    }

    /** The most severe alert, preferring the subject row: it drives the heading and the subject line. */
    private static DefaulterAlert lead(List<DefaulterAlert> alerts) {
        return ordered(alerts).stream()
                .filter(a -> a.level() == DefaulterAlertLevel.CRITICAL)
                .findFirst()
                .orElse(ordered(alerts).get(0));
    }

    private static String scopeLabel(DefaulterAlert alert) {
        return alert.isAggregate()
                ? "Overall (all subjects)"
                : alert.scopeSubjectCode() + " - " + alert.scopeSubjectName();
    }

    private static String lectures(int count) {
        return count == 1 ? "lecture" : "lectures";
    }

    private static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }
}
