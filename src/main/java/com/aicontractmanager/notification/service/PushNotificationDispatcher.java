package com.aicontractmanager.notification.service;

import com.aicontractmanager.notification.domain.PushNotification;
import com.aicontractmanager.notification.repository.DeviceTokenRepository;
import com.aicontractmanager.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.push.fcm", name = "enabled", havingValue = "true")
public class PushNotificationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(PushNotificationDispatcher.class);
    private final NotificationRepository notifications;
    private final DeviceTokenRepository devices;
    private final FirebasePushSender sender;
    private final int batchSize;

    public PushNotificationDispatcher(NotificationRepository notifications, DeviceTokenRepository devices,
                                       FirebasePushSender sender,
                                       @Value("${app.push.fcm.batch-size:100}") int batchSize) {
        this.notifications = notifications;
        this.devices = devices;
        this.sender = sender;
        this.batchSize = Math.max(1, Math.min(batchSize, 500));
    }

    @Scheduled(fixedDelayString = "${app.push.fcm.poll-interval-ms:30000}")
    public void dispatchDueNotifications() {
        for (PushNotification notification : notifications.claimDue(batchSize)) {
            List<String> tokens = devices.findActiveTokens(notification.userId());
            if (tokens.isEmpty()) {
                notifications.releaseWithoutDevices(notification.id());
                continue;
            }
            try {
                PushDeliveryResult result = sender.send(notification, tokens);
                result.invalidTokens().forEach(devices::deactivateToken);
                if (result.hasSuccess()) {
                    notifications.markPushSent(notification.id());
                } else {
                    notifications.markPushFailed(notification.id(), result.error(), notification.attempts() + 1);
                    log.warn("FCM 전송 실패: notificationId={}, reason={}", notification.id(), result.error());
                }
            } catch (Exception e) {
                notifications.markPushFailed(notification.id(), e.getMessage(), notification.attempts() + 1);
                log.warn("FCM 발송 처리 중 오류: notificationId={}", notification.id(), e);
            }
        }
    }
}
