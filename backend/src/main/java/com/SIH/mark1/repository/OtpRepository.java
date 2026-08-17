package com.SIH.mark1.repository;

import com.SIH.mark1.model.OtpPurpose;
import com.SIH.mark1.model.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OtpRepository extends JpaRepository<OtpVerification, Long> {
    Optional<OtpVerification> findTopByMobileAndPurposeAndVerifiedFalseAndExpiresAtAfterOrderByCreatedAtDesc(String mobile, OtpPurpose purpose, LocalDateTime now);

    Optional<OtpVerification> findTopByMobileAndPurposeAndVerifiedTrueAndExpiresAtAfterOrderByCreatedAtDesc(String mobile, OtpPurpose purpose, LocalDateTime now);

    List<OtpVerification> findByMobileAndVerifiedFalseAndExpiresAtBefore(String mobile, LocalDateTime now);
}
