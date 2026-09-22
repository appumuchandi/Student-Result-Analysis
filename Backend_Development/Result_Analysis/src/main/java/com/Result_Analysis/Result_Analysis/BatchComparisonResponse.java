package com.Result_Analysis.Result_Analysis;

import java.util.List;

public class BatchComparisonResponse {
    private String currentBatch;
    private String previousBatch;
    private String branch;
    private String semester;
    private String subjectCode;
    private String subjectName;
    private Double currentBatchAverage;
    private Double previousBatchAverage;
    private Double averageDifference;
    private int matchedStudents;
    private int unmatchedCurrent;
    private int unmatchedPrevious;
    private List<SubjectComparisonDto> comparisons;

    public BatchComparisonResponse() {}

    public String getCurrentBatch() { return currentBatch; }
    public void setCurrentBatch(String v) { currentBatch = v; }
    public String getPreviousBatch() { return previousBatch; }
    public void setPreviousBatch(String v) { previousBatch = v; }
    public String getBranch() { return branch; }
    public void setBranch(String v) { branch = v; }
    public String getSemester() { return semester; }
    public void setSemester(String v) { semester = v; }
    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String v) { subjectCode = v; }
    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String v) { subjectName = v; }
    public Double getCurrentBatchAverage() { return currentBatchAverage; }
    public void setCurrentBatchAverage(Double v) { currentBatchAverage = v; }
    public Double getPreviousBatchAverage() { return previousBatchAverage; }
    public void setPreviousBatchAverage(Double v) { previousBatchAverage = v; }
    public Double getAverageDifference() { return averageDifference; }
    public void setAverageDifference(Double v) { averageDifference = v; }
    public int getMatchedStudents() { return matchedStudents; }
    public void setMatchedStudents(int v) { matchedStudents = v; }
    public int getUnmatchedCurrent() { return unmatchedCurrent; }
    public void setUnmatchedCurrent(int v) { unmatchedCurrent = v; }
    public int getUnmatchedPrevious() { return unmatchedPrevious; }
    public void setUnmatchedPrevious(int v) { unmatchedPrevious = v; }
    public List<SubjectComparisonDto> getComparisons() { return comparisons; }
    public void setComparisons(List<SubjectComparisonDto> v) { comparisons = v; }
}
