package com.Result_Analysis.Result_Analysis;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

/**
 * Excel Import Service — handles both HOD direct .xls (Int/Ext/Tot/Re/GP) and generic fallback.
 * HOD format: Regular sheet, row4 main header with subject codes merged across 5 cols, row5 sub Int/Ext/Tot/Re/GP,
 * data from row6, 9 subjects (BEC401 etc. for 4th, BMATEC301 etc. for 3rd), each subject 5 cols.
 * Re preserved as P/A, Tot as marks, Int/Ext/GP stored in SubjectResult.
 */
@Service
public class ExcelImportService {

    private final StudentRepository studentRepository;
    private final StudentService studentService;
    private final ExcelUploadHistoryRepository uploadHistoryRepository;

    public ExcelImportService(StudentRepository studentRepository, StudentService studentService, ExcelUploadHistoryRepository uploadHistoryRepository) {
        this.studentRepository = studentRepository;
        this.studentService = studentService;
        this.uploadHistoryRepository = uploadHistoryRepository;
    }

    private static class SubjectGroup {
        String code; String name; int intCol, extCol, totCol, reCol, gpCol;
        SubjectGroup(String code, String name, int intCol, int extCol, int totCol, int reCol, int gpCol) {
            this.code=code; this.name=name; this.intCol=intCol; this.extCol=extCol; this.totCol=totCol; this.reCol=reCol; this.gpCol=gpCol;
        }
    }

    private boolean isHodFormat(Sheet sheet) {
        if (sheet==null || sheet.getLastRowNum()<6) return false;
        Row r4 = sheet.getRow(4); Row r5 = sheet.getRow(5);
        if (r4==null || r5==null) return false;
        String c0 = getCellString(r4.getCell(0)).trim().toLowerCase();
        String c1 = getCellString(r4.getCell(1)).trim().toLowerCase();
        String c2 = getCellString(r4.getCell(2)).trim().toLowerCase();
        String s3 = getCellString(r5.getCell(3)).trim().toLowerCase();
        String s4 = getCellString(r5.getCell(4)).trim().toLowerCase();
        String s5 = getCellString(r5.getCell(5)).trim().toLowerCase();
        return c0.contains("r. no") && c1.contains("usn") && c2.contains("student name") && s3.equals("int") && s4.equals("ext") && s5.equals("tot");
    }

    private List<SubjectGroup> extractHodSubjectGroups(Sheet sheet) {
        List<SubjectGroup> groups=new ArrayList<>();
        Row mainRow = sheet.getRow(4);
        if (mainRow==null) return groups;
        for(int col=3; col<=43; col+=5){
            String main = getCellString(mainRow.getCell(col)).trim();
            if(isBlank(main)) continue;
            if(main.equalsIgnoreCase("TOTAL MARKS") || main.equalsIgnoreCase("%") || main.equalsIgnoreCase("No.Of Sub Fails")) break;
            String code=main, name=main;
            if(main.contains("(")){
                String[] parts=main.split("\\(",2);
                code=parts[0].trim(); String inside=parts[1].replace(")","").trim(); name=inside.isEmpty()?code:inside;
            } else {
                code=main.replaceAll("[^A-Za-z0-9]",""); name=main;
            }
            code=code.replaceAll("\\s+","").toUpperCase();
            groups.add(new SubjectGroup(code, name, col, col+1, col+2, col+3, col+4));
        }
        return groups;
    }

