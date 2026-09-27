package com.Result_Analysis.Result_Analysis;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class SubjectCreditInitializer implements CommandLineRunner {

    private final SubjectCreditRepository repo;

    public SubjectCreditInitializer(SubjectCreditRepository repo) {
        this.repo = repo;
    }

    @Override
    public void run(String... args) {
        // Official VTU ECE 2022 scheme (https://vtu.ac.in/pdf/2022_3to8/ecesch.pdf) — only 18 project codes
        Object[][] mappings = {
            {"3", "BMATEC301", 3},
            {"3", "BEC302", 4},
            {"3", "BEC303", 4},
            {"3", "BEC304", 3},
            {"3", "BECL305", 1},
            {"3", "BEC306B", 3},
            {"3", "BSCK307", 1},
            {"3", "BEC358A", 1},
            {"3", "BPEK359", 0},
            {"4", "BEC401", 3},
            {"4", "BEC402", 4},
            {"4", "BEC403", 4},
            {"4", "BECL404", 1},
            {"4", "BEC405A", 3},
            {"4", "BEC456A", 1},
            {"4", "BBOK407", 3},
            {"4", "BUHK408", 1},
            {"4", "BYOK459", 0},
        };
        for(Object[] m : mappings){
            String sem = (String)m[0];
            String code = (String)m[1];
            Integer credits = (Integer)m[2];
            var existing = repo.findBySemesterAndSubjectCodeAndScheme(sem, code, "2022");
            if(existing.isEmpty()){
                SubjectCredit sc = new SubjectCredit(sem, code, credits, "2022");
                repo.save(sc);
            }
        }
    }
}
