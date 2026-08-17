package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class NotificationResponse {
    private Long notificationId;
    private String title;
    private String message;
    private String type;
    private Long complaintId;
    private String complaintNo;
    private boolean read;
    private LocalDateTime createdAt;
}