    public ImportPreviewResponse preview(MultipartFile file, String branch, String semester,
                                         String batch, String entryType, String collegeCode) throws Exception {

        ImportPreviewResponse resp = new ImportPreviewResponse();
        resp.setFileName(file.getOriginalFilename());
        resp.setBranch(branch); resp.setSemester(semester); resp.setBatch(batch); resp.setEntryType(entryType); resp.setCollegeCode(collegeCode);
        resp.setParserNote("HOD direct .xls supported: Regular sheet Int/Ext/Tot/Re/GP preserved, Re as P/A. Generic fallback also supported.");

        List<String> errors=new ArrayList<>(); List<String> warnings=new ArrayList<>();
        if(isBlank(branch)) errors.add("Department is required.");
        if(isBlank(semester)) errors.add("Semester is required.");
        if(isBlank(batch)) errors.add("Batch is required.");
        // Entry Type removed — lateral is detected per-student from file data if available, otherwise defaults to existing/false
        if(file==null||file.isEmpty()) errors.add("Excel file is required.");
        else {
            String fname=file.getOriginalFilename()!=null?file.getOriginalFilename().toLowerCase():"";
            if(!fname.endsWith(".xlsx")&&!fname.endsWith(".xls")) errors.add("Invalid file type. Please upload .xlsx (or .xls) only.");
        }

        try (InputStream is=file.getInputStream(); Workbook wb=WorkbookFactory.create(is)){
            List<String> sheetNames=new ArrayList<>();
            for(int i=0;i<wb.getNumberOfSheets();i++) sheetNames.add(wb.getSheetName(i));
            resp.setSheetNames(sheetNames);
            Sheet sheet=chooseSheet(wb);
            if(sheet==null){
                errors.add("No sheets found in workbook.");
                resp.setHeaders(Collections.emptyList()); resp.setPreviewRows(Collections.emptyList());
                resp.setTotalRows(0); resp.setStudentsDetected(0); resp.setSubjectsDetected(0);
                finalizePreview(resp,errors,warnings); return resp;
            }

            boolean isHod=isHodFormat(sheet);
            List<String> headers; List<Map<String,Object>> previewRows=new ArrayList<>();
            int totalRows=0, studentsDetected=0, subjectsDetected=0;
            Set<String> seenUsnInFile=new HashSet<>(); List<String> duplicateUsnsInFile=new ArrayList<>();
            int missingUsnCount=0, missingNameCount=0, invalidMarksCount=0;

            if(isHod){
                List<SubjectGroup> groups=extractHodSubjectGroups(sheet);
                subjectsDetected=groups.size();
                headers=new ArrayList<>(); headers.add("R. No."); headers.add("USN"); headers.add("Student Name");
                for(SubjectGroup g: groups) headers.add(g.code+" ("+g.name+") Tot");
                resp.setHeaders(headers);
                for(int r=6;r<=sheet.getLastRowNum();r++){
                    Row row=sheet.getRow(r);
                    if(row==null || isRowEmpty(row)) continue;
                    String usnVal=getCellString(row.getCell(1)).trim();
                    String nameVal=getCellString(row.getCell(2)).trim();
                    if(isBlank(usnVal) && isBlank(nameVal)) continue;
                    if(!isBlank(usnVal) && usnVal.length()<5) continue;
                    totalRows++;
                    Map<String,Object> rowMap=new LinkedHashMap<>();
                    rowMap.put("R. No.",getCellString(row.getCell(0)));
                    rowMap.put("USN",usnVal); rowMap.put("Student Name",nameVal);
                    for(SubjectGroup g: groups) rowMap.put(g.code+" ("+g.name+") Tot", getCellString(row.getCell(g.totCol)));
                    if(previewRows.size()<5) previewRows.add(rowMap);
                    if(isBlank(usnVal)) missingUsnCount++;
                    else {
                        String norm=usnVal.trim().toUpperCase();
                        if(!seenUsnInFile.add(norm) && !duplicateUsnsInFile.contains(norm)) duplicateUsnsInFile.add(norm);
                        studentsDetected++;
                    }
                    if(isBlank(nameVal)) missingNameCount++;
                    for(SubjectGroup g: groups){
                        String totStr=getCellString(row.getCell(g.totCol)).trim();
                        if(isBlank(totStr)) continue;
                        try{ int tot=parseMarks(totStr); if(tot<0||tot>100) invalidMarksCount++; }catch(NumberFormatException e){ invalidMarksCount++; }
                    }
                }
                resp.setTotalRows(totalRows); resp.setStudentsDetected(studentsDetected); resp.setPreviewRows(previewRows); resp.setSubjectsDetected(subjectsDetected);
                if(totalRows==0) errors.add("No data rows found in Regular sheet. Ensure HOD file contains student records starting at row 7.");
                if(subjectsDetected==0) warnings.add("No subject columns detected in HOD format.");
                if(missingUsnCount>0) warnings.add(missingUsnCount+" record(s) have missing USN.");
                if(missingNameCount>0) warnings.add(missingNameCount+" record(s) have missing student name.");
                if(!duplicateUsnsInFile.isEmpty()) warnings.add(duplicateUsnsInFile.size()+" duplicate USN(s) within file: "+String.join(", ",duplicateUsnsInFile));
                if(invalidMarksCount>0) warnings.add(invalidMarksCount+" subject Tot values have invalid marks (0-100).");
                // no DB check in preview for HOD to keep stable
            } else {
                int headerRowNum=findHeaderRow(sheet);
                if(headerRowNum<0){
                    errors.add("Could not find header row. Ensure first row contains column names like USN, Name.");
                    resp.setHeaders(Collections.emptyList()); resp.setPreviewRows(Collections.emptyList());
                    resp.setTotalRows(0); resp.setStudentsDetected(0); resp.setSubjectsDetected(0);
                    finalizePreview(resp,errors,warnings); return resp;
                }
                Row headerRow=sheet.getRow(headerRowNum);
                headers=extractHeaders(headerRow);
                resp.setHeaders(headers);
                if(headers.isEmpty()) errors.add("Header row is empty.");
                Map<String,Integer> colIndex=resolveHeaderIndices(headers);
                boolean hasUsn=colIndex.containsKey("usn"); boolean hasName=colIndex.containsKey("name");
                if(!hasUsn) warnings.add("USN column not detected. Expected header like 'USN', 'University Seat Number'. Preview will show raw data; import will require USN.");
                if(!hasName) warnings.add("Name column not detected. Expected header like 'Name' or 'Student Name'.");
                Integer usnIdx=colIndex.get("usn");
                List<Integer> subjectIndices=detectSubjectColumns(headers,colIndex);
                resp.setSubjectsDetected(subjectIndices.size());
                for(int r=headerRowNum+1;r<=sheet.getLastRowNum();r++){
                    Row row=sheet.getRow(r);
                    if(row==null||isRowEmpty(row)) continue;
                    totalRows++;
                    Map<String,Object> rowMap=new LinkedHashMap<>();
                    for(int c=0;c<headers.size();c++) rowMap.put(headers.get(c), getCellString(row.getCell(c)));
                    if(previewRows.size()<5) previewRows.add(rowMap);
                    String usnVal=usnIdx!=null?getCellString(row.getCell(usnIdx)):"";
                    String nameVal=colIndex.containsKey("name")?getCellString(row.getCell(colIndex.get("name"))):"";
                    if(isBlank(usnVal)) missingUsnCount++;
                    else {
                        String norm=usnVal.trim().toUpperCase();
                        if(!seenUsnInFile.add(norm) && !duplicateUsnsInFile.contains(norm)) duplicateUsnsInFile.add(norm);
                        studentsDetected++;
                    }
                    if(isBlank(nameVal)) missingNameCount++;
                    for(Integer sIdx: subjectIndices){
                        String marksStr=getCellString(row.getCell(sIdx)).trim();
                        if(isBlank(marksStr)) continue;
                        try{ int m=parseMarks(marksStr); if(m<0||m>100) invalidMarksCount++; }catch(Exception e){ invalidMarksCount++; }
                    }
                }
                resp.setTotalRows(totalRows); resp.setStudentsDetected(studentsDetected); resp.setPreviewRows(previewRows);
                if(totalRows==0) errors.add("No data rows found below header.");
                if(subjectIndices.isEmpty()) warnings.add("No subject columns detected.");
                if(missingUsnCount>0) warnings.add(missingUsnCount+" record(s) have missing USN.");
                if(missingNameCount>0) warnings.add(missingNameCount+" record(s) have missing student name.");
                if(!duplicateUsnsInFile.isEmpty()) warnings.add(duplicateUsnsInFile.size()+" duplicate USN(s) within file: "+String.join(", ",duplicateUsnsInFile));
                if(invalidMarksCount>0) warnings.add(invalidMarksCount+" record(s) have invalid marks (must be 0-100 integer).");
                if(hasUsn && totalRows>0){
                    long dupVsDb=0; Set<String> checked=new HashSet<>();
                    for(int r=headerRowNum+1;r<=sheet.getLastRowNum();r++){
                        Row row=sheet.getRow(r); if(row==null||isRowEmpty(row)) continue;
                        String usnVal=getCellString(row.getCell(usnIdx)).trim().toUpperCase();
                        if(isBlank(usnVal)||!checked.add(usnVal)) continue;
                        if(studentRepository.findByUsnIgnoreCase(usnVal).isPresent()) dupVsDb++;
                    }
                    if(dupVsDb>0) warnings.add(dupVsDb+" USN(s) already exist in database and will be skipped on import.");
                }
            }
            finalizePreview(resp,errors,warnings);
            return resp;
        } catch(Exception e){
            errors.add("Failed to read Excel: "+e.getMessage());
            e.printStackTrace();
            finalizePreview(resp,errors,warnings);
            return resp;
        }
    }

