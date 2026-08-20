package com.SIH.mark1.service.admin;

import com.SIH.mark1.ai.qdrant.KnowledgeEventListener;
import com.SIH.mark1.ai.qdrant.KnowledgeEventListener.CategoryChangedEvent;
import com.SIH.mark1.ai.qdrant.KnowledgeEventListener.ChangeType;
import com.SIH.mark1.ai.qdrant.KnowledgeEventListener.DepartmentChangedEvent;
import com.SIH.mark1.dto.request.CategoryRequest;
import com.SIH.mark1.dto.request.DepartmentRequest;
import com.SIH.mark1.dto.request.OfficerRequest;
import com.SIH.mark1.dto.response.AnalyticsResponse;
import com.SIH.mark1.dto.response.CategoryResponse;
import com.SIH.mark1.dto.response.ComplaintAdminResponse;
import com.SIH.mark1.dto.response.DashboardResponse;
import com.SIH.mark1.dto.response.DepartmentResponse;
import com.SIH.mark1.dto.response.OfficerResponse;
import com.SIH.mark1.dto.response.ReportsResponse;
import com.SIH.mark1.dto.response.UserResponse;
import com.SIH.mark1.model.Category;
import com.SIH.mark1.model.Complaint;
import com.SIH.mark1.model.Department;
import com.SIH.mark1.model.OfficerDepartment;
import com.SIH.mark1.model.PriorityMaster;
import com.SIH.mark1.model.User;
import com.SIH.mark1.model.UserRole;
import com.SIH.mark1.repository.CategoryRepository;
import com.SIH.mark1.repository.ComplaintRepository;
import com.SIH.mark1.repository.DepartmentRepository;
import com.SIH.mark1.repository.OfficerDepartmentRepository;
import com.SIH.mark1.repository.PriorityMasterRepository;
import com.SIH.mark1.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    /** Status code used to identify complaints awaiting AI review. */
    private static final String STATUS_AI_ANALYZED = "AI_ANALYZED";

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final CategoryRepository categoryRepository;
    private final PriorityMasterRepository priorityMasterRepository;
    private final ComplaintRepository complaintRepository;
    private final OfficerDepartmentRepository officerDepartmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public AdminService(UserRepository userRepository,
                        DepartmentRepository departmentRepository,
                        CategoryRepository categoryRepository,
                        PriorityMasterRepository priorityMasterRepository,
                        ComplaintRepository complaintRepository,
                        OfficerDepartmentRepository officerDepartmentRepository,
                        PasswordEncoder passwordEncoder,
                        ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.categoryRepository = categoryRepository;
        this.priorityMasterRepository = priorityMasterRepository;
        this.complaintRepository = complaintRepository;
        this.officerDepartmentRepository = officerDepartmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    public DashboardResponse getDashboard() {
        long totalComplaints = complaintRepository.countByDeletedFalse();
        long resolved = complaintRepository.countByResolvedAtIsNotNullAndDeletedFalse();
        long open = totalComplaints - resolved;
        long pendingAiReviewed = complaintRepository.countByCurrentStatusStatusCodeAndDeletedFalse(STATUS_AI_ANALYZED);
        return DashboardResponse.builder()
                .totalUsers(userRepository.count())
                .totalOfficers(userRepository.countByRole(UserRole.OFFICER))
                .totalDepartments(departmentRepository.count())
                .totalCategories(categoryRepository.count())
                .totalComplaints(totalComplaints)
                .resolvedComplaints(resolved)
                .openComplaints(open)
                .pendingAiReviewedComplaints(pendingAiReviewed)
                .build();
    }

    public List<DepartmentResponse> getDepartments() {
        return departmentRepository.findAll().stream().map(this::toDepartmentResponse).toList();
    }

    @Transactional
    public DepartmentResponse createDepartment(DepartmentRequest req) {
        if (departmentRepository.existsByDepartmentName(req.getDepartmentName())) {
            throw new IllegalArgumentException("Department name already exists");
        }
        Department department = Department.builder()
                .departmentName(req.getDepartmentName())
                .description(req.getDescription())
                .contactEmail(req.getContactEmail())
                .contactPhone(req.getContactPhone())
                .active(req.getActive() != null ? req.getActive() : true)
                .build();
        Department saved = departmentRepository.save(department);
        eventPublisher.publishEvent(new DepartmentChangedEvent(saved, ChangeType.UPSERT));
        return toDepartmentResponse(saved);
    }

    @Transactional
    public DepartmentResponse updateDepartment(Long departmentId, DepartmentRequest req) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Department not found"));
        if (!department.getDepartmentName().equalsIgnoreCase(req.getDepartmentName())
                && departmentRepository.existsByDepartmentName(req.getDepartmentName())) {
            throw new IllegalArgumentException("Department name already exists");
        }
        department.setDepartmentName(req.getDepartmentName());
        department.setDescription(req.getDescription());
        department.setContactEmail(req.getContactEmail());
        department.setContactPhone(req.getContactPhone());
        if (req.getActive() != null) {
            department.setActive(req.getActive());
        }
        Department saved = departmentRepository.save(department);
        eventPublisher.publishEvent(new DepartmentChangedEvent(saved, ChangeType.UPSERT));
        return toDepartmentResponse(saved);
    }

    @Transactional
    public void deleteDepartment(Long departmentId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new IllegalArgumentException("Department not found"));
        department.setActive(false);
        departmentRepository.save(department);
        eventPublisher.publishEvent(new DepartmentChangedEvent(department, ChangeType.DELETE));
    }

    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAll().stream().map(this::toCategoryResponse).toList();
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest req) {
        if (categoryRepository.existsByCategoryNameAndDepartmentDepartmentId(req.getCategoryName(), req.getDepartmentId())) {
            throw new IllegalArgumentException("Category already exists in department");
        }
        Department department = departmentRepository.findById(req.getDepartmentId())
                .orElseThrow(() -> new IllegalArgumentException("Department not found"));
        PriorityMaster priority = resolvePriority(req.getDefaultPriorityId());
        Category category = Category.builder()
                .department(department)
                .categoryName(req.getCategoryName())
                .description(req.getDescription())
                .defaultPriority(priority)
                .active(req.getActive() != null ? req.getActive() : true)
                .build();
        Category saved = categoryRepository.save(category);
        eventPublisher.publishEvent(new CategoryChangedEvent(saved, ChangeType.UPSERT));
        return toCategoryResponse(saved);
    }

    @Transactional
    public CategoryResponse updateCategory(Long categoryId, CategoryRequest req) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        if (!category.getCategoryName().equalsIgnoreCase(req.getCategoryName())
                || !category.getDepartment().getDepartmentId().equals(req.getDepartmentId())) {
            if (categoryRepository.existsByCategoryNameAndDepartmentDepartmentId(req.getCategoryName(), req.getDepartmentId())) {
                throw new IllegalArgumentException("Category already exists in department");
            }
        }
        Department department = departmentRepository.findById(req.getDepartmentId())
                .orElseThrow(() -> new IllegalArgumentException("Department not found"));
        category.setDepartment(department);
        category.setCategoryName(req.getCategoryName());
        category.setDescription(req.getDescription());
        category.setDefaultPriority(resolvePriority(req.getDefaultPriorityId()));
        if (req.getActive() != null) {
            category.setActive(req.getActive());
        }
        Category saved = categoryRepository.save(category);
        eventPublisher.publishEvent(new CategoryChangedEvent(saved, ChangeType.UPSERT));
        return toCategoryResponse(saved);
    }

    @Transactional
    public void deleteCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        category.setActive(false);
        categoryRepository.save(category);
        eventPublisher.publishEvent(new CategoryChangedEvent(category, ChangeType.DELETE));
    }

    public List<OfficerResponse> getOfficers() {
        return userRepository.findByRoleAndDeletedFalse(UserRole.OFFICER).stream().map(this::toOfficerResponse).toList();
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAllByDeletedFalse().stream().map(this::toUserResponse).toList();
    }

    @Transactional
    public OfficerResponse createOfficer(OfficerRequest req) {
        if (userRepository.existsByMobile(req.getMobile())) {
            throw new IllegalArgumentException("Mobile already in use");
        }
        if (req.getEmail() != null && userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }
        if (req.getPassword() == null || req.getPassword().length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }
        Department department = departmentRepository.findById(req.getDepartmentId())
                .orElseThrow(() -> new IllegalArgumentException("Department not found"));
        User officer = User.builder()
                .name(req.getName())
                .mobile(req.getMobile())
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(UserRole.OFFICER)
                .language(req.getLanguage())
                .build();
        officer = userRepository.save(officer);

        OfficerDepartment mapping = OfficerDepartment.builder()
                .officer(officer)
                .department(department)
                .wardId(req.getWardId())
                .active(req.getActive() != null ? req.getActive() : true)
                .build();
        officerDepartmentRepository.save(mapping);
        return toOfficerResponse(officer);
    }

    @Transactional
    public OfficerResponse updateOfficer(Long officerId, OfficerRequest req) {
        User officer = userRepository.findById(officerId)
                .orElseThrow(() -> new IllegalArgumentException("Officer not found"));
        if (officer.getRole() != UserRole.OFFICER) {
            throw new IllegalArgumentException("User is not an officer");
        }
        if (!officer.getMobile().equals(req.getMobile()) && userRepository.existsByMobile(req.getMobile())) {
            throw new IllegalArgumentException("Mobile already in use");
        }
        if (req.getEmail() != null && !req.getEmail().equalsIgnoreCase(officer.getEmail()) && userRepository.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }
        officer.setName(req.getName());
        officer.setMobile(req.getMobile());
        officer.setEmail(req.getEmail());
        officer.setLanguage(req.getLanguage());
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            if (req.getPassword().length() < 6) {
                throw new IllegalArgumentException("Password must be at least 6 characters");
            }
            officer.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        }
        userRepository.save(officer);

        Department department = departmentRepository.findById(req.getDepartmentId())
                .orElseThrow(() -> new IllegalArgumentException("Department not found"));

        // Update the active mapping for the requested department, or create one if missing.
        List<OfficerDepartment> mappings = officerDepartmentRepository.findByOfficer(officer);
        OfficerDepartment mapping = mappings.stream()
                .filter(m -> m.getDepartment().getDepartmentId().equals(req.getDepartmentId()))
                .findFirst()
                .orElse(null);

        if (mapping == null) {
            // Deactivate any other active mappings and create a new one for the requested department.
            mappings.forEach(m -> m.setActive(false));
            officerDepartmentRepository.saveAll(mappings);
            mapping = OfficerDepartment.builder()
                    .officer(officer)
                    .department(department)
                    .wardId(req.getWardId())
                    .active(req.getActive() != null ? req.getActive() : true)
                    .build();
        } else {
            mapping.setDepartment(department);
            mapping.setWardId(req.getWardId());
            if (req.getActive() != null) {
                mapping.setActive(req.getActive());
            }
        }
        officerDepartmentRepository.save(mapping);
        return toOfficerResponse(officer);
    }

    @Transactional
    public void deleteOfficer(Long officerId) {
        User officer = userRepository.findById(officerId)
                .orElseThrow(() -> new IllegalArgumentException("Officer not found"));
        if (officer.getRole() != UserRole.OFFICER) {
            throw new IllegalArgumentException("User is not an officer");
        }
        officer.setDeleted(true);
        officer.setDeletedAt(LocalDateTime.now());
        userRepository.save(officer);
        List<OfficerDepartment> mappings = officerDepartmentRepository.findByOfficer(officer);
        mappings.forEach(m -> m.setActive(false));
        officerDepartmentRepository.saveAll(mappings);
    }

    @Transactional(readOnly = true)
    public List<ComplaintAdminResponse> getComplaints() {
        return complaintRepository.findByDeletedFalse().stream()
                .sorted(Comparator.comparing(Complaint::getCreatedAt).reversed())
                .map(this::toComplaintResponse)
                .toList();
    }

    /** Paginated complaints for the admin panel (newest first). */
    @Transactional(readOnly = true)
    public Page<ComplaintAdminResponse> getComplaintsPaginated(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return complaintRepository.findByDeletedFalse(
                        PageRequest.of(Math.max(page, 0), safeSize, Sort.by("createdAt").descending()))
                .map(this::toComplaintResponse);
    }

    /** Paginated users for the admin panel; optional role filter (CITIZEN/OFFICER/ADMIN). */
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsersPaginated(int page, int size, String role) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by("userId").ascending());
        if (role != null && !role.isBlank() && !"ALL".equalsIgnoreCase(role)) {
            return userRepository.findByRoleAndDeletedFalse(UserRole.valueOf(role.trim().toUpperCase()), pageable)
                    .map(this::toUserResponse);
        }
        return userRepository.findAllByDeletedFalse(pageable).map(this::toUserResponse);
    }

    @Transactional(readOnly = true)
    public ComplaintAdminResponse getComplaintById(Long complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found"));
        return toComplaintResponse(complaint);
    }

    public AnalyticsResponse getAnalytics() {
        try {
            long totalComplaints = complaintRepository.countByDeletedFalse();
            long resolved = complaintRepository.countByResolvedAtIsNotNullAndDeletedFalse();
            Map<String, Long> byStatus = toMap(complaintRepository.countComplaintsByStatus());
            Map<String, Long> byDepartment = toMap(complaintRepository.countComplaintsByDepartment());
            Map<String, Long> byPriority = toMap(complaintRepository.countComplaintsByPriority());
            return AnalyticsResponse.builder()
                    .totalComplaints(totalComplaints)
                    .resolvedComplaints(resolved)
                    .openComplaints(totalComplaints - resolved)
                    .complaintsByStatus(byStatus)
                    .complaintsByDepartment(byDepartment)
                    .complaintsByPriority(byPriority)
                    .build();
        } catch (Exception e) {
            log.error("Failed to load analytics data", e);
            return AnalyticsResponse.builder()
                    .totalComplaints(0)
                    .resolvedComplaints(0)
                    .openComplaints(0)
                    .complaintsByStatus(Map.of())
                    .complaintsByDepartment(Map.of())
                    .complaintsByPriority(Map.of())
                    .build();
        }
    }

    @Transactional(readOnly = true)
    public ReportsResponse getReports() {
        try {
            long totalComplaints = complaintRepository.countByDeletedFalse();
            long resolved = complaintRepository.countByResolvedAtIsNotNullAndDeletedFalse();
            double resolutionRate = totalComplaints == 0 ? 0.0 : (resolved * 100.0) / totalComplaints;
            List<ComplaintAdminResponse> recent = complaintRepository.findTop10ByDeletedFalseOrderByCreatedAtDesc()
                    .stream()
                    .map(this::toComplaintResponse)
                    .toList();
            long totalUsers = 0;
            long totalOfficers = 0;
            try {
                totalUsers = userRepository.count();
                totalOfficers = userRepository.countByRole(UserRole.OFFICER);
            } catch (Exception e) {
                log.error("Failed to load user counts for reports", e);
            }
            return ReportsResponse.builder()
                    .generatedAt(LocalDateTime.now())
                    .totalComplaints(totalComplaints)
                    .resolvedComplaints(resolved)
                    .resolutionRate(resolutionRate)
                    .totalUsers(totalUsers)
                    .totalOfficers(totalOfficers)
                    .recentComplaints(recent)
                    .build();
        } catch (Exception e) {
            log.error("Failed to load reports data", e);
            return ReportsResponse.builder()
                    .generatedAt(LocalDateTime.now())
                    .totalComplaints(0)
                    .resolvedComplaints(0)
                    .resolutionRate(0.0)
                    .totalUsers(0)
                    .totalOfficers(0)
                    .recentComplaints(List.of())
                    .build();
        }
    }

    private PriorityMaster resolvePriority(Long priorityId) {
        if (priorityId == null) {
            return null;
        }
        return priorityMasterRepository.findById(priorityId)
                .orElseThrow(() -> new IllegalArgumentException("Priority not found"));
    }

    private Map<String, Long> toMap(List<Object[]> rows) {
        return rows.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1],
                        Long::sum,
                        LinkedHashMap::new));
    }

    private DepartmentResponse toDepartmentResponse(Department department) {
        return DepartmentResponse.builder()
                .departmentId(department.getDepartmentId())
                .departmentName(department.getDepartmentName())
                .description(department.getDescription())
                .contactEmail(department.getContactEmail())
                .contactPhone(department.getContactPhone())
                .active(department.getActive())
                .build();
    }

    private CategoryResponse toCategoryResponse(Category category) {
        return CategoryResponse.builder()
                .categoryId(category.getCategoryId())
                .categoryName(category.getCategoryName())
                .description(category.getDescription())
                .departmentId(category.getDepartment().getDepartmentId())
                .departmentName(category.getDepartment().getDepartmentName())
                .defaultPriorityId(category.getDefaultPriority() != null ? category.getDefaultPriority().getPriorityId() : null)
                .defaultPriorityCode(category.getDefaultPriority() != null ? category.getDefaultPriority().getPriorityCode() : null)
                .active(category.getActive())
                .build();
    }

    private OfficerResponse toOfficerResponse(User officer) {
        List<OfficerDepartment> mappings = officerDepartmentRepository.findByOfficer(officer);
        OfficerDepartment mapping = mappings.stream().filter(OfficerDepartment::getActive).findFirst().orElse(mappings.stream().findFirst().orElse(null));
        return OfficerResponse.builder()
                .userId(officer.getUserId())
                .name(officer.getName())
                .mobile(officer.getMobile())
                .email(officer.getEmail())
                .language(officer.getLanguage())
                .role(officer.getRole())
                .active(!Boolean.TRUE.equals(officer.getDeleted()))
                .departmentId(mapping != null ? mapping.getDepartment().getDepartmentId() : null)
                .departmentName(mapping != null ? mapping.getDepartment().getDepartmentName() : null)
                .wardId(mapping != null ? mapping.getWardId() : null)
                .build();
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .name(user.getName())
                .mobile(user.getMobile())
                .email(user.getEmail())
                .language(user.getLanguage())
                .role(user.getRole())
                .active(!Boolean.TRUE.equals(user.getDeleted()))
                .address(user.getAddress())
                .build();
    }

    private ComplaintAdminResponse toComplaintResponse(Complaint complaint) {
        return ComplaintAdminResponse.builder()
                .complaintId(complaint.getComplaintId())
                .complaintNo(complaint.getComplaintNo())
                .title(complaint.getTitle())
                .citizenName(complaint.getCitizen() != null ? complaint.getCitizen().getName() : null)
                .citizenMobile(complaint.getCitizen() != null ? complaint.getCitizen().getMobile() : null)
                .departmentName(complaint.getDepartment() != null ? complaint.getDepartment().getDepartmentName() : null)
                .categoryName(complaint.getCategory() != null ? complaint.getCategory().getCategoryName() : null)
                .officerName(complaint.getOfficer() != null ? complaint.getOfficer().getName() : null)
                .statusCode(complaint.getCurrentStatus() != null ? complaint.getCurrentStatus().getStatusCode() : null)
                .priorityCode(complaint.getPriority() != null ? complaint.getPriority().getPriorityCode() : null)
                .latitude(complaint.getLatitude())
                .longitude(complaint.getLongitude())
                .createdAt(complaint.getCreatedAt())
                .assignedAt(complaint.getAssignedAt())
                .resolvedAt(complaint.getResolvedAt())
                .build();
    }
}