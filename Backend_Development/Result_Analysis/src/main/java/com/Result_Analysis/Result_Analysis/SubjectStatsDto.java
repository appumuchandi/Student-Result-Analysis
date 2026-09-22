package com.Result_Analysis.Result_Analysis;

public class SubjectStatsDto {
    private String subject;
    private String code;
    private double averageMarks;
    private double passPercentage;
    private int highestMarks;
    private int lowestMarks;
    private int studentCount;
    private int totalStudents;

    public SubjectStatsDto() {}

    public SubjectStatsDto(String subject, String code, double averageMarks, double passPercentage, int highestMarks, int lowestMarks, int studentCount) {
        this.subject = subject;
        this.code = code;
        this.averageMarks = averageMarks;
        this.passPercentage = passPercentage;
        this.highestMarks = highestMarks;
        this.lowestMarks = lowestMarks;
        this.studentCount = studentCount;
    }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public double getAverageMarks() { return averageMarks; }
    public void setAverageMarks(double averageMarks) { this.averageMarks = averageMarks; }

    public double getPassPercentage() { return passPercentage; }
    public void setPassPercentage(double passPercentage) { this.passPercentage = passPercentage; }

    public int getHighestMarks() { return highestMarks; }
    public void setHighestMarks(int highestMarks) { this.highestMarks = highestMarks; }

    public int getLowestMarks() { return lowestMarks; }
    public void setLowestMarks(int lowestMarks) { this.lowestMarks = lowestMarks; }

    public int getStudentCount() { return studentCount; }
    public void setStudentCount(int studentCount) { this.studentCount = studentCount; }

    public int getTotalStudents() { return totalStudents; }
    public void setTotalStudents(int totalStudents) { this.totalStudents = totalStudents; }
}