    private void finalizePreview(ImportPreviewResponse resp, List<String> errors, List<String> warnings){
        resp.setValidationErrors(errors); resp.setValidationWarnings(warnings); resp.setHasErrors(!errors.isEmpty());
    }

    @Transactional
    public ImportResultResponse importConfirm(MultipartFile file, String branch, String semester,
                                              String batch, String entryType, String collegeCode) throws Exception {
        // Overload for backward compat (no uploadedBy) — delegates to main with anonymous
        return importConfirm(file, branch, semester, batch, entryType, collegeCode, "unknown");
    }

    @Transactional
    public ImportResultResponse importConfirm(MultipartFile file, String branch, String semester,
                                              String batch, String entryType, String collegeCode, String uploadedBy) throws Exception {
        ImportResultResponse result=new ImportResultResponse();
        result.setBranch(branch); result.setSemester(semester); result.setBatch(batch); result.setEntryType(entryType); result.setCollegeCode(collegeCode);
        List<String> errors=new ArrayList<>();
        if(isBlank(branch)) errors.add("Department is required.");
        if(isBlank(semester)) errors.add("Semester is required.");
        if(isBlank(batch)) errors.add("Batch is required.");
        // Entry Type removed — lateral is detected per-student from file data if available, otherwise preserved/default
        if(file==null||file.isEmpty()) errors.add("Excel file is required.");
        else {
            String fname=file.getOriginalFilename()!=null?file.getOriginalFilename().toLowerCase():"";
            if(!fname.endsWith(".xlsx")&&!fname.endsWith(".xls")) errors.add("Invalid file type. Please upload .xlsx (or .xls) only.");
        }
        if(!errors.isEmpty()){
            result.setSuccess(false); result.setMessage("Validation failed. No records were imported."); result.setErrors(errors);
            result.setStudentsImported(0); result.setSubjectResultsImported(0); return result;
        }
        // Lateral detection: HOD sheets contain both Regular and Lateral in same file without explicit marker.
        // We preserve existing student's lateral value if updating, otherwise default to Regular (false).
        // If workbook has an explicit lateral column, it would be detected here via header "lateral" — not present in HOD, so defaults.
        // See report limitation.
        int studentsImported=0, subjectResultsImported=0, studentsUpdated=0;
        try (InputStream is=file.getInputStream(); Workbook wb=WorkbookFactory.create(is)){
            Sheet sheet=chooseSheet(wb);
            if(sheet==null) throw new RuntimeException("No sheets found in workbook.");
            boolean isHod=isHodFormat(sheet);
            Set<String> seenUsnInFile=new HashSet<>();
            List<Student> toSaveNew=new ArrayList<>();

            if(isHod){
                List<SubjectGroup> groups=extractHodSubjectGroups(sheet);
                if(groups.isEmpty()) throw new RuntimeException("No subject columns detected in HOD format. Ensure Regular sheet has subjects like BEC401 with Int/Ext/Tot/Re/GP.");
                for(int r=6;r<=sheet.getLastRowNum();r++){
                    Row row=sheet.getRow(r);
                    if(row==null||isRowEmpty(row)) continue;
                    String usnVal=getCellString(row.getCell(1)).trim().toUpperCase();
                    String nameVal=getCellString(row.getCell(2)).trim();
                    if(isBlank(usnVal)&&isBlank(nameVal)) continue;
                    if(isBlank(usnVal)){ errors.add("Row "+(r+1)+": Missing USN - skipped."); continue; }
                    if(isBlank(nameVal)){ errors.add("Row "+(r+1)+" (USN "+usnVal+"): Missing student name - skipped."); continue; }
                    String normUsn=usnVal.toUpperCase();
                    if(!seenUsnInFile.add(normUsn)){ errors.add("Row "+(r+1)+" (USN "+normUsn+"): Duplicate USN within file - skipped."); continue; }
                    Optional<Student> existingOpt=studentRepository.findByUsnIgnoreCase(normUsn);
                    Student student; boolean isNew;
                    // Lateral detection per student: if workbook has lateral column, use it; else preserve existing or default Regular
                    // HOD sheets contain both Regular and Lateral in same file without explicit marker → keep existing or false
                    Boolean lateralForRow = detectLateralFromRow(row, null, normUsn, existingOpt.orElse(null));
                    if(existingOpt.isPresent()){
                        student=existingOpt.get(); student.getResults().size(); isNew=false;
                        if(isBlank(student.getBranch())) student.setBranch(branch);
                        if(isBlank(student.getAcademicYear())) student.setAcademicYear(batch);
                        if(isBlank(student.getCollegeCode())&&!isBlank(collegeCode)) student.setCollegeCode(collegeCode.trim());
                        if(student.getLateralEntry()==null && lateralForRow!=null) student.setLateralEntry(lateralForRow);
                        else if(student.getLateralEntry()==null) student.setLateralEntry(false);
                    } else {
                        student=new Student(); student.setUsn(normUsn); student.setName(nameVal);
                        student.setBranch(branch); student.setSemester(semester); student.setAcademicYear(batch);
                        student.setCollegeCode(!isBlank(collegeCode)?collegeCode.trim():null);
                        student.setEmail(normUsn.toLowerCase()+"@example.com");
                        student.setLateralEntry(lateralForRow!=null ? lateralForRow : false);
                        isNew=true;
                    }
                    List<SubjectResult> newResults=new ArrayList<>();
                    for(SubjectGroup g: groups){
                        String intStr=getCellString(row.getCell(g.intCol)).trim();
                        String extStr=getCellString(row.getCell(g.extCol)).trim();
                        String totStr=getCellString(row.getCell(g.totCol)).trim();
                        String reStr=getCellString(row.getCell(g.reCol)).trim();
                        String gpStr=getCellString(row.getCell(g.gpCol)).trim();
                        if(isBlank(totStr)&&isBlank(intStr)&&isBlank(extStr)) continue;
                        Integer tot=null, intM=null, extM=null, gp=null;
                        try{
                            if(!isBlank(totStr)) tot=parseMarks(totStr);
                            if(!isBlank(intStr)) intM=parseMarks(intStr);
                            if(!isBlank(extStr)) extM=parseMarks(extStr);
                            if(!isBlank(gpStr)) gp=parseMarks(gpStr);
                        }catch(NumberFormatException e){
                            errors.add("Row "+(r+1)+" (USN "+normUsn+"): Invalid numeric for subject '"+g.code+"' Int="+intStr+" Ext="+extStr+" Tot="+totStr+" GP="+gpStr+" - skipped subject."); continue;
                        }
                        if(tot!=null && (tot<0||tot>100)){ errors.add("Row "+(r+1)+" (USN "+normUsn+"): Tot "+tot+" out of range 0-100 for subject '"+g.code+"' - skipped subject."); continue; }
                        if(tot==null){ errors.add("Row "+(r+1)+" (USN "+normUsn+"): Missing Tot for subject '"+g.code+"' - skipped subject."); continue; }
                        boolean alreadyExists=false;
                        if(!isNew){
                            for(SubjectResult ex: student.getResults()){
                                if(ex.getCode()!=null && ex.getCode().equalsIgnoreCase(g.code) && ex.getSemester()!=null && ex.getSemester().equalsIgnoreCase(semester)){ alreadyExists=true; break; }
                            }
                        } else {
                            for(SubjectResult nr: newResults){ if(nr.getCode().equalsIgnoreCase(g.code) && nr.getSemester().equalsIgnoreCase(semester)){ alreadyExists=true; break; } }
                        }
                        if(alreadyExists){ errors.add("Row "+(r+1)+" (USN "+normUsn+"): Duplicate SubjectResult for "+g.code+" semester "+semester+" - skipped subject."); continue; }
                        SubjectResult sr=new SubjectResult();
                        sr.setCode(g.code); sr.setSubject(g.name); sr.setCredits(4); sr.setMarks(tot);
                        sr.setInternalMarks(intM); sr.setExternalMarks(extM); sr.setRe(!isBlank(reStr)?reStr.trim().toUpperCase():null); sr.setGradePoint(gp);
                        sr.setSemester(semester); sr.setStudent(student);
                        newResults.add(sr);
                    }
                    if(newResults.isEmpty()){ errors.add("Row "+(r+1)+" (USN "+normUsn+"): No valid subject marks found - skipped student."); continue; }
                    for(SubjectResult nr: newResults) student.getResults().add(nr);
                    studentService.calculateStudentData(student);
                    if(isNew){ toSaveNew.add(student); studentsImported++; subjectResultsImported+=newResults.size(); }
                    else { studentRepository.save(student); studentsUpdated++; subjectResultsImported+=newResults.size(); studentsImported++; }
                }
                if(studentsImported==0 && studentsUpdated==0){
                    result.setSuccess(false); result.setMessage("No valid records to import. All rows had errors. No records were added."); result.setErrors(errors);
                    result.setStudentsImported(0); result.setSubjectResultsImported(0); return result;
                }
                for(Student s: toSaveNew) studentRepository.save(s);
                studentRepository.flush();
                result.setSuccess(true); result.setMessage("Import successful. New: "+toSaveNew.size()+", Updated: "+studentsUpdated);
                result.setStudentsImported(studentsImported); result.setSubjectResultsImported(subjectResultsImported);
                result.setErrors(errors.isEmpty()?Collections.emptyList():errors);
                // Record upload history — MySQL source of truth, not localStorage
                try {
                    ExcelUploadHistory h = new ExcelUploadHistory();
                    h.setUploadedBy(uploadedBy != null && !uploadedBy.trim().isEmpty() ? uploadedBy : "unknown");
                    h.setUploadedAt(java.time.LocalDateTime.now());
                    h.setFileName(file.getOriginalFilename());
                    h.setDepartment(branch);
                    h.setBatch(batch);
                    h.setSemester(semester);
                    h.setStudentsImported(studentsImported);
                    h.setSubjectResultsImported(subjectResultsImported);
                    h.setStatus("SUCCESS");
                    uploadHistoryRepository.save(h);
                } catch(Exception ex){ ex.printStackTrace(); }
                return result;
            } else {
                int headerRowNum=findHeaderRow(sheet);
                if(headerRowNum<0) throw new RuntimeException("Header row not found.");
                Row headerRow=sheet.getRow(headerRowNum);
                List<String> headers=extractHeaders(headerRow);
                Map<String,Integer> colIndex=resolveHeaderIndices(headers);
                List<Integer> subjectIndices=detectSubjectColumns(headers,colIndex);
                if(!colIndex.containsKey("usn")) throw new RuntimeException("USN column not found. Required header: USN");
                if(!colIndex.containsKey("name")) throw new RuntimeException("Name column not found. Required header: Name");
                if(subjectIndices.isEmpty()) throw new RuntimeException("No subject columns detected.");
                Integer usnIdx=colIndex.get("usn"); Integer nameIdx=colIndex.get("name");
                Integer branchIdx=colIndex.get("branch"); Integer emailIdx=colIndex.get("email");
                Integer semIdx=colIndex.get("semester"); Integer acadYearIdx=colIndex.containsKey("academic year")?colIndex.get("academic year"):colIndex.get("batch");
                Integer collegeCodeIdx=colIndex.get("college code");
                List<Student> toSaveNewFallback=new ArrayList<>();
                for(int r=headerRowNum+1;r<=sheet.getLastRowNum();r++){
                    Row row=sheet.getRow(r);
                    if(row==null||isRowEmpty(row)) continue;
                    String usnVal=getCellString(row.getCell(usnIdx)).trim().toUpperCase();
                    String nameVal=getCellString(row.getCell(nameIdx)).trim();
                    if(isBlank(usnVal)){ errors.add("Row "+(r+1)+": Missing USN - skipped."); continue; }
                    if(isBlank(nameVal)){ errors.add("Row "+(r+1)+" (USN "+usnVal+"): Missing student name - skipped."); continue; }
                    String normUsn=usnVal.toUpperCase();
                    if(!seenUsnInFile.add(normUsn)){ errors.add("Row "+(r+1)+" (USN "+normUsn+"): Duplicate USN within file - skipped."); continue; }
                    Optional<Student> existingOpt=studentRepository.findByUsnIgnoreCase(normUsn);
                    Student student; boolean isNew;
                    Boolean lateralForRowGeneric = detectLateralFromRow(row, colIndex, normUsn, existingOpt.orElse(null));
                    if(existingOpt.isPresent()){
                        student=existingOpt.get(); student.getResults().size(); isNew=false;
                        if(student.getLateralEntry()==null && lateralForRowGeneric!=null) student.setLateralEntry(lateralForRowGeneric);
                        else if(student.getLateralEntry()==null) student.setLateralEntry(false);
                    } else {
                        student=new Student(); student.setUsn(normUsn); student.setName(nameVal);
                        String branchVal=branchIdx!=null?getCellString(row.getCell(branchIdx)).trim():"";
                        student.setBranch(!isBlank(branchVal)?branchVal:branch);
                        String semVal=semIdx!=null?getCellString(row.getCell(semIdx)).trim():"";
                        student.setSemester(!isBlank(semVal)?semVal:semester);
                        String acadVal=acadYearIdx!=null?getCellString(row.getCell(acadYearIdx)).trim():"";
                        student.setAcademicYear(!isBlank(acadVal)?acadVal:batch);
                        String ccVal=collegeCodeIdx!=null?getCellString(row.getCell(collegeCodeIdx)).trim():"";
                        student.setCollegeCode(!isBlank(ccVal)?ccVal:(collegeCode!=null?collegeCode.trim():null));
                        String emailVal=emailIdx!=null?getCellString(row.getCell(emailIdx)).trim():"";
                        if(!isBlank(emailVal)&&emailVal.contains("@")) student.setEmail(emailVal); else student.setEmail(normUsn.toLowerCase()+"@example.com");
                        student.setLateralEntry(lateralForRowGeneric!=null ? lateralForRowGeneric : false);
                        isNew=true;
                    }
                    List<SubjectResult> newResults=new ArrayList<>();
                    for(Integer sIdx: subjectIndices){
                        String header=headers.get(sIdx);
                        String marksStr=getCellString(row.getCell(sIdx)).trim();
                        if(isBlank(marksStr)) continue;
                        int marks; try{ marks=parseMarks(marksStr); }catch(NumberFormatException e){
                            errors.add("Row "+(r+1)+" (USN "+normUsn+"): Invalid marks '"+marksStr+"' for subject '"+header+"' - skipped subject."); continue;
                        }
                        if(marks<0||marks>100){ errors.add("Row "+(r+1)+" (USN "+normUsn+"): Marks "+marks+" out of range 0-100 for subject '"+header+"' - skipped subject."); continue; }
                        boolean alreadyExists=false;
                        if(!isNew){
                            for(SubjectResult ex: student.getResults()){
                                if(ex.getCode()!=null&&ex.getCode().equalsIgnoreCase(header)&&ex.getSemester()!=null&&ex.getSemester().equalsIgnoreCase(student.getSemester())){ alreadyExists=true; break; }
                            }
                        }
                        if(alreadyExists){ errors.add("Row "+(r+1)+" (USN "+normUsn+"): Duplicate SubjectResult for "+header+" semester "+student.getSemester()+" - skipped subject."); continue; }
                        SubjectResult sr=new SubjectResult();
                        String code=header, subject=header;
                        if(header.contains(":")){ String[] parts=header.split(":",2); code=parts[0].trim(); subject=parts[1].trim(); if(isBlank(subject)) subject=code; }
                        sr.setCode(code); sr.setSubject(subject); sr.setCredits(4); sr.setMarks(marks); sr.setSemester(student.getSemester()); sr.setStudent(student);
                        newResults.add(sr);
                    }
                    if(newResults.isEmpty()){ errors.add("Row "+(r+1)+" (USN "+normUsn+"): No valid subject marks found - skipped student."); continue; }
                    for(SubjectResult nr: newResults) student.getResults().add(nr);
                    studentService.calculateStudentData(student);
                    if(isNew){ toSaveNewFallback.add(student); studentsImported++; subjectResultsImported+=newResults.size(); }
                    else { studentRepository.save(student); studentsUpdated++; studentsImported++; subjectResultsImported+=newResults.size(); }
                }
                if(studentsImported==0){
                    result.setSuccess(false); result.setMessage("No valid records to import. All rows had errors. No records were added."); result.setErrors(errors);
                    result.setStudentsImported(0); result.setSubjectResultsImported(0); return result;
                }
                for(Student s: toSaveNewFallback) studentRepository.save(s);
                studentRepository.flush();
                result.setSuccess(true); result.setMessage("Import successful. New: "+toSaveNewFallback.size()+", Updated: "+studentsUpdated);
                result.setStudentsImported(studentsImported); result.setSubjectResultsImported(subjectResultsImported);
                result.setErrors(errors.isEmpty()?Collections.emptyList():errors);
                try {
                    ExcelUploadHistory h = new ExcelUploadHistory();
                    h.setUploadedBy(uploadedBy != null && !uploadedBy.trim().isEmpty() ? uploadedBy : "unknown");
                    h.setUploadedAt(java.time.LocalDateTime.now());
                    h.setFileName(file.getOriginalFilename());
                    h.setDepartment(branch);
                    h.setBatch(batch);
                    h.setSemester(semester);
                    h.setStudentsImported(studentsImported);
                    h.setSubjectResultsImported(subjectResultsImported);
                    h.setStatus("SUCCESS");
                    uploadHistoryRepository.save(h);
                } catch(Exception ex){ ex.printStackTrace(); }
                return result;
            }
        } catch(Exception e){
            if(errors.isEmpty()) errors.add("Import failed: "+e.getMessage());
            result.setSuccess(false); result.setMessage("Import failed. No records were added because validation failed: "+e.getMessage()); result.setErrors(errors);
            result.setStudentsImported(0); result.setSubjectResultsImported(0);
            e.printStackTrace();
            if(studentsImported>0) throw new RuntimeException("Import failed, transaction rolled back: "+e.getMessage(),e);
            return result;
        }
    }

