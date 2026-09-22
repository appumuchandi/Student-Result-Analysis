package com.Result_Analysis;

import com.Result_Analysis.Result_Analysis.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ExcelExportIntegrationTest {

    @Autowired StudentRepository studentRepository;
    @Autowired ExcelExportService excelExportService;
    @Autowired AnalyticsService analyticsService;

    @Test
    void excelServiceGeneratesValidXlsxWithHeaders() throws Exception {
        byte[] body = excelExportService.generateExcelForAll();
        assertThat(body).isNotEmpty();
        // Validate ZIP magic PK (xlsx is zip)
        org.junit.jupiter.api.Assertions.assertEquals((byte)0x50, body[0]); // P
        org.junit.jupiter.api.Assertions.assertEquals((byte)0x4B, body[1]); // K
        // Validate can be opened as Workbook
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(body))) {
            assertThat(wb.getNumberOfSheets()).isGreaterThanOrEqualTo(1);
            assertThat(wb.getSheetName(0)).isEqualTo("Student Results");
            var sheet = wb.getSheetAt(0);
            var header = sheet.getRow(0);
            assertThat((Object) header).isNotNull();
            boolean hasStudentName = false;
            boolean hasLateral = false;
            for (int i = 0; i < header.getLastCellNum(); i++) {
                String val = header.getCell(i).getStringCellValue();
                if ("Student Name".equals(val)) hasStudentName = true;
                if ("Lateral Entry".equals(val)) hasLateral = true;
            }
            assertThat(hasStudentName).isTrue();
            assertThat(hasLateral).isTrue();
            assertThat(sheet.getPaneInformation()).isNotNull(); // freeze pane
            assertThat(wb.getSheetName(1)).isEqualTo("Subject Details");
        }
    }

    @Test
    void excelExportWithSemesterFilterProducesValidXlsx() throws Exception {
        byte[] body = excelExportService.generateExcelFiltered("5th", null, null);
        assertThat(body).isNotEmpty();
        org.junit.jupiter.api.Assertions.assertEquals((byte)0x50, body[0]);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(body))) {
            assertThat(wb.getNumberOfSheets()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void excelServiceGeneratesValidBytesDirectlyWithLateralFlag() throws Exception {
        Student s = new Student();
        s.setName("Test Student");
        s.setUsn("1KL24EC001");
        s.setBranch("CSE");
        // Use semester 6th so that latestCompletedSemester = 5 (where actual marks are) – reflects real calculation logic
        s.setSemester("6th");
        s.setCollegeCode("1KL");
        s.setAcademicYear("2026");
        s.setEmail("test@example.com");
        s.setLateralEntry(true);
        s.setResult("PASS");
        s.setSgpa(8.5);
        s.setCgpa(8.5);
        s.setPercentage(85.0);
        s.setBacklog(0);
        SubjectResult sr = new SubjectResult();
        sr.setSemester("5th");
        sr.setCode("CS501");
        sr.setSubject("DSA");
        sr.setCredits(4);
        sr.setMarks(85);
        sr.setGrade("A");
        sr.setStatus("PASS");
        sr.setStudent(s);
        s.getResults().add(sr);
        SubjectResult sr2 = new SubjectResult();
        sr2.setSemester("5th");
        sr2.setCode("CS502");
        sr2.setSubject("DBMS");
        sr2.setCredits(4);
        sr2.setMarks(78);
        sr2.setGrade("B+");
        sr2.setStatus("PASS");
        sr2.setStudent(s);
        s.getResults().add(sr2);

        byte[] bytes = excelExportService.generateExcel(Arrays.asList(s));
        assertThat(bytes).isNotEmpty();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(2);
            var summary = wb.getSheet("Student Results");
            assertThat((Object) summary.getRow(1)).isNotNull();
            var row = summary.getRow(1);
            // After calculateStudentData, percentage for semester 6th with 5th results = (163/200)*100=81.5
            assertThat(row.getCell(10).getNumericCellValue()).isEqualTo(81.5);
            assertThat(row.getCell(8).getStringCellValue()).isEqualTo("Yes");
            // Ensure numeric cells are numeric
            assertThat(row.getCell(9).getNumericCellValue()).isEqualTo(163.0); // total 85+78
            // Lateral Entry formatted as Yes/No
            assertThat(row.getCell(8).getStringCellValue()).isEqualTo("Yes");
        }
    }

    @Test
    void lateralEntryFieldPersisted() {
        studentRepository.deleteAll();
        Student s = new Student();
        s.setUsn("TESTLAT001");
        s.setName("Lateral Tester");
        s.setBranch("ECE");
        s.setSemester("3rd");
        s.setCollegeCode("1KL");
        s.setLateralEntry(true);
        s.setResult("PASS");
        studentRepository.save(s);

        var found = studentRepository.findByUsnIgnoreCase("TESTLAT001");
        assertThat(found).isPresent();
        assertThat(found.get().getLateralEntry()).isTrue();

        // Regular student should default false
        Student s2 = new Student();
        s2.setUsn("REG001");
        s2.setName("Regular");
        s2.setBranch("ECE");
        s2.setSemester("3rd");
        s2.setCollegeCode("1KL");
        s2.setLateralEntry(false);
        s2.setResult("PASS");
        studentRepository.save(s2);

        var stats = analyticsService.getLateralEntryStats("3rd");
        assertThat(stats.getRegularCount()).isEqualTo(1);
        assertThat(stats.getLateralCount()).isEqualTo(1);
        assertThat(stats.getTotalCount()).isEqualTo(2);
    }

    @Test
    void analyticsServiceComputesSubjectStats() {
        studentRepository.deleteAll();
        Student s1 = new Student();
        s1.setUsn("ANA001");
        s1.setName("A");
        s1.setBranch("CSE");
        s1.setSemester("5th");
        s1.setCollegeCode("1KL");
        s1.setLateralEntry(false);
        SubjectResult r1 = new SubjectResult();
        r1.setSubject("Mathematics");
        r1.setCode("MA501");
        r1.setMarks(80);
        r1.setSemester("5th");
        r1.setStatus("PASS");
        r1.setCredits(4);
        r1.setStudent(s1);
        s1.getResults().add(r1);
        studentRepository.save(s1);

        Student s2 = new Student();
        s2.setUsn("ANA002");
        s2.setName("B");
        s2.setBranch("CSE");
        s2.setSemester("5th");
        s2.setCollegeCode("1KL");
        s2.setLateralEntry(true);
        SubjectResult r2 = new SubjectResult();
        r2.setSubject("Mathematics");
        r2.setCode("MA501");
        r2.setMarks(60);
        r2.setSemester("5th");
        r2.setStatus("PASS");
        r2.setCredits(4);
        r2.setStudent(s2);
        s2.getResults().add(r2);
        studentRepository.save(s2);

        List<SubjectStatsDto> stats = analyticsService.getSubjectStats("5th");
        assertThat(stats).isNotEmpty();
        var math = stats.stream().filter(x -> "Mathematics".equals(x.getSubject())).findFirst();
        assertThat(math).isPresent();
        assertThat(math.get().getAverageMarks()).isEqualTo(70.0);
        assertThat(math.get().getHighestMarks()).isEqualTo(80);
        assertThat(math.get().getLowestMarks()).isEqualTo(60);
        assertThat(math.get().getPassPercentage()).isEqualTo(100.0);
    }

    @Test
    void lateralEntryAggregatedStats() {
        studentRepository.deleteAll();
        // Create 2 regular 1 lateral across different semesters
        for (int i=0;i<2;i++){
            Student s=new Student(); s.setUsn("REG_AGG"+i); s.setName("Reg"+i); s.setBranch("CSE"); s.setSemester("3rd"); s.setCollegeCode("1KL"); s.setLateralEntry(false); s.setResult("PASS");
            studentRepository.save(s);
        }
        Student lat=new Student(); lat.setUsn("LAT_AGG"); lat.setName("Lat"); lat.setBranch("CSE"); lat.setSemester("3rd"); lat.setCollegeCode("1KL"); lat.setLateralEntry(true); lat.setResult("PASS");
        studentRepository.save(lat);

        var all = analyticsService.getLateralEntryStats(null);
        assertThat(all.getSemesterWise()).isNotNull();
        assertThat(all.getTotalCount()).isGreaterThanOrEqualTo(3);
        var sem3 = all.getSemesterWise().get("3");
        assertThat(sem3).isNotNull();
        assertThat(sem3.getRegularCount()).isEqualTo(2);
        assertThat(sem3.getLateralCount()).isEqualTo(1);
    }

    @Test
    void subjectStatsDynamicSubjectsNoHardcode() {
        studentRepository.deleteAll();
        Student s = new Student();
        s.setUsn("DYN001");
        s.setName("Dynamic");
        s.setBranch("ISE");
        s.setSemester("6th");
        s.setCollegeCode("1KL");
        s.setLateralEntry(false);
        String uniqueSubject = "QuantumComputing_" + System.currentTimeMillis();
        SubjectResult sr = new SubjectResult();
        sr.setSubject(uniqueSubject);
        sr.setCode("QC601");
        sr.setMarks(95);
        sr.setSemester("6th");
        sr.setStatus("PASS");
        sr.setCredits(3);
        sr.setStudent(s);
        s.getResults().add(sr);
        studentRepository.save(s);

        var stats = analyticsService.getSubjectStats("6th");
        boolean found = stats.stream().anyMatch(x -> uniqueSubject.equals(x.getSubject()));
        assertThat(found).isTrue();
    }
}
