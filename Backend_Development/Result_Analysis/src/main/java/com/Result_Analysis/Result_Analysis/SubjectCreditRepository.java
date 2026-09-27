package com.Result_Analysis.Result_Analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SubjectCreditRepository extends JpaRepository<SubjectCredit, Long> {
    Optional<SubjectCredit> findBySemesterAndSubjectCodeAndScheme(String semester, String subjectCode, String scheme);
    Optional<SubjectCredit> findBySemesterAndSubjectCode(String semester, String subjectCode);
}
