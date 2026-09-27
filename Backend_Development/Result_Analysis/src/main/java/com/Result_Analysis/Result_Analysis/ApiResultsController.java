package com.Result_Analysis.Result_Analysis;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/results")
public class ApiResultsController {

    private final AnalyticsService analyticsService;

    public ApiResultsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/analytics/subject-stats")
    public List<SubjectStatsDto> subjectStats(@RequestParam(required = false) String semester, jakarta.servlet.http.HttpServletRequest request) {
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        String role = session!=null ? (String)session.getAttribute("AUTH_USER_ROLE") : null;
        String authUsn = session!=null ? (String)session.getAttribute("AUTH_USER_ID") : null;
        if("STUDENT".equalsIgnoreCase(role) && authUsn!=null) return analyticsService.getSubjectStatsForStudent(semester, authUsn);
        return analyticsService.getSubjectStats(semester);
    }

    @GetMapping("/analytics/subject-wise")
    public List<SubjectStatsDto> subjectWise(@RequestParam(required = false) String semester, jakarta.servlet.http.HttpServletRequest request) {
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        String role = session!=null ? (String)session.getAttribute("AUTH_USER_ROLE") : null;
        String authUsn = session!=null ? (String)session.getAttribute("AUTH_USER_ID") : null;
        if("STUDENT".equalsIgnoreCase(role) && authUsn!=null) return analyticsService.getSubjectStatsForStudent(semester, authUsn);
        return analyticsService.getSubjectStats(semester);
    }

    @GetMapping("/analytics/lateral-entry")
    public LateralStatsDto lateralEntry(@RequestParam(required = false) String semester, jakarta.servlet.http.HttpServletRequest request) {
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        String role = session!=null ? (String)session.getAttribute("AUTH_USER_ROLE") : null;
        String authUsn = session!=null ? (String)session.getAttribute("AUTH_USER_ID") : null;
        if("STUDENT".equalsIgnoreCase(role) && authUsn!=null) return analyticsService.getLateralEntryStatsForStudent(semester, authUsn);
        return analyticsService.getLateralEntryStats(semester);
    }
}
