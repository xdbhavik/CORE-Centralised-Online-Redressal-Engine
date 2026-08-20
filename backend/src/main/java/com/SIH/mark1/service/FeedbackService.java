package com.SIH.mark1.service;

import com.SIH.mark1.dto.request.FeedbackRequest;
import com.SIH.mark1.dto.response.FeedbackResponse;
import com.SIH.mark1.model.Complaint;
import com.SIH.mark1.model.Feedback;
import com.SIH.mark1.model.User;
import com.SIH.mark1.repository.ComplaintRepository;
import com.SIH.mark1.repository.FeedbackRepository;
import com.SIH.mark1.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Service for citizen feedback on resolved complaints.
 * Business rules:
 *  - Only the complaint owner (citizen) can submit feedback.
 *  - Feedback can only be submitted after complaint is RESOLVED or CLOSED.
 *  - Each complaint can have at most one feedback entry.
 */
@Service
public class FeedbackService {

    private static final java.util.Set<String> FEEDBACK_ALLOWED_STATUSES =
            java.util.Set.of("RESOLVED", "CLOSED");

    private final FeedbackRepository feedbackRepository;
    private final ComplaintRepository complaintRepository;
    private final UserRepository userRepository;

    public FeedbackService(FeedbackRepository feedbackRepository,
                           ComplaintRepository complaintRepository,
                           UserRepository userRepository) {
        this.feedbackRepository = feedbackRepository;
        this.complaintRepository = complaintRepository;
        this.userRepository = userRepository;
    }

    // ──────────────────────────────────────────
    // Citizen Endpoints
    // ──────────────────────────────────────────

    /**
     * Submits citizen feedback for a resolved/closed complaint.
     * Only one feedback per complaint is allowed.
     */
    @Transactional
    public FeedbackResponse submitFeedback(String username, Long complaintId, FeedbackRequest req) {
        User citizen = findUser(username);
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found"));

        // Ownership check
        if (!complaint.getCitizen().getUserId().equals(citizen.getUserId())) {
            throw new AccessDeniedException("This is not your complaint");
        }

        // Status check
        String statusCode = complaint.getCurrentStatus() != null
                ? complaint.getCurrentStatus().getStatusCode() : "";
        if (!FEEDBACK_ALLOWED_STATUSES.contains(statusCode)) {
            throw new IllegalStateException(
                    "Feedback can only be submitted after complaint is resolved or closed. Current status: " + statusCode);
        }

        // Duplicate check
        if (feedbackRepository.findByComplaint(complaint).isPresent()) {
            throw new IllegalStateException("Feedback already submitted for this complaint");
        }

        Feedback feedback = Feedback.builder()
                .complaint(complaint)
                .user(citizen)
                .rating(req.getRating())
                .comments(req.getComments())
                .build();
        feedback.setCreatedBy(citizen.getUserId());
        return toResponse(feedbackRepository.save(feedback));
    }

    /**
     * Returns feedback for a specific complaint (citizen or admin can view).
     */
    @Transactional(readOnly = true)
    public FeedbackResponse getFeedback(Long complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found"));
        return feedbackRepository.findByComplaint(complaint)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("No feedback found for this complaint"));
    }

    // ──────────────────────────────────────────
    // Admin Endpoints
    // ──────────────────────────────────────────

    /**
     * Returns an aggregated summary of all feedback for admin analytics.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAdminSummary() {
        List<Feedback> all = feedbackRepository.findAll();
        long total = all.size();
        double avgRating = all.stream()
                .mapToInt(Feedback::getRating)
                .average()
                .orElse(0.0);

        // Count per rating (1-5)
        Map<Integer, Long> ratingDistribution = new java.util.HashMap<>();
        for (int i = 1; i <= 5; i++) {
            final int rating = i;
            ratingDistribution.put(rating, all.stream().filter(f -> f.getRating() == rating).count());
        }

        return Map.of(
                "totalFeedbacks", total,
                "averageRating", Math.round(avgRating * 10.0) / 10.0,
                "ratingDistribution", ratingDistribution
        );
    }

    // ──────────────────────────────────────────
    // Private Helpers
    // ──────────────────────────────────────────

    private User findUser(String username) {
        return userRepository.findByMobile(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    private FeedbackResponse toResponse(Feedback f) {
        return FeedbackResponse.builder()
                .feedbackId(f.getFeedbackId())
                .complaintId(f.getComplaint().getComplaintId())
                .complaintNo(f.getComplaint().getComplaintNo())
                .rating(f.getRating())
                .comments(f.getComments())
                .citizenName(f.getUser().getName())
                .submittedAt(f.getCreatedAt())
                .build();
    }
}
