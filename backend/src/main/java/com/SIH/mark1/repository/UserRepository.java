package com.SIH.mark1.repository;

import com.SIH.mark1.model.User;
import com.SIH.mark1.model.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByMobile(String mobile);
    Optional<User> findByEmail(String email);
    boolean existsByMobile(String mobile);
    boolean existsByEmail(String email);
    List<User> findByRole(UserRole role);
    List<User> findByRoleAndDeletedFalse(UserRole role);
    List<User> findAllByDeletedFalse();
    long countByRole(UserRole role);

    /** Paginated variants for the admin panel. */
    Page<User> findAllByDeletedFalse(Pageable pageable);
    Page<User> findByRoleAndDeletedFalse(UserRole role, Pageable pageable);
}
