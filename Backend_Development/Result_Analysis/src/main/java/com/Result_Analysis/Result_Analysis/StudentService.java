package com.Result_Analysis.Result_Analysis;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class StudentService {

    public String calculateGrade(Integer marks) {
        if (marks == null || marks < 36) return "Fail";
        if (marks >= 90) return "A+";
        if (marks >= 80) return "A";
        if (marks >= 70) return "B+";
        if (marks >= 60) return "B";
        if (marks >= 50) return "C+";
        return "C";
    }

    public double calculateGradePoint(String grade) {
        if (grade == null) return 0;

        grade = grade.trim().toUpperCase(); 
            if(grade.equals("A+"))  return 10;
            if(grade.equals("A"))  return 9;
            if(grade.equals("B+"))  return 8;
            if(grade.equals("B"))  return 7;
            if(grade.equals("C+"))  return 6;
            if(grade.equals("C"))  return 5;
            return 0;
    }
            

    public String calculateStatus(Integer marks) {
        return (marks != null && marks >= 36) ? "PASS" : "FAIL";
    }

    public void calculateSubjectResults(Student student) {
        if (student == null || student.getResults() == null) return;

        for (SubjectResult subject : student.getResults()) {
            subject.setGrade(calculateGrade(subject.getMarks()));
            subject.setStatus(calculateStatus(subject.getMarks()));
            subject.setStudent(student);
        }
    }

    public int getSemesterNumber(String semester) {
        if (semester == null || semester.trim().isEmpty()) return 0;

        String value = semester.trim().toLowerCase();

        if (value.startsWith("1")) return 1;
        if (value.startsWith("2")) return 2;
        if (value.startsWith("3")) return 3;
        if (value.startsWith("4")) return 4;
        if (value.startsWith("5")) return 5;
        if (value.startsWith("6")) return 6;
        if (value.startsWith("7")) return 7;
        if (value.startsWith("8")) return 8;

        return 0;
    }

    public List<SubjectResult> getSemesterResults(Student student, int semesterNumber) {
        List<SubjectResult> semesterResults = new ArrayList<>();

        if (student == null || student.getResults() == null) {
            return semesterResults;
        }

        for (SubjectResult subject : student.getResults()) {
            if (getSemesterNumber(subject.getSemester()) == semesterNumber) {
                semesterResults.add(subject);
            }
        }

        return semesterResults;
    }

    public double calculateSemesterSGPA(List<SubjectResult> semesterResults) {
        if (semesterResults == null || semesterResults.isEmpty()) return 0.0;

        double totalCreditPoints = 0.0;
        int totalCredits = 0;

        for (SubjectResult subject : semesterResults) {
            if (subject.getCredits() == null || subject.getCredits() <= 0) continue;

            String grade = subject.getGrade();

            if (grade == null || grade.trim().isEmpty()) {
                grade = calculateGrade(subject.getMarks());
                subject.setGrade(grade);
            }

            double gradePoint = calculateGradePoint(grade);

            totalCreditPoints += subject.getCredits() * gradePoint;
            totalCredits += subject.getCredits();
        }

        if (totalCredits == 0) return 0.0;

        return roundTwoDecimals(totalCreditPoints / totalCredits);
    }

    public int getLatestCompletedSemester(Student student) {
        if (student == null) return 0;

        int currentSemester = getSemesterNumber(student.getSemester());

        if (currentSemester <= 1) return 0;

        return currentSemester - 1;
    }

    public double calculateLatestSGPA(Student student) {
        int latestSemester = getLatestCompletedSemester(student);

        if (latestSemester == 0) return 0.0;

        return calculateSemesterSGPA(
                getSemesterResults(student, latestSemester)
        );
    }

    public double calculateLatestPercentage(Student student) {
        int latestSemester = getLatestCompletedSemester(student);

        if (latestSemester == 0) return 0.0;

        List<SubjectResult> results =
                getSemesterResults(student, latestSemester);

        if (results.isEmpty()) return 0.0;

        int totalMarks = 0;
        int subjectCount = 0;

        for (SubjectResult subject : results) {
            if (subject.getMarks() != null) {
                totalMarks += subject.getMarks();
                subjectCount++;
            }
        }

        if (subjectCount == 0) return 0.0;

        double percentage =
                ((double) totalMarks / (subjectCount * 100.0)) * 100.0;

        return roundTwoDecimals(percentage);
    }

    public double getSGPAForSemester(Student student, int semesterNumber) {
        return calculateSemesterSGPA(
                getSemesterResults(student, semesterNumber)
        );
    }

    public double calculateCGPA(Student student) {
        int latestSemester = getLatestCompletedSemester(student);

        if (latestSemester < 2) return 0.0;

        int previousSemester = latestSemester - 1;

        double latestSGPA =
                getSGPAForSemester(student, latestSemester);

        double previousSGPA =
                getSGPAForSemester(student, previousSemester);

        return roundTwoDecimals((latestSGPA + previousSGPA) / 2.0);
    }

    public String calculateOverallResult(Student student) {
        if (student == null ||
            student.getResults() == null ||
            student.getResults().isEmpty()) {
            return "FAIL";
        }

        for (SubjectResult subject : student.getResults()) {
            if (subject.getStatus() == null ||
                subject.getStatus().equalsIgnoreCase("FAIL")) {
                return "FAIL";
            }
        }

        return "PASS";
    }

    public int calculateBacklogs(Student student) {
        if (student == null || student.getResults() == null) return 0;

        int backlogCount = 0;

        for (SubjectResult subject : student.getResults()) {
            if (subject.getStatus() != null &&
                subject.getStatus().equalsIgnoreCase("FAIL")) {
                backlogCount++;
            }
        }

        return backlogCount;
    }

    public void calculateStudentData(Student student) {
        if (student == null) return;

        calculateSubjectResults(student);

        student.setSgpa(calculateLatestSGPA(student));
        student.setPercentage(calculateLatestPercentage(student));
        student.setCgpa(calculateCGPA(student));
        student.setBacklog(calculateBacklogs(student));
        student.setResult(calculateOverallResult(student));
    }

    private double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
