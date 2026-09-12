package com.vesit.openattend.service.notification;

import java.util.List;

/**
 * Published inside the attendance transaction; delivered to the mailer only once it commits, so a
 * submission that rolls back never emails anyone.
 */
public record DefaulterAlertRaisedEvent(List<DefaulterAlert> alerts) {
}