    private Sheet chooseSheet(Workbook wb){
        for(int i=0;i<wb.getNumberOfSheets();i++){
            String name=wb.getSheetName(i);
            if(name!=null && (name.equalsIgnoreCase("Regular")||name.equalsIgnoreCase("students"))) return wb.getSheetAt(i);
        }
        for(int i=0;i<wb.getNumberOfSheets();i++){
            Sheet s=wb.getSheetAt(i);
            if(s!=null && s.getPhysicalNumberOfRows()>0) return s;
        }
        return wb.getNumberOfSheets()>0?wb.getSheetAt(0):null;
    }

    private int findHeaderRow(Sheet sheet){
        for(int r=0;r<=Math.min(5,sheet.getLastRowNum());r++){
            Row row=sheet.getRow(r); if(row==null) continue;
            String rowText=""; for(Cell c: row) rowText+=getCellString(c).toLowerCase()+" ";
            if(rowText.contains("usn")||rowText.contains("name")||rowText.contains("seat")) return r;
        }
        for(int r=0;r<=Math.min(5,sheet.getLastRowNum());r++){
            Row row=sheet.getRow(r); if(row!=null && row.getPhysicalNumberOfCells()>=2) return r;
        }
        return -1;
    }

    private List<String> extractHeaders(Row headerRow){
        List<String> headers=new ArrayList<>();
        if(headerRow==null) return headers;
        int lastCell=headerRow.getLastCellNum();
        for(int c=0;c<lastCell;c++){
            Cell cell=headerRow.getCell(c);
            String val=getCellString(cell).trim();
            if(isBlank(val)) val="Column_"+(c+1);
            headers.add(val);
        }
        while(!headers.isEmpty() && headers.get(headers.size()-1).startsWith("Column_")) headers.remove(headers.size()-1);
        return headers;
    }

