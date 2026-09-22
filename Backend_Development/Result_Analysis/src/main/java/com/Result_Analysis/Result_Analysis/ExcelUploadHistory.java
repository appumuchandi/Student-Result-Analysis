package com.Result_Analysis.Result_Analysis;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "excel_upload_history")
public class ExcelUploadHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "department")
    private String department;

    @Column(name = "batch")
    private String batch;

    @Column(name = "semester")
    private String semester;

    @Column(name = "students_imported")
    private Integer studentsImported;

    @Column(name = "subject_results_imported")
    private Integer subjectResultsImported;

    @Column(name = "status")
    private String status; // SUCCESS, FAILED

    public ExcelUploadHistory() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }
    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
    public Integer getStudentsImported() { return studentsImported; }
    public void setStudentsImported(Integer studentsImported) { this.studentsImported = studentsImported; }
    public Integer getSubjectResultsImported() { return subjectResultsImported; }
    public void setSubjectResultsImported(Integer subjectResultsImported) { this.subjectResultsImported = subjectResultsImported; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
