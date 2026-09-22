package com.Result_Analysis.Result_Analysis;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class PdfExportController {

    private final PdfExportService pdfExportService;

    public PdfExportController(PdfExportService pdfExportService) {
        this.pdfExportService = pdfExportService;
    }

    @GetMapping({"/students/export/pdf", "/api/results/export/pdf", "/api/export/pdf"})
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String semester) {

        byte[] data = pdfExportService.generateRankPdf();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Rank_Analysis.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(data.length)
                .body(data);
    }
}
