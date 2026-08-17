package com.SIH.mark1.repository;

import com.SIH.mark1.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByCategoryName(String categoryName);
    List<Category> findByDepartmentDepartmentId(Long departmentId);
    boolean existsByCategoryNameAndDepartmentDepartmentId(String categoryName, Long departmentId);
}
