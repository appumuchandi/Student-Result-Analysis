package com.Result_Analysis.Result_Analysis;

import java.util.Map;

public class LateralStatsDto {
    private String semester;
    private long regularCount;
    private long lateralCount;
    private long totalCount;

    // For all semesters map
    private Map<String, LateralStatsDto> semesterWise;

    public LateralStatsDto() {}

    public LateralStatsDto(String semester, long regularCount, long lateralCount) {
        this.semester = semester;
        this.regularCount = regularCount;
        this.lateralCount = lateralCount;
        this.totalCount = regularCount + lateralCount;
    }

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }

    public long getRegularCount() { return regularCount; }
    public void setRegularCount(long regularCount) { this.regularCount = regularCount; }

    public long getLateralCount() { return lateralCount; }
    public void setLateralCount(long lateralCount) { this.lateralCount = lateralCount; }

    public long getTotalCount() { return totalCount; }
    public void setTotalCount(long totalCount) { this.totalCount = totalCount; }

    public Map<String, LateralStatsDto> getSemesterWise() { return semesterWise; }
    public void setSemesterWise(Map<String, LateralStatsDto> semesterWise) { this.semesterWise = semesterWise; }
}
