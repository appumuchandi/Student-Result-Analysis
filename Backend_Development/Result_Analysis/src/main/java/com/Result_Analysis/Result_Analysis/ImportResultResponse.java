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
}
