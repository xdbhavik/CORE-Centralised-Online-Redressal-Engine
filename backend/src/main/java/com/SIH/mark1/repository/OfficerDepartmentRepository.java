package com.SIH.mark1.repository;

import com.SIH.mark1.model.OfficerDepartment;
import com.SIH.mark1.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OfficerDepartmentRepository extends JpaRepository<OfficerDepartment, Long> {
    List<OfficerDepartment> findByOfficer(User officer);
    Optional<OfficerDepartment> findByOfficerUserIdAndDepartmentDepartmentIdAndActiveTrue(Long officerId, Long departmentId);
    List<OfficerDepartment> findByDepartmentDepartmentIdAndActiveTrue(Long departmentId);
}


