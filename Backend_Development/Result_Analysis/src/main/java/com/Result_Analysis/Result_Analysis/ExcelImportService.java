package com.Result_Analysis.Result_Analysis;

import org.apache.poi.ss.usermodel.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
 *
 * UPDATE/SYNC semantics (18.x):
 * - USN is stable identity for Student
 * - USN + semester + subject code is identity for SubjectResult
 * - Existing student: compare incoming vs DB, update only changed fields
 * - Existing subject: compare marks/int/ext/re/gp, update if changed, count unchanged otherwise
 * - No duplicate SubjectResult creation, preserve previous semesters
 */
@Service
public class ExcelImportService {

    private final StudentRepository studentRepository;
    private final StudentService studentService;
    private final ExcelUploadHistoryRepository uploadHistoryRepository;
    private final UserRepository userRepository;
    private final CreditResolver creditResolver;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public ExcelImportService(StudentRepository studentRepository, StudentService studentService, ExcelUploadHistoryRepository uploadHistoryRepository, UserRepository userRepository, CreditResolver creditResolver) {
        this.studentRepository = studentRepository;
        this.studentService = studentService;
        this.uploadHistoryRepository = uploadHistoryRepository;
        this.userRepository = userRepository;
        this.creditResolver = creditResolver;
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
            // Sync counters
            int newStudents=0, existingStudents=0, studentsWithChanges=0, studentsAlreadyUpToDate=0;
            int newSubjectResults=0, subjectResultsToUpdate=0, subjectResultsAlreadyUpToDate=0;
            int invalidRows=0, duplicateRowsWithinFile=0;

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

                    // Invalid row detection
                    if(isBlank(usnVal) || isBlank(nameVal)){
                        invalidRows++;
                        if(isBlank(usnVal)) missingUsnCount++;
                        if(isBlank(nameVal)) missingNameCount++;
                        continue;
                    }
                    String norm=usnVal.trim().toUpperCase();
                    if(seenUsnInFile.contains(norm)){
                        if(!duplicateUsnsInFile.contains(norm)) duplicateUsnsInFile.add(norm);
                        duplicateRowsWithinFile++;
                        continue;
                    }
                    seenUsnInFile.add(norm);
                    studentsDetected++;

                    // Check subject marks validity for warnings
                    for(SubjectGroup g: groups){
                        String totStr=getCellString(row.getCell(g.totCol)).trim();
                        if(isBlank(totStr)) continue;
                        try{ int tot=parseMarks(totStr); if(tot<0||tot>100) invalidMarksCount++; }catch(NumberFormatException e){ invalidMarksCount++; }
                    }

                    // Sync detection vs DB
                    Optional<Student> existingOpt = studentRepository.findByUsnIgnoreCase(norm);
                    if(!existingOpt.isPresent()){
                        newStudents++;
                        // All subject results for new student are new
                        for(SubjectGroup g: groups){
                            String totStr=getCellString(row.getCell(g.totCol)).trim();
                            String intStr=getCellString(row.getCell(g.intCol)).trim();
                            String extStr=getCellString(row.getCell(g.extCol)).trim();
                            if(isBlank(totStr)&&isBlank(intStr)&&isBlank(extStr)) continue;
                            // Validate tot exists and is parsable
                            try{
                                Integer tot = isBlank(totStr)?null:parseMarks(totStr);
                                if(tot!=null) newSubjectResults++;
                            }catch(Exception e){}
                        }
                    } else {
                        existingStudents++;
                        Student existing = existingOpt.get();
                        existing.getResults().size(); // init
                        // Check student fields change - HOD has no email/phone columns, preserve existing
                        String incomingBranch = branch;
                        String incomingAcad = batch;
                        String incomingSem = semester;
                        String incomingCollege = isBlank(collegeCode)?null:collegeCode.trim();
                        String incomingEmail = null;
                        String incomingPhone = null;
                        Boolean incomingLateral = detectLateralFromRow(row, null, norm, existing);
                        boolean studentChanged = isStudentChanged(existing, nameVal, incomingBranch, incomingSem, incomingAcad, incomingCollege, incomingEmail, incomingPhone, incomingLateral);

                        int rowNew=0, rowUpd=0, rowUnchanged=0;
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
                            }catch(Exception e){ continue; }
                            if(tot==null) continue;
                            SubjectResult existingSr = findExistingSubject(existing, g.code, semester);
                            if(existingSr==null){
                                rowNew++;
                            } else {
                                String incomingRe = isBlank(reStr)?null:reStr.trim().toUpperCase();
                                if(isSubjectSame(existingSr, tot, intM, extM, incomingRe, gp, g.name)){
                                    rowUnchanged++;
                                } else {
                                    rowUpd++;
                                }
                            }
                        }
                        newSubjectResults += rowNew;
                        subjectResultsToUpdate += rowUpd;
                        subjectResultsAlreadyUpToDate += rowUnchanged;
                        if(studentChanged || rowNew>0 || rowUpd>0){
                            studentsWithChanges++;
                        } else {
                            studentsAlreadyUpToDate++;
                        }
                    }
                }
                resp.setTotalRows(totalRows); resp.setStudentsDetected(studentsDetected); resp.setPreviewRows(previewRows); resp.setSubjectsDetected(subjectsDetected);
                // Set sync counters
                resp.setNewStudents(newStudents); resp.setExistingStudents(existingStudents);
                resp.setStudentsWithChanges(studentsWithChanges); resp.setStudentsAlreadyUpToDate(studentsAlreadyUpToDate);
                resp.setNewSubjectResults(newSubjectResults); resp.setSubjectResultsToUpdate(subjectResultsToUpdate);
                resp.setSubjectResultsAlreadyUpToDate(subjectResultsAlreadyUpToDate);
                resp.setInvalidRows(invalidRows); resp.setDuplicateRowsWithinFile(duplicateRowsWithinFile);

                if(totalRows==0) errors.add("No data rows found in Regular sheet. Ensure HOD file contains student records starting at row 7.");
                if(subjectsDetected==0) warnings.add("No subject columns detected in HOD format.");
                if(missingUsnCount>0) warnings.add(missingUsnCount+" record(s) have missing USN.");
                if(missingNameCount>0) warnings.add(missingNameCount+" record(s) have missing student name.");
                if(!duplicateUsnsInFile.isEmpty()) warnings.add(duplicateUsnsInFile.size()+" duplicate USN(s) within file: "+String.join(", ",duplicateUsnsInFile));
                if(invalidMarksCount>0) warnings.add(invalidMarksCount+" subject Tot values have invalid marks (0-100).");
                if(invalidRows>0) warnings.add(invalidRows+" invalid row(s) will be skipped.");
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
                    if(isBlank(usnVal) || isBlank(nameVal)){
                        invalidRows++;
                        if(isBlank(usnVal)) missingUsnCount++;
                        if(isBlank(nameVal)) missingNameCount++;
                        continue;
                    }
                    String norm=usnVal.trim().toUpperCase();
                    if(seenUsnInFile.contains(norm)){
                        if(!duplicateUsnsInFile.contains(norm)) duplicateUsnsInFile.add(norm);
                        duplicateRowsWithinFile++;
                        continue;
                    }
                    seenUsnInFile.add(norm);
                    studentsDetected++;
                    for(Integer sIdx: subjectIndices){
                        String marksStr=getCellString(row.getCell(sIdx)).trim();
                        if(isBlank(marksStr)) continue;
                        try{ int m=parseMarks(marksStr); if(m<0||m>100) invalidMarksCount++; }catch(Exception e){ invalidMarksCount++; }
                    }
                    // Sync detection
                    Optional<Student> existingOpt = studentRepository.findByUsnIgnoreCase(norm);
                    if(!existingOpt.isPresent()){
                        newStudents++;
                        newSubjectResults += subjectIndices.size(); // approx, will filter blanks later
                        // More precise: count non-blank marks
                        int cnt=0;
                        for(Integer sIdx: subjectIndices){
                            String ms=getCellString(row.getCell(sIdx)).trim();
                            if(!isBlank(ms)){
                                try{ parseMarks(ms); cnt++; }catch(Exception e){}
                            }
                        }
                        // Adjust: we added size, now correct
                        newSubjectResults = newSubjectResults - subjectIndices.size() + cnt;
                    } else {
                        existingStudents++;
                        Student existing = existingOpt.get();
                        existing.getResults().size();
                        String branchVal = colIndex.containsKey("branch")?getCellString(row.getCell(colIndex.get("branch"))).trim():branch;
                        if(isBlank(branchVal)) branchVal = branch;
                        String semVal = colIndex.containsKey("semester")?getCellString(row.getCell(colIndex.get("semester"))).trim():semester;
                        if(isBlank(semVal)) semVal = semester;
                        String acadVal = colIndex.containsKey("academic year")?getCellString(row.getCell(colIndex.get("academic year"))).trim():batch;
                        if(isBlank(acadVal)) acadVal = batch;
                        String ccVal = colIndex.containsKey("college code")?getCellString(row.getCell(colIndex.get("college code"))).trim():collegeCode;
                        String emailVal = null;
                        if(colIndex.containsKey("email")){
                            String ev = getCellString(row.getCell(colIndex.get("email"))).trim();
                            if(!isBlank(ev)) emailVal = ev;
                        }
                        String phoneVal = null;
                        if(colIndex.containsKey("phone")){
                            String pv = getCellString(row.getCell(colIndex.get("phone"))).trim();
                            if(!isBlank(pv)) phoneVal = pv;
                        }
                        Boolean lat = detectLateralFromRow(row, colIndex, norm, existing);
                        boolean studentChanged = isStudentChanged(existing, nameVal, branchVal, semVal, acadVal, ccVal, emailVal, phoneVal, lat);
                        int rowNew=0,rowUpd=0,rowUnchanged=0;
                        for(Integer sIdx: subjectIndices){
                            String header=headers.get(sIdx);
                            String marksStr=getCellString(row.getCell(sIdx)).trim();
                            if(isBlank(marksStr)) continue;
                            int marks;
                            try{ marks=parseMarks(marksStr); }catch(Exception e){ continue; }
                            String code=header.contains(":")?header.split(":",2)[0].trim():header;
                            SubjectResult existingSr = findExistingSubject(existing, code, semVal);
                            if(existingSr==null) rowNew++;
                            else {
                                if(existingSr.getMarks()!=null && existingSr.getMarks()==marks) rowUnchanged++;
                                else rowUpd++;
                            }
                        }
                        newSubjectResults += rowNew;
                        subjectResultsToUpdate += rowUpd;
                        subjectResultsAlreadyUpToDate += rowUnchanged;
                        if(studentChanged || rowNew>0 || rowUpd>0) studentsWithChanges++;
                        else studentsAlreadyUpToDate++;
                    }
                }
                resp.setTotalRows(totalRows); resp.setStudentsDetected(studentsDetected); resp.setPreviewRows(previewRows);
                resp.setNewStudents(newStudents); resp.setExistingStudents(existingStudents);
                resp.setStudentsWithChanges(studentsWithChanges); resp.setStudentsAlreadyUpToDate(studentsAlreadyUpToDate);
                resp.setNewSubjectResults(newSubjectResults); resp.setSubjectResultsToUpdate(subjectResultsToUpdate);
                resp.setSubjectResultsAlreadyUpToDate(subjectResultsAlreadyUpToDate);
                resp.setInvalidRows(invalidRows); resp.setDuplicateRowsWithinFile(duplicateRowsWithinFile);

                if(totalRows==0) errors.add("No data rows found below header.");
                if(subjectIndices.isEmpty()) warnings.add("No subject columns detected.");
                if(missingUsnCount>0) warnings.add(missingUsnCount+" record(s) have missing USN.");
                if(missingNameCount>0) warnings.add(missingNameCount+" record(s) have missing student name.");
                if(!duplicateUsnsInFile.isEmpty()) warnings.add(duplicateUsnsInFile.size()+" duplicate USN(s) within file: "+String.join(", ",duplicateUsnsInFile));
                if(invalidMarksCount>0) warnings.add(invalidMarksCount+" record(s) have invalid marks (must be 0-100 integer).");
                if(invalidRows>0) warnings.add(invalidRows+" invalid row(s) will be skipped.");
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
        if(file==null||file.isEmpty()) errors.add("Excel file is required.");
        else {
            String fname=file.getOriginalFilename()!=null?file.getOriginalFilename().toLowerCase():"";
            if(!fname.endsWith(".xlsx")&&!fname.endsWith(".xls")) errors.add("Invalid file type. Please upload .xlsx (or .xls) only.");
        }
        if(!errors.isEmpty()){
            result.setSuccess(false); result.setMessage("Validation failed. No records were imported."); result.setErrors(errors);
            result.setStudentsImported(0); result.setSubjectResultsImported(0); return result;
        }
        int studentsImported=0, subjectResultsImported=0, studentsUpdated=0;
        int studentsNew=0, studentsUnchanged=0, subjectResultsInserted=0, subjectResultsUpdated=0, subjectResultsUnchanged=0, skippedRows=0, duplicateRowsWithinFile=0;
        List<Student> toSaveNew=new ArrayList<>();
        Set<String> seenUsnInFile=new HashSet<>();
        try (InputStream is=file.getInputStream(); Workbook wb=WorkbookFactory.create(is)){
            Sheet sheet=chooseSheet(wb);
            if(sheet==null) throw new RuntimeException("No sheets found in workbook.");
            boolean isHod=isHodFormat(sheet);

            if(isHod){
                List<SubjectGroup> groups=extractHodSubjectGroups(sheet);
                if(groups.isEmpty()) throw new RuntimeException("No subject columns detected in HOD format. Ensure Regular sheet has subjects like BEC401 with Int/Ext/Tot/Re/GP.");
                for(int r=6;r<=sheet.getLastRowNum();r++){
                    Row row=sheet.getRow(r);
                    if(row==null||isRowEmpty(row)) continue;
                    String usnVal=getCellString(row.getCell(1)).trim().toUpperCase();
                    String nameVal=getCellString(row.getCell(2)).trim();
                    if(isBlank(usnVal)&&isBlank(nameVal)) continue;
                    if(isBlank(usnVal)){ errors.add("Row "+(r+1)+": Missing USN - skipped."); skippedRows++; continue; }
                    if(isBlank(nameVal)){ errors.add("Row "+(r+1)+" (USN "+usnVal+"): Missing student name - skipped."); skippedRows++; continue; }
                    String normUsn=usnVal.toUpperCase();
                    if(!seenUsnInFile.add(normUsn)){ errors.add("Row "+(r+1)+" (USN "+normUsn+"): Duplicate USN within file - skipped."); duplicateRowsWithinFile++; continue; }
                    Optional<Student> existingOpt=studentRepository.findByUsnIgnoreCase(normUsn);
                    Student student; boolean isNew=false;
                    Boolean lateralForRow = detectLateralFromRow(row, null, normUsn, existingOpt.orElse(null));
                    boolean studentChanged=false;
                    if(existingOpt.isPresent()){
                        student=existingOpt.get(); student.getResults().size();
                        // Detect and apply student field changes — preserve existing email/phone if incoming blank (HOD has no contact columns)
                        String incomingBranch = branch;
                        String incomingAcad = batch;
                        String incomingSem = semester;
                        String incomingCollege = isBlank(collegeCode)?null:collegeCode.trim();
                        String incomingEmail = null;
                        String incomingPhone = null;
                        // Check changes
                        if(!equalsTrim(student.getName(), nameVal)) { student.setName(nameVal); studentChanged=true; }
                        if(!equalsTrim(student.getBranch(), incomingBranch)) { student.setBranch(incomingBranch); studentChanged=true; }
                        if(!equalsTrim(student.getSemester(), incomingSem)) { student.setSemester(incomingSem); studentChanged=true; }
                        if(!equalsTrim(student.getAcademicYear(), incomingAcad)) { student.setAcademicYear(incomingAcad); studentChanged=true; }
                        if(!equalsTrim(student.getCollegeCode(), incomingCollege) && !isBlank(incomingCollege)) { student.setCollegeCode(incomingCollege); studentChanged=true; }
                        if(!isBlank(incomingEmail) && !equalsTrim(student.getEmail(), incomingEmail)) { student.setEmail(incomingEmail); studentChanged=true; }
                        if(!isBlank(incomingPhone) && !equalsTrim(student.getPhoneNumber(), incomingPhone)) { student.setPhoneNumber(incomingPhone); studentChanged=true; }
                        if(lateralForRow!=null && !Objects.equals(student.getLateralEntry(), lateralForRow)) { student.setLateralEntry(lateralForRow); studentChanged=true; }
                        isNew=false;
                    } else {
                        student=new Student(); student.setUsn(normUsn); student.setName(nameVal);
                        student.setBranch(branch); student.setSemester(semester); student.setAcademicYear(batch);
                        student.setCollegeCode(!isBlank(collegeCode)?collegeCode.trim():null);
                        student.setEmail(normUsn.toLowerCase()+"@example.com");
                        student.setPhoneNumber(null);
                        student.setLateralEntry(lateralForRow!=null ? lateralForRow : false);
                        isNew=true;
                        // Create student login account (USN + phone as initial password) — phone may be missing for HOD, then warn
                        ensureStudentAccount(normUsn, nameVal, null, errors, errors);
                    }
                    int rowInserted=0, rowUpdated=0, rowUnchanged=0;
                    List<SubjectResult> toInsert=new ArrayList<>();
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
                            errors.add("Row "+(r+1)+" (USN "+normUsn+"): Invalid numeric for subject '"+g.code+"' Int="+intStr+" Ext="+extStr+" Tot="+totStr+" GP="+gpStr+" - skipped subject."); skippedRows++; continue;
                        }
                        if(tot!=null && (tot<0||tot>100)){ errors.add("Row "+(r+1)+" (USN "+normUsn+"): Tot "+tot+" out of range 0-100 for subject '"+g.code+"' - skipped subject."); continue; }
                        if(tot==null){ errors.add("Row "+(r+1)+" (USN "+normUsn+"): Missing Tot for subject '"+g.code+"' - skipped subject."); continue; }
                        SubjectResult existingSr = isNew?null:findExistingSubject(student, g.code, semester);
                        if(existingSr==null){
                            // Check duplicate within newResults for new student
                            boolean dupInNew=false;
                            for(SubjectResult nr: toInsert){ if(nr.getCode().equalsIgnoreCase(g.code) && nr.getSemester().equalsIgnoreCase(semester)){ dupInNew=true; break; } }
                            if(dupInNew){ errors.add("Row "+(r+1)+" (USN "+normUsn+"): Duplicate SubjectResult for "+g.code+" semester "+semester+" within file - skipped subject."); continue; }
                            var creditOpt = creditResolver.resolveCredit(semester, g.code);
                            if(creditOpt.isEmpty()){
                                // Unknown subject — report but use fallback 4 to preserve import (not inventing official value)
                                errors.add("Row "+(r+1)+" (USN "+normUsn+"): Unknown subject code '"+g.code+"' semester "+semester+" — no official credit mapping, using fallback 4.");
                            }
                            int credits = creditOpt.orElse(4);
                            SubjectResult sr=new SubjectResult();
                            sr.setCode(g.code); sr.setSubject(g.name); sr.setCredits(credits); sr.setMarks(tot);
                            sr.setInternalMarks(intM); sr.setExternalMarks(extM); sr.setRe(!isBlank(reStr)?reStr.trim().toUpperCase():null); sr.setGradePoint(gp);
                            sr.setSemester(semester); sr.setStudent(student);
                            toInsert.add(sr); rowInserted++;
                        } else {
                            String incomingRe = isBlank(reStr)?null:reStr.trim().toUpperCase();
                            if(isSubjectSame(existingSr, tot, intM, extM, incomingRe, gp, g.name)){
                                rowUnchanged++;
                            } else {
                                existingSr.setMarks(tot); existingSr.setInternalMarks(intM); existingSr.setExternalMarks(extM);
                                existingSr.setRe(incomingRe); existingSr.setGradePoint(gp); existingSr.setSubject(g.name);
                                // Update credits if official mapping exists and differs
                                var creditOpt2 = creditResolver.resolveCredit(semester, g.code);
                                if(creditOpt2.isPresent() && !creditOpt2.get().equals(existingSr.getCredits())){
                                    existingSr.setCredits(creditOpt2.get());
                                }
                                rowUpdated++;
                            }
                        }
                    }
                    if(toInsert.isEmpty() && rowUpdated==0 && rowUnchanged==0){
                        // No valid subjects at all
                        errors.add("Row "+(r+1)+" (USN "+normUsn+"): No valid subject marks found - skipped student."); skippedRows++;
                        // revert student changes if it was existing and we changed fields but no subjects? Keep changes? For now revert not needed
                        continue;
                    }
                    // If student is existing and no subject changes and no student field changes → unchanged
                    boolean hasSubjectChanges = rowInserted>0 || rowUpdated>0;
                    boolean isStudentUnchanged = !isNew && !studentChanged && !hasSubjectChanges && rowUnchanged>0;
                    for(SubjectResult nr: toInsert) student.getResults().add(nr);
                    studentService.calculateStudentData(student);
                    if(isNew){
                        toSaveNew.add(student); studentsNew++; studentsImported++;
                        subjectResultsInserted+=rowInserted;
                        subjectResultsUnchanged+=rowUnchanged;
                        subjectResultsImported+=rowInserted+rowUpdated;
                        // For new student, updated is 0
                    } else {
                        if(isStudentUnchanged){
                            studentsUnchanged++; 
                            // still need to save? No changes, but we may have not changed anything, avoid save
                        } else {
                            // studentChanged or subject changes
                            if(studentChanged || hasSubjectChanges){
                                studentRepository.save(student);
                                if(rowInserted==0 && rowUpdated==0 && studentChanged){
                                    // Only student fields changed
                                    studentsUpdated++;
                                } else if(hasSubjectChanges || studentChanged){
                                    studentsUpdated++;
                                }
                            } else {
                                studentsUnchanged++;
                            }
                            studentsImported++; // counts as processed
                        }
                        subjectResultsInserted+=rowInserted;
                        subjectResultsUpdated+=rowUpdated;
                        subjectResultsUnchanged+=rowUnchanged;
                        subjectResultsImported+=rowInserted+rowUpdated;
                    }
                }
                // Handle all unchanged case
                int totalProcessed = studentsNew + studentsUpdated + studentsUnchanged;
                if(totalProcessed==0){
                    result.setSuccess(false); result.setMessage("No valid records to import. All rows had errors. No records were added."); result.setErrors(errors);
                    result.setStudentsImported(0); result.setSubjectResultsImported(0); return result;
                }
                for(Student s: toSaveNew) studentRepository.save(s);
                studentRepository.flush();
                // Build message with sync semantics
                String msg;
                if(studentsNew==0 && studentsUpdated==0 && studentsUnchanged>0){
                    msg = "Already uploaded — no changes detected. Students already up to date: "+studentsUnchanged;
                } else {
                    msg = "Import completed successfully. New: "+studentsNew+", Updated: "+studentsUpdated+", Unchanged: "+studentsUnchanged;
                }
                result.setSuccess(true); result.setMessage(msg);
                result.setStudentsImported(studentsImported); result.setSubjectResultsImported(subjectResultsImported);
                result.setStudentsNew(studentsNew); result.setStudentsUpdated(studentsUpdated); result.setStudentsUnchanged(studentsUnchanged);
                result.setSubjectResultsInserted(subjectResultsInserted); result.setSubjectResultsUpdated(subjectResultsUpdated); result.setSubjectResultsUnchanged(subjectResultsUnchanged);
                result.setSkippedRows(skippedRows); result.setDuplicateRowsWithinFile(duplicateRowsWithinFile);
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
            } else {
                // Generic fallback
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
                Integer branchIdx=colIndex.get("branch"); Integer emailIdx=colIndex.get("email"); Integer phoneIdx=colIndex.get("phone");
                Integer semIdx=colIndex.get("semester"); Integer acadYearIdx=colIndex.containsKey("academic year")?colIndex.get("academic year"):colIndex.get("batch");
                Integer collegeCodeIdx=colIndex.get("college code");
                List<Student> toSaveNewFallback=new ArrayList<>();
                for(int r=headerRowNum+1;r<=sheet.getLastRowNum();r++){
                    Row row=sheet.getRow(r);
                    if(row==null||isRowEmpty(row)) continue;
                    String usnVal=getCellString(row.getCell(usnIdx)).trim().toUpperCase();
                    String nameVal=getCellString(row.getCell(nameIdx)).trim();
                    if(isBlank(usnVal)){ errors.add("Row "+(r+1)+": Missing USN - skipped."); skippedRows++; continue; }
                    if(isBlank(nameVal)){ errors.add("Row "+(r+1)+" (USN "+usnVal+"): Missing student name - skipped."); skippedRows++; continue; }
                    String normUsn=usnVal.toUpperCase();
                    if(!seenUsnInFile.add(normUsn)){ errors.add("Row "+(r+1)+" (USN "+normUsn+"): Duplicate USN within file - skipped."); duplicateRowsWithinFile++; continue; }
                    Optional<Student> existingOpt=studentRepository.findByUsnIgnoreCase(normUsn);
                    Student student; boolean isNew=false;
                    Boolean lateralForRowGeneric = detectLateralFromRow(row, colIndex, normUsn, existingOpt.orElse(null));
                    String branchVal = branchIdx!=null?getCellString(row.getCell(branchIdx)).trim():"";
                    String useBranch = !isBlank(branchVal)?branchVal:branch;
                    String semVal = semIdx!=null?getCellString(row.getCell(semIdx)).trim():"";
                    String useSem = !isBlank(semVal)?semVal:semester;
                    String acadVal = acadYearIdx!=null?getCellString(row.getCell(acadYearIdx)).trim():"";
                    String useAcad = !isBlank(acadVal)?acadVal:batch;
                    String ccVal = collegeCodeIdx!=null?getCellString(row.getCell(collegeCodeIdx)).trim():"";
                    String useCC = !isBlank(ccVal)?ccVal:(collegeCode!=null?collegeCode.trim():null);
                    String emailValRaw=emailIdx!=null?getCellString(row.getCell(emailIdx)).trim():null;
                    String useEmail = null;
                    if(!isBlank(emailValRaw) && emailValRaw.contains("@")) useEmail = emailValRaw.trim();
                    else if(!isBlank(emailValRaw)) useEmail = emailValRaw.trim();
                    String phoneValRaw=phoneIdx!=null?getCellString(row.getCell(phoneIdx)).trim():null;
                    String usePhone = isBlank(phoneValRaw)?null:phoneValRaw.trim();
                    boolean studentChanged=false;
                    if(existingOpt.isPresent()){
                        student=existingOpt.get(); student.getResults().size();
                        if(!equalsTrim(student.getName(), nameVal)) { student.setName(nameVal); studentChanged=true; }
                        if(!equalsTrim(student.getBranch(), useBranch)) { student.setBranch(useBranch); studentChanged=true; }
                        if(!equalsTrim(student.getSemester(), useSem)) { student.setSemester(useSem); studentChanged=true; }
                        if(!equalsTrim(student.getAcademicYear(), useAcad)) { student.setAcademicYear(useAcad); studentChanged=true; }
                        if(!equalsTrim(student.getCollegeCode(), useCC) && !isBlank(useCC)) { student.setCollegeCode(useCC); studentChanged=true; }
                        if(!isBlank(useEmail) && !equalsTrim(student.getEmail(), useEmail)) { student.setEmail(useEmail); studentChanged=true; }
                        if(!isBlank(usePhone) && !equalsTrim(student.getPhoneNumber(), usePhone)) { student.setPhoneNumber(usePhone); studentChanged=true; }
                        if(lateralForRowGeneric!=null && !Objects.equals(student.getLateralEntry(), lateralForRowGeneric)) { student.setLateralEntry(lateralForRowGeneric); studentChanged=true; }
                        isNew=false;
                    } else {
                        student=new Student(); student.setUsn(normUsn); student.setName(nameVal);
                        student.setBranch(useBranch); student.setSemester(useSem); student.setAcademicYear(useAcad);
                        student.setCollegeCode(useCC);
                        student.setEmail(!isBlank(useEmail)?useEmail:normUsn.toLowerCase()+"@example.com");
                        student.setPhoneNumber(usePhone);
                        student.setLateralEntry(lateralForRowGeneric!=null ? lateralForRowGeneric : false);
                        isNew=true;
                        ensureStudentAccount(normUsn, nameVal, usePhone, errors, errors);
                    }
                    int rowInserted=0,rowUpdated=0,rowUnchanged=0;
                    List<SubjectResult> toInsert=new ArrayList<>();
                    for(Integer sIdx: subjectIndices){
                        String header=headers.get(sIdx);
                        String marksStr=getCellString(row.getCell(sIdx)).trim();
                        if(isBlank(marksStr)) continue;
                        int marks; try{ marks=parseMarks(marksStr); }catch(NumberFormatException e){
                            errors.add("Row "+(r+1)+" (USN "+normUsn+"): Invalid marks '"+marksStr+"' for subject '"+header+"' - skipped subject."); continue;
                        }
                        if(marks<0||marks>100){ errors.add("Row "+(r+1)+" (USN "+normUsn+"): Marks "+marks+" out of range 0-100 for subject '"+header+"' - skipped subject."); continue; }
                        String code=header.contains(":")?header.split(":",2)[0].trim():header;
                        String subject=header.contains(":")?header.split(":",2)[1].trim():header;
                        if(isBlank(subject)) subject=code;
                        SubjectResult existingSr = isNew?null:findExistingSubject(student, code, useSem);
                        if(existingSr==null){
                            boolean dupInNew=false;
                            for(SubjectResult nr: toInsert){ if(nr.getCode().equalsIgnoreCase(code) && nr.getSemester().equalsIgnoreCase(useSem)){ dupInNew=true; break; } }
                            if(dupInNew){ errors.add("Row "+(r+1)+" (USN "+normUsn+"): Duplicate SubjectResult for "+code+" semester "+useSem+" within file - skipped subject."); continue; }
                            var creditOpt = creditResolver.resolveCredit(useSem, code);
                            if(creditOpt.isEmpty()){
                                errors.add("Row "+(r+1)+" (USN "+normUsn+"): Unknown subject code '"+code+"' semester "+useSem+" — no official credit mapping, using fallback 4.");
                            }
                            int credits = creditOpt.orElse(4);
                            SubjectResult sr=new SubjectResult();
                            sr.setCode(code); sr.setSubject(subject); sr.setCredits(credits); sr.setMarks(marks); sr.setSemester(useSem); sr.setStudent(student);
                            toInsert.add(sr); rowInserted++;
                        } else {
                            if(existingSr.getMarks()!=null && existingSr.getMarks()==marks){
                                rowUnchanged++;
                            } else {
                                existingSr.setMarks(marks);
                                var creditOpt2 = creditResolver.resolveCredit(useSem, code);
                                if(creditOpt2.isPresent() && !creditOpt2.get().equals(existingSr.getCredits())){
                                    existingSr.setCredits(creditOpt2.get());
                                }
                                rowUpdated++;
                            }
                        }
                    }
                    if(toInsert.isEmpty() && rowUpdated==0 && rowUnchanged==0){ errors.add("Row "+(r+1)+" (USN "+normUsn+"): No valid subject marks found - skipped student."); skippedRows++; continue; }
                    boolean hasSubjectChanges = rowInserted>0 || rowUpdated>0;
                    boolean isStudentUnchanged = !isNew && !studentChanged && !hasSubjectChanges && rowUnchanged>0;
                    for(SubjectResult nr: toInsert) student.getResults().add(nr);
                    studentService.calculateStudentData(student);
                    if(isNew){
                        toSaveNewFallback.add(student); studentsNew++; studentsImported++; subjectResultsInserted+=rowInserted; subjectResultsUnchanged+=rowUnchanged; subjectResultsImported+=rowInserted+rowUpdated;
                    } else {
                        if(isStudentUnchanged){
                            studentsUnchanged++;
                        } else {
                            if(studentChanged || hasSubjectChanges){
                                studentRepository.save(student);
                                studentsUpdated++;
                            } else {
                                studentsUnchanged++;
                            }
                            studentsImported++;
                        }
                        subjectResultsInserted+=rowInserted; subjectResultsUpdated+=rowUpdated; subjectResultsUnchanged+=rowUnchanged; subjectResultsImported+=rowInserted+rowUpdated;
                    }
                }
                int totalProcessed = studentsNew + studentsUpdated + studentsUnchanged;
                if(totalProcessed==0){
                    result.setSuccess(false); result.setMessage("No valid records to import. All rows had errors. No records were added."); result.setErrors(errors);
                    result.setStudentsImported(0); result.setSubjectResultsImported(0); return result;
                }
                for(Student s: toSaveNewFallback) studentRepository.save(s);
                studentRepository.flush();
                String msg;
                if(studentsNew==0 && studentsUpdated==0 && studentsUnchanged>0){
                    msg = "Already uploaded — no changes detected. Students already up to date: "+studentsUnchanged;
                } else {
                    msg = "Import completed successfully. New: "+studentsNew+", Updated: "+studentsUpdated+", Unchanged: "+studentsUnchanged;
                }
                result.setSuccess(true); result.setMessage(msg);
                result.setStudentsImported(studentsImported); result.setSubjectResultsImported(subjectResultsImported);
                result.setStudentsNew(studentsNew); result.setStudentsUpdated(studentsUpdated); result.setStudentsUnchanged(studentsUnchanged);
                result.setSubjectResultsInserted(subjectResultsInserted); result.setSubjectResultsUpdated(subjectResultsUpdated); result.setSubjectResultsUnchanged(subjectResultsUnchanged);
                result.setSkippedRows(skippedRows); result.setDuplicateRowsWithinFile(duplicateRowsWithinFile);
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

    private SubjectResult findExistingSubject(Student student, String code, String semester){
        if(student==null || student.getResults()==null) return null;
        for(SubjectResult sr: student.getResults()){
            if(sr.getCode()!=null && sr.getCode().equalsIgnoreCase(code) && sr.getSemester()!=null && sr.getSemester().equalsIgnoreCase(semester)){
                return sr;
            }
        }
        return null;
    }

    private boolean isSubjectSame(SubjectResult existing, Integer tot, Integer intM, Integer extM, String re, Integer gp, String subjectName){
        if(existing==null) return false;
        if(!Objects.equals(existing.getMarks(), tot)) return false;
        if(!Objects.equals(existing.getInternalMarks(), intM)) return false;
        if(!Objects.equals(existing.getExternalMarks(), extM)) return false;
        String existingRe = existing.getRe()==null?null:existing.getRe().trim().toUpperCase();
        String incomingRe2 = re==null?null:re.trim().toUpperCase();
        if(!Objects.equals(existingRe, incomingRe2)) return false;
        if(!Objects.equals(existing.getGradePoint(), gp)) return false;
        if(subjectName!=null && existing.getSubject()!=null && !existing.getSubject().trim().equalsIgnoreCase(subjectName.trim())) return false; // optional
        return true;
    }

    private boolean isStudentChanged(Student existing, String incomingName, String incomingBranch, String incomingSemester, String incomingAcad, String incomingCollege, String incomingEmail, String incomingPhone, Boolean incomingLateral){
        if(existing==null) return true;
        if(!equalsTrim(existing.getName(), incomingName)) return true;
        if(!equalsTrim(existing.getBranch(), incomingBranch)) return true;
        if(!equalsTrim(existing.getSemester(), incomingSemester)) return true;
        if(!equalsTrim(existing.getAcademicYear(), incomingAcad)) return true;
        if(!isBlank(incomingCollege) && !equalsTrim(existing.getCollegeCode(), incomingCollege)) return true;
        if(!isBlank(incomingEmail) && !equalsTrim(existing.getEmail(), incomingEmail)) return true;
        if(!isBlank(incomingPhone) && !equalsTrim(existing.getPhoneNumber(), incomingPhone)) return true;
        if(incomingLateral!=null && !Objects.equals(existing.getLateralEntry(), incomingLateral)) return true;
        return false;
    }

    private boolean equalsTrim(String a, String b){
        String aa = a==null?null:a.trim();
        String bb = b==null?null:b.trim();
        if(aa==null && bb==null) return true;
        if(aa==null || bb==null) return false;
        return aa.equalsIgnoreCase(bb);
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
            String raw=headers.get(i).trim().toLowerCase();
            String norm=raw.replaceAll("[._\\-]+"," ").replaceAll("\\s+"," ").trim(); // normalize
            String compact=norm.replaceAll("\\s+",""); // without spaces for matching
            if(norm.equals("usn")||norm.equals("usn no")||norm.equals("usn number")||norm.equals("university seat number")||norm.equals("seat no")||norm.equals("roll no")||norm.equals("reg no")||norm.equals("registration no")||norm.contains("usn")){
                if(!map.containsKey("usn")) map.put("usn",i);
            }
            if(norm.equals("name")||norm.equals("student name")||norm.equals("candidate name")||norm.equals("full name")||(norm.contains("name")&&!norm.contains("subject"))){
                if(!map.containsKey("name")) map.put("name",i);
            }
            if(norm.equals("branch")||norm.equals("department")||norm.equals("dept")) map.putIfAbsent("branch",i);
            if(norm.equals("semester")||norm.equals("sem")) map.putIfAbsent("semester",i);
            if(norm.equals("academic year")||norm.equals("academic year")||norm.equals("batch")||norm.equals("year")) map.putIfAbsent("academic year",i);
            if(norm.equals("batch")) map.putIfAbsent("batch",i);
            // Email variants
            if(compact.equals("email")||compact.equals("emailaddress")||compact.equals("emailid")||norm.equals("e mail")||norm.equals("e-mail")||norm.equals("mail")||norm.equals("student email")||norm.equals("student email id")||norm.equals("student email address")||norm.contains("email")){
                map.putIfAbsent("email",i);
            }
            // Phone variants
            if(compact.equals("phone")||compact.equals("phonenumber")||compact.equals("phoneno")||compact.equals("mobile")||compact.equals("mobilenumber")||compact.equals("mobileno")||compact.equals("contact")||compact.equals("contactnumber")||compact.equals("contactno")||norm.equals("phone number")||norm.equals("phone no")||norm.equals("mobile number")||norm.equals("mobile no")||norm.equals("contact number")||norm.equals("contact no")||norm.equals("mobile no.")||norm.equals("phone no.")||norm.contains("phone")||norm.contains("mobile")||norm.contains("contact")){
                // Avoid false positives: ensure not subject
                if(!norm.contains("subject")){
                    map.putIfAbsent("phone",i);
                }
            }
            if(norm.equals("college code")||norm.equals("college code")||compact.equals("collegecode")||norm.equals("college")) map.putIfAbsent("college code",i);
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
        return false;
    }

    private String normalizePhone(String raw){
        if(isBlank(raw)) return null;
        String digits = raw.replaceAll("[^0-9]","");
        // Handle +91 prefix
        if(digits.length()>10 && digits.startsWith("91") && digits.length()>=12){
            digits = digits.substring(digits.length()-10);
        } else if(digits.length()>10){
            digits = digits.substring(digits.length()-10);
        }
        return digits;
    }

    private boolean isValidPhone(String phone){
        if(isBlank(phone)) return false;
        String n = normalizePhone(phone);
        return n!=null && n.matches("\\d{10}");
    }

    private void ensureStudentAccount(String usn, String name, String phone, List<String> warnings, List<String> errors){
        String normUsn = usn==null?null:usn.trim().toUpperCase();
        if(isBlank(normUsn)) return;
        Optional<User> existingUser = userRepository.findByUserId(normUsn);
        if(existingUser.isPresent()){
            // Existing account — preserve password, do not change on phone update
            return;
        }
        // New student account
        String normalizedPhone = normalizePhone(phone);
        if(!isValidPhone(normalizedPhone)){
            String msg = "Student "+normUsn+" imported, but Student login account could not be created because no valid mobile number was provided.";
            if(warnings!=null) warnings.add(msg);
            else if(errors!=null) errors.add(msg);
            return;
        }
        User u = new User();
        u.setUserId(normUsn);
        u.setName(name!=null?name:normUsn);
        u.setPhone(normalizedPhone);
        u.setRole("STUDENT");
        u.setPassword(encoder.encode(normalizedPhone));
        u.setMustChangePassword(true);
        try{
            userRepository.save(u);
        }catch(Exception e){
            if(errors!=null) errors.add("Failed to create student account for "+normUsn+": "+e.getMessage());
        }
    }
}
