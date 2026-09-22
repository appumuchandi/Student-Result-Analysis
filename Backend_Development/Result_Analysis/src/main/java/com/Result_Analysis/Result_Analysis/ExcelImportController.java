package com.Result_Analysis.Result_Analysis;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * Excel Import Controller — Frontend → Backend API → MySQL
 *
 * No DB credentials exposed to frontend.
 * Two-step workflow: preview (no DB write) + confirm (transactional write).
 */
@RestController
public class ExcelImportController {

    private final ExcelImportService excelImportService;
    private final ExcelUploadHistoryRepository uploadHistoryRepository;

    public ExcelImportController(ExcelImportService excelImportService, ExcelUploadHistoryRepository uploadHistoryRepository) {
        this.excelImportService = excelImportService;
        this.uploadHistoryRepository = uploadHistoryRepository;
    }

    @PostMapping("/api/import/preview")
    public ResponseEntity<?> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "branch", required = false) String branch,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "semester", required = false) String semester,
            @RequestParam(value = "batch", required = false) String batch,
            @RequestParam(value = "entryType", required = false) String entryType,
            @RequestParam(value = "collegeCode", required = false) String collegeCode,
            @RequestParam(value = "uploadedBy", required = false) String uploadedByParam,
            HttpServletRequest request) {
        try {
            String dept = (department != null && !department.trim().isEmpty()) ? department : branch;
            // SECURITY: Do NOT trust browser-supplied uploadedBy; resolve from server-side session (AUTH_USER_ID)
            String uploader = resolveAuthenticatedUser(request);
            // uploadedByParam is ignored for security; kept only for backward compat but not trusted
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            ImportPreviewResponse resp = excelImportService.preview(file, dept, semester, batch, entryType, collegeCode);
            resp.setUploadedBy(uploader);
            resp.setUploadedAt(now);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Failed to preview Excel: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
        }
    }

    @PostMapping("/api/import/confirm")
    public ResponseEntity<?> confirm(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "branch", required = false) String branch,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "semester", required = false) String semester,
            @RequestParam(value = "batch", required = false) String batch,
            @RequestParam(value = "entryType", required = false) String entryType,
            @RequestParam(value = "collegeCode", required = false) String collegeCode,
            @RequestParam(value = "uploadedBy", required = false) String uploadedByParam,
            HttpServletRequest request) {
        try {
            String dept = (department != null && !department.trim().isEmpty()) ? department : branch;
            // SECURITY: Resolve uploader from server session, not from browser-supplied uploadedByParam
            String uploader = resolveAuthenticatedUser(request);
            ImportResultResponse resp = excelImportService.importConfirm(file, dept, semester, batch, entryType, collegeCode, uploader);
            if (!resp.isSuccess()) {
                return ResponseEntity.ok(resp);
            }
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Import failed. No records were added: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    private boolean isBlank(String s){ return s==null || s.trim().isEmpty(); }

    private String resolveAuthenticatedUser(HttpServletRequest request){
        if(request == null) return "unknown";
        HttpSession session = request.getSession(false);
        if(session != null){
            Object uid = session.getAttribute("AUTH_USER_ID");
            if(uid != null && !uid.toString().trim().isEmpty()) return uid.toString().trim();
        }
        // No authenticated session — do NOT trust browser-supplied uploadedByParam; return unknown
        return "unknown";
    }

    // Health check for import feature
    @GetMapping("/api/import/status")
    public ResponseEntity<Map<String, String>> status() {
        Map<String, String> m = new HashMap<>();
        m.put("status", "Excel Import API ready");
        m.put("preview", "POST /api/import/preview (multipart file + branch, semester, batch, entryType, collegeCode)");
        m.put("confirm", "POST /api/import/confirm (same params, transactional)");
        m.put("parser", "HOD direct .xls supported: Regular sheet Int/Ext/Tot/Re/GP preserved, Re as P/A");
        return ResponseEntity.ok(m);
    }

    @GetMapping("/api/import/history/latest")
    public ResponseEntity<?> latestHistory() {
        var opt = uploadHistoryRepository.findTopByOrderByUploadedAtDesc();
        if (opt.isPresent()) return ResponseEntity.ok(opt.get());
        return ResponseEntity.ok(Map.of("message", "No Excel sheet has been uploaded yet."));
    }

    @GetMapping("/api/import/history")
    public ResponseEntity<?> allHistory() {
        return ResponseEntity.ok(uploadHistoryRepository.findAll());
    }
}
