package com.Result_Analysis.Result_Analysis;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/compare")
public class CompareController {

    private final CompareService compareService;

    public CompareController(CompareService compareService) {
        this.compareService = compareService;
    }

    @GetMapping("/subjects")
    public BatchComparisonResponse compareSubjects(
            @RequestParam String currentBatch,
            @RequestParam String previousBatch,
            @RequestParam String branch,
            @RequestParam String semester,
            @RequestParam String subject,
            jakarta.servlet.http.HttpServletRequest request) {
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        String role = session!=null ? (String)session.getAttribute("AUTH_USER_ROLE") : null;
        String authUsn = session!=null ? (String)session.getAttribute("AUTH_USER_ID") : null;
        if("STUDENT".equalsIgnoreCase(role) && authUsn!=null){
            return compareService.compareForStudent(currentBatch, previousBatch, branch, semester, subject, authUsn);
        }
        return compareService.compare(currentBatch, previousBatch, branch, semester, subject);
    }

    // Alias for flexibility
    @GetMapping("/batch")
    public BatchComparisonResponse compareBatch(
            @RequestParam String currentBatch,
            @RequestParam String previousBatch,
            @RequestParam String branch,
            @RequestParam String semester,
            @RequestParam String subject,
            jakarta.servlet.http.HttpServletRequest request) {
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        String role = session!=null ? (String)session.getAttribute("AUTH_USER_ROLE") : null;
        String authUsn = session!=null ? (String)session.getAttribute("AUTH_USER_ID") : null;
        if("STUDENT".equalsIgnoreCase(role) && authUsn!=null){
            return compareService.compareForStudent(currentBatch, previousBatch, branch, semester, subject, authUsn);
        }
        return compareService.compare(currentBatch, previousBatch, branch, semester, subject);
    }
}
