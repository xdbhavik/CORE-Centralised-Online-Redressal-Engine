package com.SIH.mark1.controller;

import com.SIH.mark1.dto.request.FeedbackRequest;
import com.SIH.mark1.dto.response.FeedbackResponse;
import com.SIH.mark1.service.FeedbackService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller for complaint feedback.
 * - Citizens can submit feedback on their RESOLVED/CLOSED complaints.
 * - Admins can view aggregate feedback summary.
 */
@RestController
@RequestMapping("/api/v1")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /** Citizen submits feedback for a resolved complaint. */
    @PostMapping("/complaints/{id}/feedback")
    @PreAuthorize("hasRole('CITIZEN')")
    public ResponseEntity<FeedbackResponse> submitFeedback(
            @PathVariable("id") Long complaintId,
            @Valid @RequestBody FeedbackRequest request) {
        String username = currentUsername();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(feedbackService.submitFeedback(username, complaintId, request));
    }

    /** Get feedback for a specific complaint. */
    @GetMapping("/complaints/{id}/feedback")
    @PreAuthorize("hasAnyRole('CITIZEN', 'ADMIN', 'OFFICER')")
    public ResponseEntity<FeedbackResponse> getFeedback(@PathVariable("id") Long complaintId) {
        return ResponseEntity.ok(feedbackService.getFeedback(complaintId));
    }

    /** Admin views aggregate feedback analytics summary. */
    @GetMapping("/admin/feedback/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getAdminSummary() {
        return ResponseEntity.ok(feedbackService.getAdminSummary());
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}
