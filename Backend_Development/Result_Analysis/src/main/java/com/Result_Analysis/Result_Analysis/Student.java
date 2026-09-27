package com.Result_Analysis.Result_Analysis;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name="students")
public class Student {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(unique=true, nullable=false) 
    private String usn;
    @Column(name = "college_code")
    private String collegeCode;
    private String name;
    private String branch;
    private String semester;
    private String academicYear;
    private String email;
    @Column(name="phone_number")
    private String phoneNumber;
    private Double sgpa;
    private Double cgpa;
    private Double percentage;
    private String result;
    private Integer studentRank;
    private Integer backlog;
    @Column(name="lateral_entry")
    private Boolean lateralEntry = false;
    @OneToMany(mappedBy="student", cascade=CascadeType.ALL, orphanRemoval=true)
    @JsonManagedReference private List<SubjectResult> results=new ArrayList<>();
    public Student(){}
    public Long getId(){return id;} public void setId(Long v){id=v;}
    public String getUsn(){return usn;} public void setUsn(String v){usn=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getBranch(){return branch;} public void setBranch(String v){branch=v;}
    public String getSemester(){return semester;} public void setSemester(String v){semester=v;}
    public String getAcademicYear(){return academicYear;} public void setAcademicYear(String v){academicYear=v;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public Double getSgpa(){return sgpa;} public void setSgpa(Double v){sgpa=v;}
    public Double getCgpa(){return cgpa;} public void setCgpa(Double v){cgpa=v;}
    public Double getPercentage(){return percentage;} public void setPercentage(Double v){percentage=v;}
    public String getResult(){return result;} public void setResult(String v){result=v;}
    public List<SubjectResult> getResults(){return results;} public void setResults(List<SubjectResult> v){results=v;}
    public Integer getstudentRank(){
    return studentRank;
}

public void setstudentRank(Integer rank){
    this.studentRank = rank;
}

public String getCollegeCode(){
    return collegeCode;
}

public void setCollegeCode(String collegeCode){
    this.collegeCode = collegeCode;
}

public Integer getBacklog(){
     return backlog;
 }

 public void setBacklog(Integer backlog){
     this.backlog = backlog;
 }

 public Boolean getLateralEntry(){
     return lateralEntry;
 }

 public void setLateralEntry(Boolean lateralEntry){
     this.lateralEntry = lateralEntry;
  }

 public String getPhoneNumber(){
     return phoneNumber;
  }

 public void setPhoneNumber(String phoneNumber){
     this.phoneNumber = phoneNumber;
  }
}
