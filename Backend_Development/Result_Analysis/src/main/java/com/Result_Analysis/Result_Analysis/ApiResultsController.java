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
    public List<SubjectStatsDto> subjectStats(@RequestParam(required = false) String semester) {
        return analyticsService.getSubjectStats(semester);
    }

    @GetMapping("/analytics/subject-wise")
    public List<SubjectStatsDto> subjectWise(@RequestParam(required = false) String semester) {
        return analyticsService.getSubjectStats(semester);
    }

    @GetMapping("/analytics/lateral-entry")
    public LateralStatsDto lateralEntry(@RequestParam(required = false) String semester) {
        return analyticsService.getLateralEntryStats(semester);
    }
}
