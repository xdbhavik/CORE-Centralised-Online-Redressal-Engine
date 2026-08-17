package com.SIH.mark1.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "complaint_assignment",
        indexes = {
                @Index(name = "idx_current_assignment_complaint", columnList = "complaint_id"),
                @Index(name = "idx_current_assignment_officer", columnList = "officer_id"),
                @Index(name = "idx_current_assignment_department", columnList = "assigned_department_id")
        }
)
public class ComplaintAssignment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false, unique = true)
    private Complaint complaint;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "officer_id", nullable = false)
    private User officer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_admin_id")
    private User assignedByAdmin;

    /**
     * Who performed the assignment — AI (auto-assignment engine) or MANUAL (admin).
     * Existing rows default to MANUAL for backward compatibility.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_by_type", nullable = false, length = 10)
    @Builder.Default
    private AssignedByType assignedByType = AssignedByType.MANUAL;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_department_id", nullable = false)
    private Department assignedDepartment;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "internal_note", columnDefinition = "TEXT")
    private String internalNote;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_status", nullable = false, length = 30)
    private AssignmentStatus assignmentStatus;
}
