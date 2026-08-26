package com.SIH.mark1;

import com.SIH.mark1.dto.request.CreateComplaintRequest;
import com.SIH.mark1.dto.response.ComplaintResponse;
import com.SIH.mark1.dto.response.ComplaintDetailsResponse;
import com.SIH.mark1.model.Category;
import com.SIH.mark1.model.ComplaintStatusMaster;
import com.SIH.mark1.model.Department;
import com.SIH.mark1.model.PriorityMaster;
import com.SIH.mark1.model.User;
import com.SIH.mark1.model.UserRole;
import com.SIH.mark1.model.PreferredLanguage;
import com.SIH.mark1.repository.CategoryRepository;
import com.SIH.mark1.repository.ComplaintRepository;
import com.SIH.mark1.repository.ComplaintStatusMasterRepository;
import com.SIH.mark1.repository.DepartmentRepository;
import com.SIH.mark1.repository.PriorityMasterRepository;
import com.SIH.mark1.repository.UserRepository;
import com.SIH.mark1.service.AutoAssignmentService;
import com.SIH.mark1.service.ComplaintService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@SpringBootTest
@Transactional
@TestPropertySource(properties = "ai.duplicate.enabled=false")
public class ComplaintCreationTest {

    /**
     * Auto-assignment is stubbed out so this test observes the creation flow in isolation.
     *
     * <p>Without this the test was order- and environment-dependent: {@code createComplaint}
     * calls {@code autoAssignIfEnabled}, which reads the {@code AUTO_ASSIGNMENT_ENABLED} row
     * from {@code system_settings}. Because the suite runs against the shared dev MySQL
     * instance, any earlier run that flipped that flag to {@code true} left it {@code true},
     * and the complaint advanced AI_ANALYZED → ASSIGNED before this test asserted on it.
     * That is correct application behaviour, so the assertion below is not what was wrong —
     * the test's dependency on mutable global state was. Assignment has its own tests.</p>
     */
    @MockBean
    private AutoAssignmentService autoAssignmentService;

    @Autowired
    private ComplaintService complaintService;


    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ComplaintStatusMasterRepository statusMasterRepository;

    @Autowired
    private PriorityMasterRepository priorityMasterRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Test
    public void testCreateComplaintFlow() {
        // 1. Seed Department
        Department dept = Department.builder()
                .departmentName("Water Works")
                .description("Handles water leaks and plumbing issues")
                .active(true)
                .build();
        dept = departmentRepository.save(dept);

        // 2. Seed Priority
        PriorityMaster priority = priorityMasterRepository.findByPriorityCode("HIGH")
                .orElseGet(() -> priorityMasterRepository.save(PriorityMaster.builder()
                        .priorityCode("HIGH")
                        .priorityName("High Priority")
                        .displayOrder(1)
                        .active(true)
                        .build()));

        // 3. Seed Category
        Category category = Category.builder()
                .categoryName("Pipe Leakage")
                .description("Leaking public pipes")
                .department(dept)
                .defaultPriority(priority)
                .active(true)
                .build();
        category = categoryRepository.save(category);

        // 4. Seed Status
        ComplaintStatusMaster status = statusMasterRepository.findByStatusCode("REGISTERED")
                .orElseGet(() -> statusMasterRepository.save(ComplaintStatusMaster.builder()
                        .statusCode("REGISTERED")
                        .statusName("Registered")
                        .displayOrder(1)
                        .active(true)
                        .build()));

        // 5. Seed User (Citizen) with dynamic credentials to prevent duplicate key constraint violations
        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        String mobile = uniqueSuffix.substring(Math.max(0, uniqueSuffix.length() - 10));
        String email = "ramesh_" + uniqueSuffix + "@example.com";

        User citizen = User.builder()
                .name("Ramesh Kumar")
                .mobile(mobile)
                .email(email)
                .passwordHash("hashed_pass")
                .role(UserRole.CITIZEN)
                .language(PreferredLanguage.ENGLISH)
                .build();
        citizen = userRepository.save(citizen);

        // 6. Create Complaint Request
        CreateComplaintRequest request = new CreateComplaintRequest();
        request.setTitle("Water leakage in main street");
        request.setDescription("The main pipeline is broken and water is flowing everywhere.");
        request.setLatitude(new BigDecimal("28.6139"));
        request.setLongitude(new BigDecimal("77.2090"));
        request.setAddress("Connaught Place, New Delhi");
        request.setLanguage("ENGLISH");

        // 7. Invoke createComplaint
        ComplaintResponse response = complaintService.createComplaint(citizen.getMobile(), request);

        // 8. Assertions
        Assertions.assertNotNull(response);
        Assertions.assertNotNull(response.getComplaintId());
        Assertions.assertTrue(response.getComplaintNumber().startsWith("GRV-2026-"));
        Assertions.assertEquals("AI_ANALYZED", response.getStatus());

        // 9. Verify Detail Retrieve
        ComplaintDetailsResponse details = complaintService.getComplaintById(citizen.getMobile(), response.getComplaintId());
        Assertions.assertEquals("Water leakage in main street", details.getTitle());
        Assertions.assertEquals("Water Supply", details.getCategory());
        Assertions.assertEquals("Water Department", details.getDepartment());
        Assertions.assertEquals("HIGH", details.getPriority());
        Assertions.assertEquals("AI_ANALYZED", details.getStatus());
        
        System.out.println("====== SUCCESS: Complaint Created successfully ======");
        System.out.println("Complaint ID: " + response.getComplaintId());
        System.out.println("Complaint Number: " + response.getComplaintNumber());
        System.out.println("=====================================================");
    }
}

