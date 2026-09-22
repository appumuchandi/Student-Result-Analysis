package com.Result_Analysis.Result_Analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student,Long> {
    Optional<Student> findByUsnIgnoreCase(String usn);

    Optional<Student> findByUsnAndCollegeCodeAndBranch(
        String usn,
        String collegeCode,
        String branch
    );
}
