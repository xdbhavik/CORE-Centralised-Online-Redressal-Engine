package com.SIH.mark1.service;

import com.SIH.mark1.dto.response.NotificationResponse;
import com.SIH.mark1.ivr.service.SmsService;
import com.SIH.mark1.model.Complaint;
import com.SIH.mark1.model.Notification;
import com.SIH.mark1.model.NotificationType;
import com.SIH.mark1.model.User;
import com.SIH.mark1.repository.NotificationRepository;
import com.SIH.mark1.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for creating, retrieving, and managing notifications.
 * Notifications are created by service-layer events (complaint registered,
 * officer assigned, status updated, resolved, etc.) and consumed by users
 * via the NotificationController.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /**
     * Types that are also pushed over SMS.
     *
     * <p>Deliberately narrow. Verification events are the one case where the recipient may
     * never open the app — the call-back exists precisely because we could not reach them
     * there — so an in-app-only notice would be invisible to the people it is meant for.
     * Routine status updates stay in-app to avoid spamming citizens and burning SMS credit.</p>
     */
    private static final Set<NotificationType> SMS_TYPES = EnumSet.of(
            NotificationType.VERIFICATION_PENDING,
            NotificationType.VERIFICATION_REJECTED,
            NotificationType.VERIFICATION_UNREACHABLE);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SmsService smsService;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               SmsService smsService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.smsService = smsService;
    }

    // ──────────────────────────────────────────
    // Create
    // ──────────────────────────────────────────

    /**
     * Creates and saves a notification for a user.
     *
     * @param user      recipient
     * @param complaint associated complaint (may be null)
     * @param type      notification type
     * @param title     short notification title
     * @param message   full notification message body
     */
    public void sendNotification(User user, Complaint complaint,
                                 NotificationType type, String title, String message) {
        try {
            Notification notification = Notification.builder()
                    .user(user)
                    .complaint(complaint)
                    .type(type)
                    .title(title)
                    .message(message)
                    .read(false)
                    .build();
            notification.setCreatedBy(user.getUserId());
            notificationRepository.save(notification);
            log.debug("Notification sent to user={} type={} complaint={}",
                    user.getUserId(), type, complaint != null ? complaint.getComplaintNo() : "N/A");

            fanOutToSms(user, type, message);
        } catch (Exception ex) {
            // Notification failures should never break main business logic
            log.error("Failed to create notification for user={}: {}", user.getUserId(), ex.getMessage());
        }
    }

    /**
     * Mirrors selected notifications to SMS.
     *
     * <p>Runs after the in-app row is persisted so a telephony problem can never cost the
     * user their notification history. {@code SmsService} already swallows its own failures;
     * the extra guard here protects against anything unexpected, because a courtesy SMS must
     * never roll back the grievance action that triggered it.</p>
     */
    private void fanOutToSms(User user, NotificationType type, String message) {
        if (!SMS_TYPES.contains(type)) {
            return;
        }
        try {
            smsService.send(user.getMobile(), message);
        } catch (Exception ex) {
            log.warn("SMS fan-out failed for user={} type={}: {}", user.getUserId(), type, ex.getMessage());
        }
    }

    // ──────────────────────────────────────────
    // Retrieve
    // ──────────────────────────────────────────

    /**
     * Returns all notifications for the authenticated user, newest first.
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getAllNotifications(String username) {
        User user = findUser(username);
        return notificationRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns unread notifications + unread count for the authenticated user.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getUnreadSummary(String username) {
        User user = findUser(username);
        List<NotificationResponse> unread = notificationRepository
                .findByUserAndReadFalseOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .toList();
        return Map.of("unreadCount", unread.size(), "notifications", unread);
    }

    // ──────────────────────────────────────────
    // Mark Read
    // ──────────────────────────────────────────

    /**
     * Marks a single notification as read (only if it belongs to the requesting user).
     */
    @Transactional
    public NotificationResponse markRead(String username, Long notificationId) {
        User user = findUser(username);
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        if (!notification.getUser().getUserId().equals(user.getUserId())) {
            throw new org.springframework.security.access.AccessDeniedException("Not your notification");
        }
        notification.setRead(true);
        return toResponse(notificationRepository.save(notification));
    }

    /**
     * Marks ALL unread notifications of the user as read.
     */
    @Transactional
    public void markAllRead(String username) {
        User user = findUser(username);
        notificationRepository.markAllReadByUser(user);
    }

    // ──────────────────────────────────────────
    // Private Helpers
    // ──────────────────────────────────────────

    private User findUser(String username) {
        return userRepository.findByMobile(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .notificationId(n.getNotificationId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType() != null ? n.getType().name() : null)
                .complaintId(n.getComplaint() != null ? n.getComplaint().getComplaintId() : null)
                .complaintNo(n.getComplaint() != null ? n.getComplaint().getComplaintNo() : null)
                .read(Boolean.TRUE.equals(n.getRead()))
                .createdAt(n.getCreatedAt())
                .build();
    }
}
