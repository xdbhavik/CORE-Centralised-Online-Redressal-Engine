package com.SIH.mark1.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OfficerActionRequest {
    @Size(max = 1000)
    private String remarks;
}
