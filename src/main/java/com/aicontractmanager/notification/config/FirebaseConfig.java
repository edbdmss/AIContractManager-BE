package com.aicontractmanager.notification.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Configuration
@ConditionalOnProperty(prefix = "app.push.fcm", name = "enabled", havingValue = "true")
public class FirebaseConfig {
    @Bean
    FirebaseApp firebaseApp(@Value("${app.push.fcm.credentials-file:}") String credentialsFile,
                            @Value("${app.push.fcm.project-id:}") String projectId) throws IOException {
        List<FirebaseApp> existingApps = FirebaseApp.getApps();
        if (!existingApps.isEmpty()) return existingApps.get(0);

        GoogleCredentials credentials;
        if (credentialsFile != null && !credentialsFile.isBlank()) {
            Path path = Path.of(credentialsFile);
            if (!Files.isReadable(path)) {
                throw new IllegalStateException("FCM 서비스 계정 파일을 읽을 수 없습니다: " + path);
            }
            try (FileInputStream input = new FileInputStream(path.toFile())) {
                credentials = GoogleCredentials.fromStream(input);
            }
        } else {
            credentials = GoogleCredentials.getApplicationDefault();
        }

        FirebaseOptions.Builder options = FirebaseOptions.builder().setCredentials(credentials);
        if (projectId != null && !projectId.isBlank()) options.setProjectId(projectId);
        return FirebaseApp.initializeApp(options.build());
    }

    @Bean
    FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
