package com.Result_Analysis.Result_Analysis;

import java.util.List;

/**
 * DTO for final import result.
 */
public class ImportResultResponse {
    private boolean success;
    private String message;
    private int studentsImported;
    private int subjectResultsImported;
    private String branch;
    private String semester;
    private String batch;
    private String entryType;
    private String collegeCode;
    private List<String> errors;
    // Detailed sync counters
    private int studentsNew;
    private int studentsUpdated;
    private int studentsUnchanged;
    private int subjectResultsInserted;
    private int subjectResultsUpdated;
    private int subjectResultsUnchanged;
    private int skippedRows;
    private int duplicateRowsWithinFile;

    public ImportResultResponse() {}

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public int getStudentsImported() { return studentsImported; }
    public void setStudentsImported(int studentsImported) { this.studentsImported = studentsImported; }
    public int getSubjectResultsImported() { return subjectResultsImported; }
    public void setSubjectResultsImported(int subjectResultsImported) { this.subjectResultsImported = subjectResultsImported; }
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
    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }
    public int getStudentsNew() { return studentsNew; }
    public void setStudentsNew(int v) { this.studentsNew = v; }
    public int getStudentsUpdated() { return studentsUpdated; }
    public void setStudentsUpdated(int v) { this.studentsUpdated = v; }
    public int getStudentsUnchanged() { return studentsUnchanged; }
    public void setStudentsUnchanged(int v) { this.studentsUnchanged = v; }
    public int getSubjectResultsInserted() { return subjectResultsInserted; }
    public void setSubjectResultsInserted(int v) { this.subjectResultsInserted = v; }
    public int getSubjectResultsUpdated() { return subjectResultsUpdated; }
    public void setSubjectResultsUpdated(int v) { this.subjectResultsUpdated = v; }
    public int getSubjectResultsUnchanged() { return subjectResultsUnchanged; }
    public void setSubjectResultsUnchanged(int v) { this.subjectResultsUnchanged = v; }
    public int getSkippedRows() { return skippedRows; }
    public void setSkippedRows(int v) { this.skippedRows = v; }
    public int getDuplicateRowsWithinFile() { return duplicateRowsWithinFile; }
    public void setDuplicateRowsWithinFile(int v) { this.duplicateRowsWithinFile = v; }
}
