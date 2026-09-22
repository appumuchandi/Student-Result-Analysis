package com.Result_Analysis.Result_Analysis;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExcelExportService {

    private final StudentRepository studentRepository;
    private final StudentService studentService;

    public ExcelExportService(StudentRepository studentRepository, StudentService studentService) {
        this.studentRepository = studentRepository;
        this.studentService = studentService;
    }

    public byte[] generateExcel(List<Student> students) {
        // Ensure ranks and calculated fields are populated
        // Recalculate and sort by CGPA like controller does
        for (Student s : students) {
            studentService.calculateStudentData(s);
        }
        students.sort((a, b) -> {
            Double cgpaA = a.getCgpa();
            Double cgpaB = b.getCgpa();
            if (cgpaA == null && cgpaB == null) return 0;
            if (cgpaA == null) return 1;
            if (cgpaB == null) return -1;
            int r = Double.compare(cgpaB, cgpaA);
            if (r != 0) return r;
            return String.valueOf(a.getUsn()).compareToIgnoreCase(String.valueOf(b.getUsn()));
        });
        int rank = 1;
        for (Student s : students) {
            s.setstudentRank(rank++);
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            createSummarySheet(workbook, students);
            createSubjectDetailsSheet(workbook, students);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Excel file", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] generateExcelForAll() {
        List<Student> all = studentRepository.findAll();
        // Force initialization of lazy results within transaction
        for (Student s : all) { s.getResults().size(); }
        return generateExcel(all);
    }

    @Transactional(readOnly = true)
    public byte[] generateExcelFiltered(String semester, String branch, Boolean lateralEntry) {
        List<Student> all = studentRepository.findAll();
        for (Student s : all) { s.getResults().size(); }
        List<Student> filtered = filterStudents(all, semester, branch, lateralEntry);
        return generateExcel(filtered);
    }

    private List<Student> filterStudents(List<Student> all, String semester, String branch, Boolean lateralEntry) {
        List<Student> filtered = new ArrayList<>();
        for (Student s : all) {
            if (semester != null && !semester.trim().isEmpty()) {
                String semFilter = normalizeSemester(semester);
                String studentSem = normalizeSemester(s.getSemester());
                // If filter is specified, match either student's semester field or any subject result semester
                boolean matches = false;
                if (semFilter.equals(studentSem)) {
                    matches = true;
                } else {
                    if (s.getResults() != null) {
                        for (SubjectResult sr : s.getResults()) {
                            if (semFilter.equals(normalizeSemester(sr.getSemester()))) {
                                matches = true;
                                break;
                            }
                        }
                    }
                }
                if (!matches) continue;
            }
            if (branch != null && !branch.trim().isEmpty()) {
                if (s.getBranch() == null || !s.getBranch().equalsIgnoreCase(branch.trim())) continue;
            }
            if (lateralEntry != null) {
                Boolean isLateral = s.getLateralEntry() != null ? s.getLateralEntry() : false;
                if (!isLateral.equals(lateralEntry)) continue;
            }
            filtered.add(s);
        }
        return filtered;
    }

    private String normalizeSemester(String sem) {
        if (sem == null) return "";
        String t = sem.trim().toLowerCase();
        // Reuse logic from StudentService but more tolerant
        if (t.contains("1")) return "1";
        if (t.contains("2")) return "2";
        if (t.contains("3")) return "3";
        if (t.contains("4")) return "4";
        if (t.contains("5")) return "5";
        if (t.contains("6")) return "6";
        if (t.contains("7")) return "7";
        if (t.contains("8")) return "8";
        return t;
    }

    private void createSummarySheet(XSSFWorkbook workbook, List<Student> students) {
        XSSFSheet sheet = workbook.createSheet("Student Results");
        sheet.createFreezePane(0, 1);

        // Styles
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerFont.setFontHeightInPoints((short) 11);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);
        headerStyle.setTopBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        headerStyle.setBottomBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        headerStyle.setLeftBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        headerStyle.setRightBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        headerStyle.setWrapText(true);

        CellStyle dataStyle = workbook.createCellStyle();
        dataStyle.setBorderTop(BorderStyle.THIN);
        dataStyle.setBorderBottom(BorderStyle.THIN);
        dataStyle.setBorderLeft(BorderStyle.THIN);
        dataStyle.setBorderRight(BorderStyle.THIN);
        dataStyle.setTopBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        dataStyle.setBottomBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        dataStyle.setLeftBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        dataStyle.setRightBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        dataStyle.setWrapText(false);

        CellStyle centeredStyle = workbook.createCellStyle();
        centeredStyle.cloneStyleFrom(dataStyle);
        centeredStyle.setAlignment(HorizontalAlignment.CENTER);

        CellStyle numericStyle = workbook.createCellStyle();
        numericStyle.cloneStyleFrom(dataStyle);
        numericStyle.setAlignment(HorizontalAlignment.CENTER);
        DataFormat fmt = workbook.createDataFormat();
        numericStyle.setDataFormat(fmt.getFormat("0.00"));

        CellStyle percentStyle = workbook.createCellStyle();
        percentStyle.cloneStyleFrom(numericStyle);
        percentStyle.setDataFormat(fmt.getFormat("0.00"));

        CellStyle passStyle = workbook.createCellStyle();
        passStyle.cloneStyleFrom(centeredStyle);
        Font passFont = workbook.createFont();
        passFont.setColor(IndexedColors.DARK_GREEN.getIndex());
        passFont.setBold(true);
        passStyle.setFont(passFont);

        CellStyle failStyle = workbook.createCellStyle();
        failStyle.cloneStyleFrom(centeredStyle);
        Font failFont = workbook.createFont();
        failFont.setColor(IndexedColors.RED.getIndex());
        failFont.setBold(true);
        failStyle.setFont(failFont);

        String[] headers = {
                "Sl No", "Student Name", "USN", "Semester", "Department", "College Code",
                "Academic Year", "Email", "Lateral Entry", "Total Marks", "Percentage (%)",
                "SGPA", "CGPA", "Result", "Rank", "Backlogs", "Subject-wise Marks"
        };

        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(26);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        for (int i = 0; i < students.size(); i++) {
            Student s = students.get(i);
            Row row = sheet.createRow(rowIdx++);
            row.setHeightInPoints(18);

            // Compute total marks and subject-wise string
            int totalMarks = 0;
            int subjectCountForTotal = 0;
            StringBuilder subjectWise = new StringBuilder();
            if (s.getResults() != null && !s.getResults().isEmpty()) {
                for (int j = 0; j < s.getResults().size(); j++) {
                    SubjectResult sr = s.getResults().get(j);
                    if (sr.getMarks() != null) {
                        totalMarks += sr.getMarks();
                        subjectCountForTotal++;
                    }
                    if (subjectWise.length() > 0) subjectWise.append(", ");
                    subjectWise.append(sr.getSubject() != null ? sr.getSubject() : (sr.getCode() != null ? sr.getCode() : "Sub" + (j + 1)));
                    subjectWise.append(": ");
                    subjectWise.append(sr.getMarks() != null ? sr.getMarks() : "--");
                    if (sr.getGrade() != null) subjectWise.append(" (" + sr.getGrade() + ")");
                }
            }

            int col = 0;
            Cell c0 = row.createCell(col++);
            c0.setCellValue(i + 1);
            c0.setCellStyle(centeredStyle);

            Cell c1 = row.createCell(col++);
            c1.setCellValue(s.getName() != null ? s.getName() : "--");
            c1.setCellStyle(dataStyle);

            Cell c2 = row.createCell(col++);
            c2.setCellValue(s.getUsn() != null ? s.getUsn() : "--");
            c2.setCellStyle(centeredStyle);

            Cell c3 = row.createCell(col++);
            c3.setCellValue(s.getSemester() != null ? s.getSemester() : "--");
            c3.setCellStyle(centeredStyle);

            Cell c4 = row.createCell(col++);
            c4.setCellValue(s.getBranch() != null ? s.getBranch() : "--");
            c4.setCellStyle(centeredStyle);

            Cell c5 = row.createCell(col++);
            c5.setCellValue(s.getCollegeCode() != null ? s.getCollegeCode() : "--");
            c5.setCellStyle(centeredStyle);

            Cell c6 = row.createCell(col++);
            c6.setCellValue(s.getAcademicYear() != null ? s.getAcademicYear() : "--");
            c6.setCellStyle(centeredStyle);

            Cell c7 = row.createCell(col++);
            c7.setCellValue(s.getEmail() != null ? s.getEmail() : "--");
            c7.setCellStyle(dataStyle);

            Cell c8 = row.createCell(col++);
            boolean isLateral = s.getLateralEntry() != null ? s.getLateralEntry() : false;
            c8.setCellValue(isLateral ? "Yes" : "No");
            c8.setCellStyle(centeredStyle);

            Cell c9 = row.createCell(col++);
            c9.setCellValue(totalMarks);
            c9.setCellStyle(centeredStyle);

            Cell c10 = row.createCell(col++);
            if (s.getPercentage() != null) {
                c10.setCellValue(s.getPercentage());
                c10.setCellStyle(percentStyle);
            } else {
                c10.setCellValue("--");
                c10.setCellStyle(centeredStyle);
            }

            Cell c11 = row.createCell(col++);
            if (s.getSgpa() != null) {
                c11.setCellValue(s.getSgpa());
                c11.setCellStyle(numericStyle);
            } else {
                c11.setCellValue("--");
                c11.setCellStyle(centeredStyle);
            }

            Cell c12 = row.createCell(col++);
            if (s.getCgpa() != null) {
                c12.setCellValue(s.getCgpa());
                c12.setCellStyle(numericStyle);
            } else {
                c12.setCellValue("--");
                c12.setCellStyle(centeredStyle);
            }

            Cell c13 = row.createCell(col++);
            String result = s.getResult() != null ? s.getResult().toUpperCase() : "--";
            c13.setCellValue(result);
            if ("PASS".equalsIgnoreCase(result)) c13.setCellStyle(passStyle);
            else if ("FAIL".equalsIgnoreCase(result)) c13.setCellStyle(failStyle);
            else c13.setCellStyle(centeredStyle);

            Cell c14 = row.createCell(col++);
            if (s.getstudentRank() != null) {
                c14.setCellValue(s.getstudentRank());
                c14.setCellStyle(centeredStyle);
            } else {
                c14.setCellValue("--");
                c14.setCellStyle(centeredStyle);
            }

            Cell c15 = row.createCell(col++);
            c15.setCellValue(s.getBacklog() != null ? s.getBacklog() : 0);
            c15.setCellStyle(centeredStyle);

            Cell c16 = row.createCell(col++);
            c16.setCellValue(subjectWise.length() > 0 ? subjectWise.toString() : "--");
            c16.setCellStyle(dataStyle);
        }

        // Auto-size and set widths
        int[] widths = { 2200, 6000, 4200, 3000, 3800, 3600, 3600, 7000, 3600, 3000, 3600, 2500, 2500, 2800, 2200, 2400, 12000 };
        for (int i = 0; i < headers.length; i++) {
            if (i < widths.length) sheet.setColumnWidth(i, widths[i]);
            else sheet.autoSizeColumn(i);
        }
        sheet.setDefaultRowHeightInPoints(16);
        // Alternate row shading could be added but keeping simple with borders

        // Print setup
        sheet.getPrintSetup().setLandscape(true);
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
        sheet.setAutobreaks(true);
    }

    private void createSubjectDetailsSheet(XSSFWorkbook workbook, List<Student> students) {
        XSSFSheet sheet = workbook.createSheet("Subject Details");
        sheet.createFreezePane(0, 1);

        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerFont.setFontHeightInPoints((short) 11);
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);
        headerStyle.setWrapText(true);

        CellStyle dataStyle = workbook.createCellStyle();
        dataStyle.setBorderTop(BorderStyle.THIN);
        dataStyle.setBorderBottom(BorderStyle.THIN);
        dataStyle.setBorderLeft(BorderStyle.THIN);
        dataStyle.setBorderRight(BorderStyle.THIN);
        dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle centeredStyle = workbook.createCellStyle();
        centeredStyle.cloneStyleFrom(dataStyle);
        centeredStyle.setAlignment(HorizontalAlignment.CENTER);

        String[] headers = { "Sl No", "Student Name", "USN", "Semester", "Department", "Subject Code", "Subject Name", "Credits", "Marks", "Grade", "Status" };
        Row headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(24);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        int slNo = 1;
        for (Student s : students) {
            if (s.getResults() == null || s.getResults().isEmpty()) {
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(slNo++);
                row.getCell(0).setCellStyle(centeredStyle);
                Cell c1 = row.createCell(col++); c1.setCellValue(s.getName() != null ? s.getName() : "--"); c1.setCellStyle(dataStyle);
                Cell c2 = row.createCell(col++); c2.setCellValue(s.getUsn() != null ? s.getUsn() : "--"); c2.setCellStyle(centeredStyle);
                Cell c3 = row.createCell(col++); c3.setCellValue(s.getSemester() != null ? s.getSemester() : "--"); c3.setCellStyle(centeredStyle);
                Cell c4 = row.createCell(col++); c4.setCellValue(s.getBranch() != null ? s.getBranch() : "--"); c4.setCellStyle(centeredStyle);
                for (int k = col; k < headers.length; k++) { Cell c = row.createCell(k); c.setCellValue("--"); c.setCellStyle(centeredStyle); }
                // apply style to first cell already
                for (int k = 1; k < row.getLastCellNum(); k++) {
                    if (row.getCell(k) != null && row.getCell(k).getCellStyle() == null) row.getCell(k).setCellStyle(centeredStyle);
                }
                continue;
            }
            for (SubjectResult sr : s.getResults()) {
                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                Cell c0 = row.createCell(col++); c0.setCellValue(slNo++); c0.setCellStyle(centeredStyle);
                Cell c1 = row.createCell(col++); c1.setCellValue(s.getName() != null ? s.getName() : "--"); c1.setCellStyle(dataStyle);
                Cell c2 = row.createCell(col++); c2.setCellValue(s.getUsn() != null ? s.getUsn() : "--"); c2.setCellStyle(centeredStyle);
                Cell c3 = row.createCell(col++); c3.setCellValue(sr.getSemester() != null ? sr.getSemester() : (s.getSemester() != null ? s.getSemester() : "--")); c3.setCellStyle(centeredStyle);
                Cell c4 = row.createCell(col++); c4.setCellValue(s.getBranch() != null ? s.getBranch() : "--"); c4.setCellStyle(centeredStyle);
                Cell c5 = row.createCell(col++); c5.setCellValue(sr.getCode() != null ? sr.getCode() : "--"); c5.setCellStyle(centeredStyle);
                Cell c6 = row.createCell(col++); c6.setCellValue(sr.getSubject() != null ? sr.getSubject() : "--"); c6.setCellStyle(dataStyle);
                Cell c7 = row.createCell(col++);
                if (sr.getCredits() != null) { c7.setCellValue(sr.getCredits()); c7.setCellStyle(centeredStyle); } else { c7.setCellValue("--"); c7.setCellStyle(centeredStyle); }
                Cell c8 = row.createCell(col++);
                if (sr.getMarks() != null) { c8.setCellValue(sr.getMarks()); c8.setCellStyle(centeredStyle); } else { c8.setCellValue("--"); c8.setCellStyle(centeredStyle); }
                Cell c9 = row.createCell(col++); c9.setCellValue(sr.getGrade() != null ? sr.getGrade() : "--"); c9.setCellStyle(centeredStyle);
                Cell c10 = row.createCell(col++);
                String status = sr.getStatus() != null ? sr.getStatus() : "--";
                c10.setCellValue(status);
                CellStyle statusStyle = centeredStyle;
                if ("PASS".equalsIgnoreCase(status)) {
                    CellStyle passStyle = workbook.createCellStyle();
                    passStyle.cloneStyleFrom(centeredStyle);
                    Font f = workbook.createFont(); f.setColor(IndexedColors.DARK_GREEN.getIndex()); f.setBold(true); passStyle.setFont(f);
                    statusStyle = passStyle;
                } else if ("FAIL".equalsIgnoreCase(status)) {
                    CellStyle failStyle = workbook.createCellStyle();
                    failStyle.cloneStyleFrom(centeredStyle);
                    Font f = workbook.createFont(); f.setColor(IndexedColors.RED.getIndex()); f.setBold(true); failStyle.setFont(f);
                    statusStyle = failStyle;
                }
                c10.setCellStyle(statusStyle);
            }
        }

        int[] widths = { 2000, 5500, 4000, 2800, 3500, 3500, 6000, 2500, 2500, 2500, 2500 };
        for (int i = 0; i < headers.length; i++) {
            sheet.setColumnWidth(i, widths[i]);
        }
        sheet.getPrintSetup().setLandscape(true);
        sheet.setFitToPage(true);
    }
}
