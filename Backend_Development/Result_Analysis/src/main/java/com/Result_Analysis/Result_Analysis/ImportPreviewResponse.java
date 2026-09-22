package com.Result_Analysis.Result_Analysis;

import java.util.List;
import java.util.Map;

/**
 * DTO for Excel import preview.
 * IMPORTANT: The HOD Excel format has NOT been provided yet.
 * This DTO is format-agnostic and displays raw sheet information + provisional parsing.
 * The actual student/subject mapping in ExcelImportService#parseForPreview() will be finalized
 * once the HOD Excel file is provided. See service javadoc for details.
 */
public class ImportPreviewResponse {
    private String fileName;
    private String branch;
    private String semester;
    private String batch; // maps to Student.academicYear
    private String entryType; // Regular / Lateral Entry
    private String collegeCode;
    private int totalRows;
    private int studentsDetected;
    private int subjectsDetected;
    private List<String> sheetNames;
    private List<String> headers;
    private List<Map<String, Object>> previewRows; // first 5 rows
    private List<String> validationErrors;
    private List<String> validationWarnings;
    private boolean hasErrors;
    private String parserNote;
    // Uploader info: backend-generated, not browser-trusted; shown as ONLY visible uploader section in Dashboard
    private String uploadedBy;
    private java.time.LocalDateTime uploadedAt;

    public ImportPreviewResponse() {}

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
    public String getBatch() { return batch; }
    public void setBatch(String batch) { this.batch = batch; }
    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }
    public String getCollegeCode() { return collegeCode; }
    public void setCollegeCode(String collegeCode) { this.collegeCode = collegeCode; }
    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
    public int getStudentsDetected() { return studentsDetected; }
    public void setStudentsDetected(int studentsDetected) { this.studentsDetected = studentsDetected; }
    public int getSubjectsDetected() { return subjectsDetected; }
    public void setSubjectsDetected(int subjectsDetected) { this.subjectsDetected = subjectsDetected; }
    public List<String> getSheetNames() { return sheetNames; }
    public void setSheetNames(List<String> sheetNames) { this.sheetNames = sheetNames; }
    public List<String> getHeaders() { return headers; }
    public void setHeaders(List<String> headers) { this.headers = headers; }
    public List<Map<String, Object>> getPreviewRows() { return previewRows; }
    public void setPreviewRows(List<Map<String, Object>> previewRows) { this.previewRows = previewRows; }
    public List<String> getValidationErrors() { return validationErrors; }
    public void setValidationErrors(List<String> validationErrors) { this.validationErrors = validationErrors; }
    public List<String> getValidationWarnings() { return validationWarnings; }
    public void setValidationWarnings(List<String> validationWarnings) { this.validationWarnings = validationWarnings; }
    public boolean isHasErrors() { return hasErrors; }
    public void setHasErrors(boolean hasErrors) { this.hasErrors = hasErrors; }
    public String getParserNote() { return parserNote; }
    public void setParserNote(String parserNote) { this.parserNote = parserNote; }
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
    public java.time.LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(java.time.LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
