package com.SIH.mark1.controller;

import com.SIH.mark1.dto.response.NotificationResponse;
import com.SIH.mark1.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for user notifications.
 * Available to all authenticated roles (CITIZEN, OFFICER, ADMIN).
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** Returns all notifications (newest first) for the logged-in user. */
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getAll() {
        return ResponseEntity.ok(notificationService.getAllNotifications(currentUsername()));
    }

    /** Returns unread notifications + count for the logged-in user. */
    @GetMapping("/unread")
    public ResponseEntity<Map<String, Object>> getUnread() {
        return ResponseEntity.ok(notificationService.getUnreadSummary(currentUsername()));
    }

    /** Marks a single notification as read. */
    @PutMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable("id") Long notificationId) {
        return ResponseEntity.ok(notificationService.markRead(currentUsername(), notificationId));
    }

    /** Marks all notifications as read. */
    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        notificationService.markAllRead(currentUsername());
        return ResponseEntity.noContent().build();
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}
