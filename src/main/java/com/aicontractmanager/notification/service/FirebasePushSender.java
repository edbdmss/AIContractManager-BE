package com.aicontractmanager.notification.service;

import com.aicontractmanager.notification.domain.PushNotification;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="app.push.fcm", name="enabled", havingValue="true")
@Component
public class FirebasePushSender {
    private final FirebaseMessaging messaging;

    public FirebasePushSender(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    public PushDeliveryResult send(PushNotification notification, List<String> tokens) {
        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(notification.title()).setBody(notification.body()).build())
                .setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())
                .putData("notificationId", notification.id().toString())
                .build();
        try {
            BatchResponse response = messaging.sendEachForMulticast(message);
            List<String> invalidTokens = new ArrayList<>();
            List<SendResponse> responses = response.getResponses();
            for (int i = 0; i < responses.size(); i++) {
                SendResponse item = responses.get(i);
                if (!item.isSuccessful() && item.getException() != null
                        && item.getException().getMessagingErrorCode() != null
                        && item.getException().getMessagingErrorCode().name().equals("UNREGISTERED")) {
                    invalidTokens.add(tokens.get(i));
                }
            }
            return new PushDeliveryResult(response.getSuccessCount(),
                    response.getFailureCount() == 0 ? null : response.getFailureCount() + "개 기기 전송 실패",
                    invalidTokens);
        } catch (FirebaseMessagingException e) {
            throw new IllegalStateException("FCM 메시지 전송에 실패했습니다: " + e.getMessage(), e);
        }
    }
}
