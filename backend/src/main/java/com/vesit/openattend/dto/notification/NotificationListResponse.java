package com.vesit.openattend.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationListResponse {
    private boolean success;
    private List<NotificationDto> notifications;
    private long unreadCount;
}
