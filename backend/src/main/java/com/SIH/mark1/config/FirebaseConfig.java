package com.SIH.mark1.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.service-account.path:classpath:firebase-service-account.json}")
    private String serviceAccountPath;

    @Value("${firebase.enabled:false}")
    private boolean firebaseEnabled;

    @Bean
    public FirebaseAuth firebaseAuth() throws IOException {
        if (!firebaseEnabled) {
            log.warn("Firebase is DISABLED (firebase.enabled=false). Firebase OTP registration will not work.");
            return null;
        }

        Resource resource = new DefaultResourceLoader().getResource(serviceAccountPath);
        if (!resource.exists()) {
            log.warn("Firebase service account file not found at '{}'. " +
                    "Firebase ID token verification is DISABLED. " +
                    "Download it from Firebase Console > Project Settings > Service Accounts.", serviceAccountPath);
            return null;
        }

        try (InputStream serviceAccount = resource.getInputStream()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
        }

        log.info("Firebase Admin SDK initialized successfully");
        return FirebaseAuth.getInstance();
    }
}
