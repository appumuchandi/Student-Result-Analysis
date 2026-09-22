package com.Result_Analysis.Result_Analysis;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

    private final StudentRepository studentRepository;
    private final StudentService studentService;

    public AnalyticsService(StudentRepository studentRepository, StudentService studentService) {
        this.studentRepository = studentRepository;
        this.studentService = studentService;
    }

    @Transactional(readOnly = true)
    public List<SubjectStatsDto> getSubjectStats(String semesterFilter) {
        List<Student> all = studentRepository.findAll();
        // Ensure lazy collections initialized within transaction
        for (Student s : all) { s.getResults().size(); }
        // Map subjectName -> list of marks
        Map<String, List<Integer>> marksBySubject = new LinkedHashMap<>();
        Map<String, String> codeBySubject = new HashMap<>();
        Map<String, Integer> passCountBySubject = new HashMap<>();
        Map<String, Integer> totalCountBySubject = new HashMap<>();

        String filterNorm = semesterFilter != null ? normalizeSemester(semesterFilter) : null;
        boolean hasFilter = filterNorm != null && !filterNorm.isEmpty();

        for (Student s : all) {
            if (s.getResults() == null) continue;
            for (SubjectResult sr : s.getResults()) {
                if (sr.getSubject() == null || sr.getSubject().trim().isEmpty()) continue;
                // Filter by semester if provided
                if (hasFilter) {
                    String srSem = normalizeSemester(sr.getSemester());
                    String studentSem = normalizeSemester(s.getSemester());
                    // Also check result's semester; if neither matches, skip
                    boolean matches = filterNorm.equals(srSem) || filterNorm.equals(studentSem);
                    if (!matches) continue;
                }
                String subjectKey = sr.getSubject().trim();
                // Use subject as key; keep code if available
                if (sr.getCode() != null && !sr.getCode().trim().isEmpty()) {
                    codeBySubject.putIfAbsent(subjectKey, sr.getCode().trim());
                }
                marksBySubject.computeIfAbsent(subjectKey, k -> new ArrayList<>());
                totalCountBySubject.put(subjectKey, totalCountBySubject.getOrDefault(subjectKey, 0) + 1);
                if (sr.getMarks() != null) {
                    marksBySubject.get(subjectKey).add(sr.getMarks());
                    String status = sr.getStatus();
                    if (status == null) status = studentService.calculateStatus(sr.getMarks());
                    if ("PASS".equalsIgnoreCase(status)) {
                        passCountBySubject.put(subjectKey, passCountBySubject.getOrDefault(subjectKey, 0) + 1);
                    }
                }
            }
        }

        List<SubjectStatsDto> result = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> e : marksBySubject.entrySet()) {
            String subject = e.getKey();
            List<Integer> marks = e.getValue();
            if (marks.isEmpty()) continue;
            int sum = marks.stream().mapToInt(Integer::intValue).sum();
            double avg = (double) sum / marks.size();
            int highest = marks.stream().mapToInt(Integer::intValue).max().orElse(0);
            int lowest = marks.stream().mapToInt(Integer::intValue).min().orElse(0);
            int total = totalCountBySubject.getOrDefault(subject, marks.size());
            int pass = passCountBySubject.getOrDefault(subject, 0);
            double passPct = total > 0 ? ((double) pass / total) * 100.0 : 0.0;

            SubjectStatsDto dto = new SubjectStatsDto();
            dto.setSubject(subject);
            dto.setCode(codeBySubject.getOrDefault(subject, "--"));
            dto.setAverageMarks(Math.round(avg * 100.0) / 100.0);
            dto.setPassPercentage(Math.round(passPct * 100.0) / 100.0);
            dto.setHighestMarks(highest);
            dto.setLowestMarks(lowest);
            dto.setStudentCount(marks.size());
            dto.setTotalStudents(total);
            result.add(dto);
        }

        // Sort by subject name for stable output
        result.sort(Comparator.comparing(SubjectStatsDto::getSubject, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    @Transactional(readOnly = true)
    public LateralStatsDto getLateralEntryStats(String semester) {
        List<Student> all = studentRepository.findAll();
        for (Student s : all) { if (s.getResults()!=null) s.getResults().size(); }
        if (semester != null && !semester.trim().isEmpty()) {
            String filterNorm = normalizeSemester(semester);
            long regular = 0, lateral = 0;
            for (Student s : all) {
                String studSem = normalizeSemester(s.getSemester());
                // Check if student belongs to requested semester (or any of its subject results)
                boolean matches = false;
                if (filterNorm.equals(studSem)) matches = true;
                else if (s.getResults() != null) {
                    for (SubjectResult sr : s.getResults()) {
                        if (filterNorm.equals(normalizeSemester(sr.getSemester()))) { matches = true; break; }
                    }
                }
                if (!matches) continue;
                boolean isLateral = s.getLateralEntry() != null ? s.getLateralEntry() : false;
                if (isLateral) lateral++; else regular++;
            }
            LateralStatsDto dto = new LateralStatsDto();
            dto.setSemester(semester);
            dto.setRegularCount(regular);
            dto.setLateralCount(lateral);
            dto.setTotalCount(regular + lateral);
            return dto;
        } else {
            // Aggregate per semester
            Map<String, LateralStatsDto> map = new LinkedHashMap<>();
            // Initialize for 1-8
            for (int i = 1; i <= 8; i++) {
                String semLabel = i + getSuffix(i);
                map.put(String.valueOf(i), new LateralStatsDto(semLabel, 0, 0));
            }
            long totalRegular = 0, totalLateral = 0;
            for (Student s : all) {
                String semNorm = normalizeSemester(s.getSemester());
                boolean isLateral = s.getLateralEntry() != null ? s.getLateralEntry() : false;
                if (semNorm != null && !semNorm.isEmpty() && map.containsKey(semNorm)) {
                    LateralStatsDto dto = map.get(semNorm);
                    if (isLateral) dto.setLateralCount(dto.getLateralCount() + 1);
                    else dto.setRegularCount(dto.getRegularCount() + 1);
                    dto.setTotalCount(dto.getRegularCount() + dto.getLateralCount());
                }
                if (isLateral) totalLateral++; else totalRegular++;
            }
            LateralStatsDto overall = new LateralStatsDto();
            overall.setSemester("All");
            overall.setRegularCount(totalRegular);
            overall.setLateralCount(totalLateral);
            overall.setTotalCount(totalRegular + totalLateral);
            overall.setSemesterWise(map);
            return overall;
        }
    }

    private String normalizeSemester(String sem) {
        if (sem == null) return "";
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

    private String getSuffix(int n) {
        if (n == 1) return "st";
        if (n == 2) return "nd";
        if (n == 3) return "rd";
        return "th";
    }
}
