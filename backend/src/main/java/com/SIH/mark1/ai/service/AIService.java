package com.SIH.mark1.ai.service;

import com.SIH.mark1.ai.dto.AIResponse;
import com.SIH.mark1.dto.request.CreateComplaintRequest;
import com.SIH.mark1.model.Complaint;
import com.SIH.mark1.repository.ComplaintRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AIService {

    private final ComplaintAnalysisService complaintAnalysisService;
    private final ComplaintRepository complaintRepository;

    public AIService(ComplaintAnalysisService complaintAnalysisService,
                     ComplaintRepository complaintRepository) {
        this.complaintAnalysisService = complaintAnalysisService;
        this.complaintRepository = complaintRepository;
    }

    public AIResponse analyze(CreateComplaintRequest request) {
        return complaintAnalysisService.analyze(request);
    }

    @Transactional(readOnly = true)
    public AIResponse analyze(Long complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintId));

        CreateComplaintRequest request = new CreateComplaintRequest();
        request.setTitle(complaint.getTitle());
        request.setDescription(complaint.getDescription());
        request.setLatitude(complaint.getLatitude());
        request.setLongitude(complaint.getLongitude());
        request.setAddress(complaint.getLocationAddress());

        return complaintAnalysisService.analyze(request);
    }

    public String modelName() {
        return complaintAnalysisService.modelName();
    }
}
