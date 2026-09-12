package com.vesit.openattend.service.notification;

import com.vesit.openattend.config.AsyncConfig;
import com.vesit.openattend.entity.enums.NotificationEmailStatus;
import com.vesit.openattend.repository.NotificationRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Emails defaulter alerts to the student's institutional address.
 *
 * <p>Runs after the attendance transaction commits, on {@link AsyncConfig#NOTIFICATION_EXECUTOR}:
 * the faculty's submit returns without waiting on SMTP, and the email normally leaves within a
 * second or two. SMTP connect/read timeouts are capped in {@code application.yml} so a hung mail
 * server cannot hold the pool.
 *
 * <p>Every alert ends in a recorded {@link NotificationEmailStatus}; a failed email never removes
 * the in-app notification.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaulterAlertMailer {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final NotificationRepository notificationRepository;

    @Value("${openattend.notifications.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${openattend.notifications.email.from:OpenAttend <no-reply@ves.ac.in>}")
    private String fromAddress;

    @Value("${openattend.allowed-email-domain:ves.ac.in}")
    private String allowedEmailDomains;

    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDefaulterAlertsRaised(DefaulterAlertRaisedEvent event) {
        Map<String, List<DefaulterAlert>> byRecipient = new LinkedHashMap<>();
        for (DefaulterAlert alert : event.alerts()) {
            String recipient = alert.studentEmail() != null ? alert.studentEmail().trim() : "";
            byRecipient.computeIfAbsent(recipient, key -> new ArrayList<>()).add(alert);
        }
        byRecipient.forEach(this::deliver);
    }

    /** One email per student, however many of their percentages dropped on this roll call. */
    private void deliver(String recipient, List<DefaulterAlert> alerts) {
        NotificationEmailStatus outcome = send(recipient, alerts);
        List<String> ids = alerts.stream().map(DefaulterAlert::notificationId).toList();
        try {
            notificationRepository.markEmailOutcome(ids, outcome,
                    outcome == NotificationEmailStatus.SENT ? LocalDateTime.now() : null);
        } catch (RuntimeException e) {
            log.error("Could not record email outcome {} for notifications {}: {}", outcome, ids, e.getMessage());
        }
    }

    private NotificationEmailStatus send(String recipient, List<DefaulterAlert> alerts) {
        if (!emailEnabled) {
            log.debug("Notification email disabled; {} alert(s) for {} kept in-app only", alerts.size(), recipient);
            return NotificationEmailStatus.SKIPPED;
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            log.warn("Notification email enabled but no mail server is configured (spring.mail.host); alert for {} kept in-app only", recipient);
            return NotificationEmailStatus.SKIPPED;
        }

        if (!isInstitutional(recipient)) {
            log.warn("Defaulter alert not emailed: '{}' is not an institutional address ({})", recipient, allowedEmailDomains);
            return NotificationEmailStatus.SKIPPED;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(recipient);
            helper.setSubject(DefaulterAlertEmail.subject(alerts));
            helper.setText(DefaulterAlertEmail.plainText(alerts), DefaulterAlertEmail.html(alerts));
            mailSender.send(message);

            log.info("Defaulter alert emailed to {} ({} alert(s))", recipient, alerts.size());
            return NotificationEmailStatus.SENT;
        } catch (MessagingException | MailException e) {
            log.error("Defaulter alert email to {} failed: {}", recipient, e.getMessage());
            return NotificationEmailStatus.FAILED;
        } catch (RuntimeException e) {
            log.error("Defaulter alert email to {} failed unexpectedly", recipient, e);
            return NotificationEmailStatus.FAILED;
        }
    }

    private boolean isInstitutional(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String normalised = email.toLowerCase(Locale.ROOT);
        return Arrays.stream(allowedEmailDomains.split(","))
                .map(domain -> domain.trim().toLowerCase(Locale.ROOT))
                .filter(domain -> !domain.isEmpty())
                .anyMatch(domain -> normalised.endsWith("@" + domain));
    }
}
