package com.Result_Analysis.Result_Analysis;

import java.util.List;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/students")
public class StudentController {
    private final StudentRepository repository;
    private final StudentService studentService;

    public StudentController(StudentRepository repository,StudentService studentService){
        this.repository=repository;
        this.studentService = studentService;
    }

    @GetMapping 
    public List<Student> getStudents(){
        List<Student> students = repository.findAll();
        for(Student student : students){
            studentService.calculateStudentData(student);
        }
        students.sort((a, b) -> {
            Double cgpaA = a.getCgpa();
            Double cgpaB = b.getCgpa();
            if (cgpaA == null && cgpaB == null) return 0;
            if (cgpaA == null) return 1;
            if (cgpaB == null) return -1;
            int result = Double.compare(cgpaB, cgpaA);
            if (result != 0) {
                    return result;
            }
            return String.valueOf(a.getUsn()).compareToIgnoreCase(String.valueOf(b.getUsn()));
        });
        int rank = 1;
        for(Student student : students){
            student.setstudentRank(rank);
            rank++;
        }
        return students;
    }

    @GetMapping("/usn/{usn}")
    public ResponseEntity<Student> getByUsn(@PathVariable String usn,@RequestParam String collegeCode,@RequestParam String branch){
        return repository.findByUsnAndCollegeCodeAndBranch(usn, collegeCode, branch).map(student -> {
            studentService.calculateStudentData(student);
            return ResponseEntity.ok(student);
        }) .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Student> getById(@PathVariable Long id){
        return repository.findById(id).map(ResponseEntity::ok).orElseGet(()->ResponseEntity.notFound().build());
    }

    @PostMapping("/addstudent")
    public Student addStudent(@RequestBody Student student){
        studentService.calculateStudentData(student);
        return repository.save(student);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Student> update(@PathVariable Long id,@RequestBody Student details){
        return repository.findById(id).map(s->{
            s.setUsn(details.getUsn()); s.setName(details.getName()); s.setBranch(details.getBranch());
            s.setSemester(details.getSemester()); s.setAcademicYear(details.getAcademicYear()); s.setEmail(details.getEmail());
            s.setSgpa(details.getSgpa()); s.setCgpa(details.getCgpa()); s.setPercentage(details.getPercentage()); s.setResult(details.getResult());
            s.setLateralEntry(details.getLateralEntry());
            s.setCollegeCode(details.getCollegeCode());
            s.setBacklog(details.getBacklog());
            if(details.getResults()!=null){s.getResults().clear(); details.getResults().forEach(r->{r.setStudent(s); s.getResults().add(r);});}
            studentService.calculateStudentData(s);
            return ResponseEntity.ok(repository.save(s));
        }).orElseGet(()->ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/result")
    public ResponseEntity<Student> addSubjectResult(
        @PathVariable Long id,
        @RequestBody SubjectResult subjectResult){
        Student student = repository.findById(id).orElse(null);
        if(student == null){
            return ResponseEntity.notFound().build();
        }
        subjectResult.setStudent(student);
        student.getResults().add(subjectResult);
        Student saveStudent = repository.save(student);
        return ResponseEntity.ok(saveStudent);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id){if(!repository.existsById(id)) return ResponseEntity.notFound().build();repository.deleteById(id);return ResponseEntity.noContent().build();}
}