    private Map<String,Integer> resolveHeaderIndices(List<String> headers){
        Map<String,Integer> map=new HashMap<>();
        for(int i=0;i<headers.size();i++){
            String h=headers.get(i).trim().toLowerCase();
            if(h.equals("usn")||h.equals("usn no")||h.equals("usn number")||h.equals("university seat number")||h.equals("seat no")||h.equals("roll no")||h.equals("reg no")||h.equals("registration no")||h.contains("usn")){
                if(!map.containsKey("usn")) map.put("usn",i);
            }
            if(h.equals("name")||h.equals("student name")||h.equals("candidate name")||h.equals("full name")||(h.contains("name")&&!h.contains("subject"))){
                if(!map.containsKey("name")) map.put("name",i);
            }
            if(h.equals("branch")||h.equals("department")||h.equals("dept")) map.putIfAbsent("branch",i);
            if(h.equals("semester")||h.equals("sem")) map.putIfAbsent("semester",i);
            if(h.equals("academic year")||h.equals("academic_year")||h.equals("batch")||h.equals("year")) map.putIfAbsent("academic year",i);
            if(h.equals("batch")) map.putIfAbsent("batch",i);
            if(h.equals("email")||h.equals("e-mail")||h.equals("mail")) map.putIfAbsent("email",i);
            if(h.equals("college code")||h.equals("college_code")||h.equals("college")||h.equals("collegecode")) map.putIfAbsent("college code",i);
        }
        if(map.containsKey("academic year")&&!map.containsKey("batch")) map.put("batch",map.get("academic year"));
        if(map.containsKey("batch")&&!map.containsKey("academic year")) map.put("academic year",map.get("batch"));
        return map;
    }

