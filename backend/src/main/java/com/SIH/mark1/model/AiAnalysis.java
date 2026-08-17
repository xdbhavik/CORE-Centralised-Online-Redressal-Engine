package com.SIH.mark1.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ai_analysis")
public class AiAnalysis extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "analysis_id")
    private Long analysisId;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false, unique = true)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detected_department_id")
    private Department detectedDepartment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detected_category_id")
    private Category detectedCategory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detected_priority_id")
    private PriorityMaster detectedPriority;


    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Column(name = "priority_score", precision = 5, scale = 2)
    private BigDecimal priorityScore;

    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Column(name = "confidence_score", precision = 5, scale = 2)
    private BigDecimal confidenceScore;

    @Column(name = "model_name", length = 120)
    private String modelName;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;
}

