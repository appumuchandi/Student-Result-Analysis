package com.Result_Analysis.Result_Analysis;

import jakarta.persistence.*;

@Entity
@Table(name = "subject_credits", uniqueConstraints = @UniqueConstraint(columnNames = {"semester", "subject_code", "scheme"}))
public class SubjectCredit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String semester; // "3" or "4"

    @Column(name = "subject_code", nullable = false, length = 20)
    private String subjectCode; // e.g., BEC401

    @Column(nullable = false)
    private Integer credits;

    @Column(nullable = false, length = 20)
    private String scheme = "2022";

    public SubjectCredit() {}
    public SubjectCredit(String semester, String subjectCode, Integer credits, String scheme) {
        this.semester = semester;
        this.subjectCode = subjectCode.toUpperCase().replaceAll("\\s+","");
        this.credits = credits;
        this.scheme = scheme;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }
    public Integer getCredits() { return credits; }
    public void setCredits(Integer credits) { this.credits = credits; }
    public String getScheme() { return scheme; }
    public void setScheme(String scheme) { this.scheme = scheme; }
}
