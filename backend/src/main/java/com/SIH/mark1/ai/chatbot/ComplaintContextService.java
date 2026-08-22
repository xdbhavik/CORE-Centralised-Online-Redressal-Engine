package com.SIH.mark1.ai.chatbot;

import com.SIH.mark1.model.Complaint;
import com.SIH.mark1.model.ComplaintAssignment;
import com.SIH.mark1.model.ComplaintAssignmentHistory;
import com.SIH.mark1.model.StatusHistory;
import com.SIH.mark1.model.User;
import com.SIH.mark1.repository.ComplaintAssignmentHistoryRepository;
import com.SIH.mark1.repository.ComplaintAssignmentRepository;
import com.SIH.mark1.repository.ComplaintRepository;
import com.SIH.mark1.repository.StatusHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ComplaintContextService {

    private static final Pattern COMPLAINT_NO_PATTERN = Pattern.compile("\\b(?:GRV|CMP)-[A-Z0-9-]+\\b", Pattern.CASE_INSENSITIVE);

    private final ComplaintRepository complaintRepository;
    private final ComplaintAssignmentRepository assignmentRepository;
    private final ComplaintAssignmentHistoryRepository assignmentHistoryRepository;
    private final StatusHistoryRepository statusHistoryRepository;

    public ComplaintContextService(ComplaintRepository complaintRepository,
                                   ComplaintAssignmentRepository assignmentRepository,
                                   ComplaintAssignmentHistoryRepository assignmentHistoryRepository,
                                   StatusHistoryRepository statusHistoryRepository) {
        this.complaintRepository = complaintRepository;
        this.assignmentRepository = assignmentRepository;
        this.assignmentHistoryRepository = assignmentHistoryRepository;
        this.statusHistoryRepository = statusHistoryRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ComplaintChatContext> resolveComplaint(User citizen, String message) {
        Optional<String> complaintNo = extractComplaintNumber(message);
        Optional<Complaint> complaint = complaintNo
                .flatMap(number -> complaintRepository.findByComplaintNoAndCitizenAndDeletedFalse(number, citizen));

        if (complaint.isEmpty() && complaintNo.isEmpty()) {
            complaint = complaintRepository.findFirstByCitizenAndDeletedFalseOrderByCreatedAtDesc(citizen);
        }

        return complaint.map(this::toContext);
    }

    @Transactional(readOnly = true)
    public boolean requestedComplaintBelongsElsewhere(User citizen, String message) {
        Optional<String> complaintNo = extractComplaintNumber(message);
        if (complaintNo.isEmpty()) {
            return false;
        }
        return complaintRepository.findByComplaintNoAndDeletedFalse(complaintNo.get())
                .map(complaint -> !complaint.getCitizen().getUserId().equals(citizen.getUserId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<ComplaintChatContext> listComplaints(User citizen) {
        return complaintRepository.findByCitizenAndDeletedFalse(citizen).stream()
                .sorted(Comparator.comparing(Complaint::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(this::toContext)
                .toList();
    }

    private ComplaintChatContext toContext(Complaint complaint) {
        Optional<ComplaintAssignment> assignment = assignmentRepository.findByComplaint(complaint);
        String officer = assignment.map(a -> safeName(a.getOfficer()))
                .orElseGet(() -> safeName(complaint.getOfficer()));
        String department = assignment.map(a -> a.getAssignedDepartment().getDepartmentName())
                .orElseGet(() -> complaint.getDepartment() != null ? complaint.getDepartment().getDepartmentName() : null);

        return new ComplaintChatContext(
                complaint.getComplaintNo(),
                complaint.getTitle(),
                complaint.getCurrentStatus() != null ? complaint.getCurrentStatus().getStatusName() : null,
                department,
                complaint.getCategory() != null ? complaint.getCategory().getCategoryName() : null,
                officer,
                complaint.getCreatedAt(),
                complaint.getUpdatedAt(),
                timelineFor(complaint)
        );
    }

    private List<ComplaintChatContext.TimelineItem> timelineFor(Complaint complaint) {
        List<ComplaintChatContext.TimelineItem> timeline = new ArrayList<>();

        if (complaint.getCreatedAt() != null) {
            timeline.add(new ComplaintChatContext.TimelineItem(complaint.getCreatedAt(), "Complaint Registered"));
        }

        for (StatusHistory history : statusHistoryRepository.findByComplaintOrderByCreatedAtDesc(complaint)) {
            LocalDateTime time = history.getChangedAt() != null ? history.getChangedAt() : history.getCreatedAt();
            String status = history.getStatus() != null ? history.getStatus().getStatusName() : "Status Updated";
            String remarks = history.getRemarks();
            String event = remarks == null || remarks.isBlank() ? status : status + " - " + remarks;
            timeline.add(new ComplaintChatContext.TimelineItem(time, event));
        }

        for (ComplaintAssignmentHistory history : assignmentHistoryRepository.findByComplaintOrderByChangedAtDesc(complaint)) {
            String officer = safeName(history.getNewOfficer());
            String event = officer == null ? "Assigned to officer" : "Assigned to " + officer;
            timeline.add(new ComplaintChatContext.TimelineItem(history.getChangedAt(), event));
        }

        return timeline.stream()
                .filter(item -> item.time() != null)
                .distinct()
                .sorted(Comparator.comparing(ComplaintChatContext.TimelineItem::time))
                .toList();
    }

    private Optional<String> extractComplaintNumber(String message) {
        if (message == null) {
            return Optional.empty();
        }
        Matcher matcher = COMPLAINT_NO_PATTERN.matcher(message.toUpperCase());
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }

    private String safeName(User user) {
        return user == null ? null : user.getName();
    }
}
