package com.Result_Analysis.Result_Analysis;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Subject-wise Batch Comparison — SAME SUBJECT between CORRESPONDING STUDENTS from DIFFERENT BATCHES.
 * Example: 2KD24EC001 (BEC401 Tot 59) ↔ 2KD23EC001 (BEC401 Tot 66) difference = -7
 * Stable identifier = last 3 digits of USN (e.g., 001). Batch year extracted from USN (2 chars after college code).
 */
@Service
public class CompareService {

    private final StudentRepository studentRepository;

    public CompareService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Transactional(readOnly = true)
    public BatchComparisonResponse compareForStudent(String currentBatch, String previousBatch, String branch, String semester, String subjectCode, String authUsn) {
        // STUDENT: only own stableId
        BatchComparisonResponse full = compare(currentBatch, previousBatch, branch, semester, subjectCode);
        String stable = extractStableId(authUsn);
        if(stable==null || stable.isEmpty()) {
            full.setComparisons(List.of());
            full.setMatchedStudents(0);
            return full;
        }
        List<SubjectComparisonDto> filtered = new ArrayList<>();
        for(SubjectComparisonDto dto : full.getComparisons()){
            String curStable = dto.getCurrentUsn()!=null ? extractStableId(dto.getCurrentUsn()) : null;
            String prevStable = dto.getPreviousUsn()!=null ? extractStableId(dto.getPreviousUsn()) : null;
            if(stable.equals(curStable) || stable.equals(prevStable)){
                // Only expose own comparison, hide other student's marks? Keep as is but filtered
                // For privacy, we could clear other student's marks if not own, but spec says own-related only, so we keep the pair where own stable matches
                filtered.add(dto);
            }
        }
        full.setComparisons(filtered);
        // Recalculate matched counts for filtered
        long matched = filtered.stream().filter(d -> d.getCurrentMarks()!=null && d.getPreviousMarks()!=null).count();
        full.setMatchedStudents((int)matched);
        return full;
    }