    private List<Integer> detectSubjectColumns(List<String> headers, Map<String,Integer> fixedCols){
        Set<Integer> fixed=new HashSet<>(fixedCols.values());
        List<Integer> subjects=new ArrayList<>();
        for(int i=0;i<headers.size();i++){
            if(fixed.contains(i)) continue;
            String h=headers.get(i).trim().toLowerCase();
            if(h.equals("sl no")||h.equals("sl.no")||h.equals("s.no")||h.equals("serial no")||h.equals("sr no")||h.equals("id")) continue;
            if(h.isEmpty()) continue;
            subjects.add(i);
        }
        return subjects;
    }

    private String getCellString(Cell cell){
        if(cell==null) return "";
        switch(cell.getCellType()){
            case STRING: return cell.getStringCellValue()!=null?cell.getStringCellValue():"";
            case NUMERIC:
                if(DateUtil.isCellDateFormatted(cell)) return cell.getLocalDateTimeCellValue().toString();
                double d=cell.getNumericCellValue();
                if(d==Math.floor(d)) return String.valueOf((long)d);
                return String.valueOf(d);
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try{ return cell.getStringCellValue(); }catch(Exception e){ try{ return String.valueOf((long)cell.getNumericCellValue()); }catch(Exception ex){ return cell.getCellFormula(); } }
            case BLANK: return "";
            default: return "";
        }
    }

