package com.Result_Analysis.Result_Analysis;

public class SubjectComparisonDto {
    private String currentUsn;
    private String previousUsn;
    private String subjectCode;
    private String subjectName;
    private Integer currentMarks;
    private Integer previousMarks;
    private Integer difference; // current - previous
    private String currentRe;
    private String previousRe;

    public SubjectComparisonDto() {}

    public SubjectComparisonDto(String currentUsn, String previousUsn, String subjectCode, String subjectName, Integer currentMarks, Integer previousMarks, Integer difference) {
        this.currentUsn = currentUsn;
        this.previousUsn = previousUsn;
        this.subjectCode = subjectCode;
        this.subjectName = subjectName;
        this.currentMarks = currentMarks;
        this.previousMarks = previousMarks;
        this.difference = difference;
    }

    public String getCurrentUsn() { return currentUsn; }
    public void setCurrentUsn(String v) { currentUsn = v; }
    public String getPreviousUsn() { return previousUsn; }
    public void setPreviousUsn(String v) { previousUsn = v; }
    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String v) { subjectCode = v; }
    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String v) { subjectName = v; }
    public Integer getCurrentMarks() { return currentMarks; }
    public void setCurrentMarks(Integer v) { currentMarks = v; }
    public Integer getPreviousMarks() { return previousMarks; }
    public void setPreviousMarks(Integer v) { previousMarks = v; }
    public Integer getDifference() { return difference; }
    public void setDifference(Integer v) { difference = v; }
    public String getCurrentRe() { return currentRe; }
    public void setCurrentRe(String v) { currentRe = v; }
    public String getPreviousRe() { return previousRe; }
    public void setPreviousRe(String v) { previousRe = v; }
}
