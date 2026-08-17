package com.SIH.mark1.dto.request;

import lombok.Data;
import java.math.BigDecimal;

/**
 * Payload for updating a complaint.
 * Contains the same fields as CreateComplaintRequest, all optional.
 */
@Data
public class UpdateComplaintRequest {

    private String title;
    private String description;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String address;
    private String language;
}
