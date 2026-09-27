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
            @RequestParam(required = false) String semester,
            jakarta.servlet.http.HttpServletRequest request) {
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        String role = session!=null ? (String)session.getAttribute("AUTH_USER_ROLE") : null;
        String authUsn = session!=null ? (String)session.getAttribute("AUTH_USER_ID") : null;
        byte[] data;
        if("STUDENT".equalsIgnoreCase(role) && authUsn!=null){
            if(semester!=null && !semester.trim().isEmpty()){
                data = pdfExportService.generateRankPdfForStudentFiltered(authUsn, semester);
            } else {
                data = pdfExportService.generateRankPdfForStudent(authUsn);
            }
        } else {
            if((branch!=null && !branch.trim().isEmpty()) || (semester!=null && !semester.trim().isEmpty())){
                data = pdfExportService.generateRankPdfFiltered(branch, semester);
            } else {
                data = pdfExportService.generateRankPdf();
            }
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Rank_Analysis.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(data.length)
                .body(data);
    }
}