    @Transactional(readOnly = true)
    public BatchComparisonResponse compare(String currentBatch, String previousBatch, String branch, String semester, String subjectCode) {
        BatchComparisonResponse resp = new BatchComparisonResponse();
        resp.setCurrentBatch(currentBatch);
        resp.setPreviousBatch(previousBatch);
        resp.setBranch(branch);
        resp.setSemester(semester);
        resp.setSubjectCode(subjectCode);

        if (isBlank(currentBatch) || isBlank(previousBatch) || isBlank(branch) || isBlank(semester) || isBlank(subjectCode)) {
            resp.setComparisons(Collections.emptyList());
            resp.setMatchedStudents(0);
            resp.setUnmatchedCurrent(0);
            resp.setUnmatchedPrevious(0);
            return resp;
        }

        String curYear = extractYearFromBatch(currentBatch);
        String prevYear = extractYearFromBatch(previousBatch);
        String semNorm = normalizeSemester(semester);
        String subjNorm = subjectCode.trim().toUpperCase();

        List<Student> all = studentRepository.findAll();
        for (Student s : all) s.getResults().size();

        // Filter by branch and semester and batch year
        Map<String, Student> currentMap = new HashMap<>(); // stableId -> student
        Map<String, Student> previousMap = new HashMap<>();

        for (Student s : all) {
            if (s.getBranch() == null || !s.getBranch().equalsIgnoreCase(branch.trim())) continue;
            // Semester check: either student.semester or any result semester matches
            boolean semMatch = false;
            String studSemNorm = normalizeSemester(s.getSemester());
            if (semNorm.equals(studSemNorm)) semMatch = true;
            else if (s.getResults() != null) {
                for (SubjectResult sr : s.getResults()) {
                    if (semNorm.equals(normalizeSemester(sr.getSemester()))) { semMatch = true; break; }
                }
            }
            if (!semMatch) continue;

            String usn = s.getUsn();
            if (isBlank(usn)) continue;
            String usnYear = extractYearFromUsn(usn);
            String stableId = extractStableId(usn);

            if (isBlank(usnYear) || isBlank(stableId)) continue;

            if (usnYear.equals(curYear)) {
                // If duplicate stableId within same batch, keep first (should not happen)
                currentMap.putIfAbsent(stableId, s);
            } else if (usnYear.equals(prevYear)) {
                previousMap.putIfAbsent(stableId, s);
            }
        }

        // Now for each stableId that exists in either map, try to find subject
        List<SubjectComparisonDto> comparisons = new ArrayList<>();
        Set<String> allStableIds = new HashSet<>();
        allStableIds.addAll(currentMap.keySet());
        allStableIds.addAll(previousMap.keySet());

        int matched = 0;
        double curSum = 0;
        double prevSum = 0;
        int curCount = 0;
        int prevCount = 0;

        // First collect averages per batch for the subject
        for (String sid : allStableIds) {
            Student curStu = currentMap.get(sid);
            Student prevStu = previousMap.get(sid);
            Integer curMarks = null;
            Integer prevMarks = null;
            String curRe = null;
            String prevRe = null;
            String subjName = null;

            if (curStu != null) {
                SubjectResult sr = findSubject(curStu, subjNorm, semNorm);
                if (sr != null) {
                    curMarks = sr.getMarks();
                    curRe = sr.getRe();
                    subjName = sr.getSubject();
                    if (curMarks != null) { curSum += curMarks; curCount++; }
                }
            }
            if (prevStu != null) {
                SubjectResult sr = findSubject(prevStu, subjNorm, semNorm);
                if (sr != null) {
                    prevMarks = sr.getMarks();
                    prevRe = sr.getRe();
                    if (subjName == null) subjName = sr.getSubject();
                    if (prevMarks != null) { prevSum += prevMarks; prevCount++; }
                }
            }

            // Only create comparison if at least one side has marks
            if (curMarks != null || prevMarks != null) {
                // For matched, require both sides have marks
                if (curMarks != null && prevMarks != null) matched++;
                Integer diff = null;
                if (curMarks != null && prevMarks != null) diff = curMarks - prevMarks;
                SubjectComparisonDto dto = new SubjectComparisonDto();
                dto.setCurrentUsn(curStu != null ? curStu.getUsn() : null);
                dto.setPreviousUsn(prevStu != null ? prevStu.getUsn() : null);
                dto.setSubjectCode(subjNorm);
                dto.setSubjectName(subjName != null ? subjName : subjNorm);
                dto.setCurrentMarks(curMarks);
                dto.setPreviousMarks(prevMarks);
                dto.setDifference(diff);
                dto.setCurrentRe(curRe);
                dto.setPreviousRe(prevRe);
                comparisons.add(dto);
            }
        }

        // Sort by stableId (extract from USN)
        comparisons.sort(Comparator.comparing(dto -> {
            String usn = dto.getCurrentUsn() != null ? dto.getCurrentUsn() : dto.getPreviousUsn();
            return extractStableId(usn);
        }));

        Double curAvg = curCount > 0 ? Math.round((curSum / curCount) * 100.0) / 100.0 : null;
        Double prevAvg = prevCount > 0 ? Math.round((prevSum / prevCount) * 100.0) / 100.0 : null;
        Double avgDiff = (curAvg != null && prevAvg != null) ? Math.round((curAvg - prevAvg) * 100.0) / 100.0 : null;

        resp.setCurrentBatchAverage(curAvg);
        resp.setPreviousBatchAverage(prevAvg);
        resp.setAverageDifference(avgDiff);
        resp.setMatchedStudents(matched);
        resp.setUnmatchedCurrent((int) allStableIds.stream().filter(id -> currentMap.containsKey(id) && !previousMap.containsKey(id)).count());
        resp.setUnmatchedPrevious((int) allStableIds.stream().filter(id -> previousMap.containsKey(id) && !currentMap.containsKey(id)).count());
        // Actually unmatched for comparison perspective: students that have no counterpart
        // But also need to count those with no subject marks? For simplicity, use map sizes
        resp.setComparisons(comparisons);
        if (!comparisons.isEmpty() && comparisons.get(0).getSubjectName() != null) resp.setSubjectName(comparisons.get(0).getSubjectName());
        else resp.setSubjectName(subjNorm);

        return resp;
    }

