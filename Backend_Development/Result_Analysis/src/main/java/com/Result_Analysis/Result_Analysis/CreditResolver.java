package com.Result_Analysis.Result_Analysis;

import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class CreditResolver {

    private final SubjectCreditRepository repo;

    public CreditResolver(SubjectCreditRepository repo) {
        this.repo = repo;
    }

    /**
     * Resolve official VTU credit for semester + subjectCode.
     * Returns Optional.empty() for unknown (do NOT silently return 4 or 0).
     * Normalizes code safely (trim, upper, remove spaces).
     */
    public Optional<Integer> resolveCredit(String semester, String subjectCode) {
        if(semester==null || subjectCode==null) return Optional.empty();
        String semNorm = normalizeSemester(semester);
        String codeNorm = normalizeCode(subjectCode);
        if(semNorm==null || codeNorm==null) return Optional.empty();
        // Try exact semester + code + scheme 2022
        var opt = repo.findBySemesterAndSubjectCodeAndScheme(semNorm, codeNorm, "2022");
        if(opt.isPresent()) return Optional.of(opt.get().getCredits());
        // Fallback: try without scheme (for backward compat)
        var opt2 = repo.findBySemesterAndSubjectCode(semNorm, codeNorm);
        if(opt2.isPresent()) return Optional.of(opt2.get().getCredits());
        return Optional.empty();
    }

    private String normalizeSemester(String sem){
        if(sem==null) return null;
        String t = sem.trim().toLowerCase();
        if(t.contains("1")) return "1";
        if(t.contains("2")) return "2";
        if(t.contains("3")) return "3";
        if(t.contains("4")) return "4";
        if(t.contains("5")) return "5";
        if(t.contains("6")) return "6";
        if(t.contains("7")) return "7";
        if(t.contains("8")) return "8";
        String d = t.replaceAll("[^0-9]","");
        if(!d.isEmpty()) return d.substring(0,1);
        return t;
    }

    private String normalizeCode(String code){
        if(code==null) return null;
        return code.trim().toUpperCase().replaceAll("\\s+","");
    }
}
