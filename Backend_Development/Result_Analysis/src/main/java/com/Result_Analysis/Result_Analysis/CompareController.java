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
            @RequestParam String subject) {
        return compareService.compare(currentBatch, previousBatch, branch, semester, subject);
    }

    // Alias for flexibility
    @GetMapping("/batch")
    public BatchComparisonResponse compareBatch(
            @RequestParam String currentBatch,
            @RequestParam String previousBatch,
            @RequestParam String branch,
            @RequestParam String semester,
            @RequestParam String subject) {
        return compareService.compare(currentBatch, previousBatch, branch, semester, subject);
    }
}
