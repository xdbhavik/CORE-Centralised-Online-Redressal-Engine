package com.SIH.mark1;

import com.SIH.mark1.dto.response.DuplicateReviewItem;
import com.SIH.mark1.model.Category;
import com.SIH.mark1.model.Complaint;
import com.SIH.mark1.model.ComplaintStatusMaster;
import com.SIH.mark1.model.Department;
import com.SIH.mark1.model.DuplicateDetectionRecord;
import com.SIH.mark1.model.DuplicateReviewStatus;
import com.SIH.mark1.model.PreferredLanguage;
import com.SIH.mark1.model.PriorityMaster;
import com.SIH.mark1.model.User;
import com.SIH.mark1.model.UserRole;
import com.SIH.mark1.repository.CategoryRepository;
import com.SIH.mark1.repository.ComplaintRepository;
import com.SIH.mark1.repository.ComplaintStatusMasterRepository;
import com.SIH.mark1.repository.DepartmentRepository;
import com.SIH.mark1.repository.DuplicateComplaintRepository;
import com.SIH.mark1.repository.DuplicateDetectionRecordRepository;
import com.SIH.mark1.repository.PriorityMasterRepository;
import com.SIH.mark1.repository.UserRepository;
import com.SIH.mark1.service.admin.AdminDuplicateService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@SpringBootTest
@Transactional
@TestPropertySource(properties = "ai.duplicate.enabled=false")
public class AdminDuplicateServiceTest {

    @Autowired
    private AdminDuplicateService adminDuplicateService;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private DuplicateDetectionRecordRepository recordRepository;

    @Autowired
    private DuplicateComplaintRepository duplicateComplaintRepository;

    @Autowired
    private ComplaintStatusMasterRepository statusMasterRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PriorityMasterRepository priorityMasterRepository;

    @Test
    public void testReviewQueueReturnsPendingItems() {
        User admin = seedUser("ADMIN");
        User citizen = seedUser("CITIZEN");
        Complaint existing = seedComplaint(citizen, "Existing title", "GRV-2026-ADM-0001");
        Complaint flagged = seedComplaint(citizen, "Flagged title", "GRV-2026-ADM-0002");

        seedRecord(flagged, existing, DuplicateReviewStatus.PENDING);

        List<DuplicateReviewItem> queue = adminDuplicateService.reviewQueue();

        Assertions.assertFalse(queue.isEmpty());
        Assertions.assertTrue(queue.stream()
                .anyMatch(item -> item.getComplaintId().equals(flagged.getComplaintId())));
        Assertions.assertEquals(existing.getComplaintNo(), queue.stream()
                .filter(item -> item.getComplaintId().equals(flagged.getComplaintId()))
                .findFirst().orElseThrow().getMatchedComplaintNumber());
    }

    @Test
    public void testConfirmDuplicateLinksAndSetsStatus() {
        User admin = seedUser("ADMIN");
        User citizen = seedUser("CITIZEN");
        Complaint existing = seedComplaint(citizen, "Existing title", "GRV-2026-ADM-0003");
        Complaint flagged = seedComplaint(citizen, "Flagged title", "GRV-2026-ADM-0004");

        seedRecord(flagged, existing, DuplicateReviewStatus.PENDING);

        DuplicateReviewItem result = adminDuplicateService.confirmDuplicate(flagged.getComplaintId(), admin.getMobile());

        Assertions.assertEquals("CONFIRMED_DUPLICATE", result.getReviewStatus());
        Assertions.assertEquals(1, duplicateComplaintRepository.findAll().stream()
                .filter(l -> l.getDuplicateComplaint().getComplaintId().equals(flagged.getComplaintId()))
                .count());
    }

    @Test
    public void testRejectDuplicateMarksNotDuplicate() {
        User admin = seedUser("ADMIN");
        User citizen = seedUser("CITIZEN");
        Complaint existing = seedComplaint(citizen, "Existing title", "GRV-2026-ADM-0005");
        Complaint flagged = seedComplaint(citizen, "Flagged title", "GRV-2026-ADM-0006");

        seedRecord(flagged, existing, DuplicateReviewStatus.PENDING);

        DuplicateReviewItem result = adminDuplicateService.rejectDuplicate(flagged.getComplaintId(), admin.getMobile());

        Assertions.assertEquals("NOT_DUPLICATE", result.getReviewStatus());
        Assertions.assertEquals(0, duplicateComplaintRepository.findAll().size());
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────

    private User seedUser(String role) {
        String suffix = String.valueOf(System.nanoTime()) + role;
        String mobile = suffix.length() > 10 ? suffix.substring(0, 10) : suffix;
        return userRepository.save(User.builder()
                .name(role.toLowerCase() + " user")
                .mobile(mobile)
                .email(role.toLowerCase() + "_" + suffix + "@example.com")
                .passwordHash("hashed")
                .role(UserRole.valueOf(role))
                .language(PreferredLanguage.ENGLISH)
                .build());
    }

    private Complaint seedComplaint(User citizen, String title, String complaintNo) {
        Department dept = departmentRepository.save(Department.builder()
                .departmentName("Test Dept " + System.nanoTime())
                .active(true)
                .build());
        PriorityMaster priority = priorityMasterRepository.findByPriorityCode("HIGH")
                .orElseGet(() -> priorityMasterRepository.save(PriorityMaster.builder()
                        .priorityCode("HIGH")
                        .priorityName("High")
                        .displayOrder(1)
                        .active(true)
                        .build()));
        Category category = categoryRepository.save(Category.builder()
                .categoryName("Test Category " + System.nanoTime())
                .department(dept)
                .defaultPriority(priority)
                .active(true)
                .build());
        ComplaintStatusMaster status = statusMasterRepository.findByStatusCode("REGISTERED")
                .orElseGet(() -> statusMasterRepository.save(ComplaintStatusMaster.builder()
                        .statusCode("REGISTERED")
                        .statusName("Registered")
                        .displayOrder(1)
                        .active(true)
                        .build()));

        Complaint complaint = Complaint.builder()
                .complaintNo(complaintNo + "-" + System.nanoTime())
                .citizen(citizen)
                .department(dept)
                .category(category)
                .priority(priority)
                .currentStatus(status)
                .title(title)
                .description("Description for " + title)
                .build();
        complaint.setCreatedBy(citizen.getUserId());
        complaint.setDeleted(false);
        return complaintRepository.save(complaint);
    }

    private DuplicateDetectionRecord seedRecord(Complaint complaint, Complaint matched,
                                                DuplicateReviewStatus reviewStatus) {
        DuplicateDetectionRecord record = DuplicateDetectionRecord.builder()
                .complaint(complaint)
                .matchedComplaint(matched)
                .similarity(0.93)
                .decision("POSSIBLE_DUPLICATE")
                .scope("INDIVIDUAL")
                .resourceMatch(true)
                .locationMatch(false)
                .reasons("Similarity 0.93\nSame department\nSame category")
                .reviewStatus(reviewStatus)
                .build();
        record.setCreatedBy(complaint.getCreatedBy());
        return recordRepository.save(record);
    }
}