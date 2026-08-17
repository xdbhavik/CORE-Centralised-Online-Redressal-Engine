package com.SIH.mark1.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "officer_department",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_officer_department_ward",
                columnNames = {"officer_id", "department_id", "ward_id"}
        ),
        indexes = {
                @Index(name = "idx_officer_department_officer", columnList = "officer_id"),
                @Index(name = "idx_officer_department_department", columnList = "department_id"),
                @Index(name = "idx_officer_department_ward", columnList = "ward_id")
        }
)
public class OfficerDepartment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "officer_id", nullable = false)
    private User officer;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "ward_id")
    private Long wardId;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean active = true;
}
