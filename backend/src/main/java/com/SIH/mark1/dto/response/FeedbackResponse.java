package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class FeedbackResponse {
    private Long feedbackId;
    private Long complaintId;
    private String complaintNo;
    private Integer rating;
    private String comments;
    private String citizenName;
    private LocalDateTime submittedAt;
}
