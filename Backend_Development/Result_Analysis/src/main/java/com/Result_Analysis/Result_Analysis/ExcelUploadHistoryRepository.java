package com.Result_Analysis.Result_Analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ExcelUploadHistoryRepository extends JpaRepository<ExcelUploadHistory, Long> {
    Optional<ExcelUploadHistory> findTopByOrderByUploadedAtDesc();
    Optional<ExcelUploadHistory> findTopByOrderByUploadedAtDescIdDesc();
}