    private SubjectResult findSubject(Student s, String subjNorm, String semNorm) {
        if (s.getResults() == null) return null;
        for (SubjectResult sr : s.getResults()) {
            if (sr.getCode() == null) continue;
            String codeNorm = sr.getCode().trim().toUpperCase().replaceAll("\\s+", "");
            String targetNorm = subjNorm.replaceAll("\\s+", "");
            if (!codeNorm.equals(targetNorm)) {
                // Fallback: check subject name contains code?
                if (sr.getSubject() != null && sr.getSubject().toUpperCase().contains(targetNorm)) {
                    // allow
                } else continue;
            }
            // Semester must match if semNorm provided
            if (semNorm != null && !semNorm.isEmpty()) {
                String srSemNorm = normalizeSemester(sr.getSemester());
                if (!semNorm.equals(srSemNorm)) {
                    // Also check student's semester? But we already filtered student, so allow if either matches
                    // If subject's semester doesn't match, skip
                    continue;
                }
            }
            return sr;
        }
        return null;
    }

    private String extractYearFromBatch(String batch) {
        if (isBlank(batch)) return "";
        // Batch may be like "2024-2028", "2024", "24", "2KD24"
        String b = batch.trim();
        // Find 4-digit year 20xx
        Matcher m4 = Pattern.compile("(20\\d{2})").matcher(b);
        if (m4.find()) {
            String y4 = m4.group(1);
            return y4.substring(2); // return 2-digit like "24"
        }
        // Find 2-digit year
        Matcher m2 = Pattern.compile("\\b(\\d{2})\\b").matcher(b);
        if (m2.find()) return m2.group(1);
        // Fallback: last 2 chars if numeric
        if (b.length() >= 2 && b.substring(b.length()-2).matches("\\d{2}")) return b.substring(b.length()-2);
        return b;
    }

    private String extractYearFromUsn(String usn) {
        if (isBlank(usn)) return "";
        String u = usn.trim().toUpperCase();
        // USN pattern: college(1-3) + year(2) + branch(2-3) + id(3)
        // Find 2-digit year after college code: look for pattern where after first 2-3 alphanum, there's 2 digits
        // Simpler: find first occurrence of 2 consecutive digits that is likely year (20-30)
        Matcher m = Pattern.compile("(\\d{2})").matcher(u);
        while (m.find()) {
            String y = m.group(1);
            // Year likely 20-30 for 2020-2030
            try {
                int yi = Integer.parseInt(y);
                if (yi >= 20 && yi <= 30) return y;
            } catch (Exception e) {}
        }
        // Fallback: substring 3-5 for typical 2KD24EC001 (year at 3-4 index 3)
        if (u.length() >= 5) {
            String sub = u.substring(3, 5);
            if (sub.matches("\\d{2}")) return sub;
        }
        return "";
    }

    private String extractStableId(String usn) {
        if (isBlank(usn)) return "";
        String u = usn.trim().toUpperCase();
        // Stable is last 3 digits
        Matcher m = Pattern.compile("(\\d{3})\\s*$").matcher(u);
        if (m.find()) return m.group(1);
        // Fallback: last 3 chars
        if (u.length() >= 3) return u.substring(u.length()-3);
        return u;
    }

    private String normalizeSemester(String sem) {
        if (isBlank(sem)) return "";
        String t = sem.trim().toLowerCase();
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

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
}