    private int parseMarks(String s){
        s=s.trim();
        if(s.contains("/")) s=s.split("/")[0].trim();
        if(s.contains(".")){
            double d=Double.parseDouble(s);
            return (int)Math.round(d);
        }
        return Integer.parseInt(s);
    }

    private boolean isBlank(String s){ return s==null||s.trim().isEmpty(); }

    private boolean isRowEmpty(Row row){
        if(row==null) return true;
        for(Cell c: row) if(!isBlank(getCellString(c))) return false;
        return true;
    }

    /**
     * Lateral detection per student.
     * HOD sheets contain both Regular and Lateral in same file without explicit marker.
     * We first check if workbook has an explicit lateral column (header contains "lateral" or "entry type").
     * If found, use that cell value (Lateral/Regular).
     * Otherwise, preserve existing student's lateral value if updating, else default to Regular (false).
     * We do NOT fabricate from USN pattern alone, as HOD data shows no reliable USN-based lateral marker.
     * Reports limitation via validationWarnings if needed.
     */
    private Boolean detectLateralFromRow(Row row, Map<String,Integer> colIndex, String usn, Student existing){
        if(existing!=null && existing.getLateralEntry()!=null) return existing.getLateralEntry();
        if(row!=null && colIndex!=null){
            for(Map.Entry<String,Integer> e: colIndex.entrySet()){
                String key=e.getKey().toLowerCase();
                if(key.contains("lateral")||key.contains("entry type")){
                    Integer idx=e.getValue();
                    String val=getCellString(row.getCell(idx)).trim().toLowerCase();
                    if(val.contains("lateral")) return true;
                    if(val.contains("regular")) return false;
                }
            }
        }
        // No explicit info — default to Regular (false) for new students, preserve existing otherwise
        // Could also check USN stable ID >=400 as heuristic for lateral (2KD25EC400), but HOD 3rd/4th have no such, so keep false
        return false;
    }
}
