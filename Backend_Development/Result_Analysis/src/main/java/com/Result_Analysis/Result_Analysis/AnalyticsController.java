package com.Result_Analysis.Result_Analysis;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/students/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/subject-stats")
    public List<SubjectStatsDto> subjectStats(@RequestParam(required = false) String semester) {
        return analyticsService.getSubjectStats(semester);
    }

    @GetMapping("/lateral-entry")
    public LateralStatsDto lateralEntry(@RequestParam(required = false) String semester) {
        return analyticsService.getLateralEntryStats(semester);
    }

    // Alias for convenience: /api/results/analytics/*
    @GetMapping("/subject-wise")
    public List<SubjectStatsDto> subjectWise(@RequestParam(required = false) String semester) {
        return analyticsService.getSubjectStats(semester);
    }
}
