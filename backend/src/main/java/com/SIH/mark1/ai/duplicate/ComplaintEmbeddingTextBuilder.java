package com.SIH.mark1.ai.duplicate;

import com.SIH.mark1.ai.dto.AIResponse;
import com.SIH.mark1.dto.request.CreateComplaintRequest;
import com.SIH.mark1.model.Complaint;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;

@Component
public class ComplaintEmbeddingTextBuilder {

    public String build(CreateComplaintRequest request, AIResponse analysis) {
        return join(
                request.getTitle(),
                request.getDescription(),
                request.getAddress(),
                analysis != null ? analysis.department() : null,
                analysis != null ? analysis.category() : null,
                analysis != null ? analysis.scope() : null,
                analysis != null ? analysis.resourceType() : null);
    }

    public String build(Complaint complaint) {
        return join(
                complaint.getTitle(),
                complaint.getDescription(),
                complaint.getLocationAddress(),
                complaint.getDepartment() != null ? complaint.getDepartment().getDepartmentName() : null,
                complaint.getCategory() != null ? complaint.getCategory().getCategoryName() : null,
                complaint.getWard(),
                complaint.getCity(),
                complaint.getState(),
                complaint.getPincode());
    }

    private String join(String... parts) {
        return Stream.of(parts)
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .reduce("", (left, right) -> left.isBlank() ? right : left + ". " + right);
    }
}
