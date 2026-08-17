package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ComplaintAdminResponse {
    private Long complaintId;
    private String complaintNo;
    private String title;
    private String citizenName;
    private String citizenMobile;
    private String departmentName;
    private String categoryName;
    private String officerName;
    private String statusCode;
    private String priorityCode;
    /** Geo-coordinates for the admin map view (null when citizen didn't share location). */
    private BigDecimal latitude;
    private BigDecimal longitude;
    private LocalDateTime createdAt;
    private LocalDateTime assignedAt;
    private LocalDateTime resolvedAt;
}
