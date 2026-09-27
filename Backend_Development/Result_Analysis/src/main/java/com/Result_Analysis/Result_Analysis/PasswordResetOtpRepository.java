package com.Result_Analysis.Result_Analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {
    List<PasswordResetOtp> findByUserIdOrderByCreatedAtDesc(String userId);
    List<PasswordResetOtp> findByUserIdAndCreatedAtAfter(String userId, LocalDateTime after);
    Optional<PasswordResetOtp> findTopByUserIdOrderByCreatedAtDesc(String userId);
}
