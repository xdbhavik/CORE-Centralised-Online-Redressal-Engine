package com.SIH.mark1.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class OfficerNoteResponse {
    private Long noteId;
    private String note;
    private String officerName;
    private LocalDateTime createdAt;
}
