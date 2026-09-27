package com.Result_Analysis.Result_Analysis;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
public class PdfExportService {

    private final StudentRepository studentRepository;
    private final StudentService studentService;

    public PdfExportService(StudentRepository studentRepository, StudentService studentService) {
        this.studentRepository = studentRepository;
        this.studentService = studentService;
    }

    @Transactional(readOnly = true)
    public byte[] generateRankPdf() {
        List<Student> all = studentRepository.findAll();
        for (Student s : all) { s.getResults().size(); if (s.getResults()!=null) for(SubjectResult sr: s.getResults()) sr.getGrade(); }
        // Calculate and sort like controller
        for (Student s : all) studentService.calculateStudentData(s);
        all.sort((a,b)->{
            Double ca = a.getCgpa(); Double cb = b.getCgpa();
            if (ca==null && cb==null) return 0;
            if (ca==null) return 1;
            if (cb==null) return -1;
            int r = Double.compare(cb,ca);
            if(r!=0) return r;
            return String.valueOf(a.getUsn()).compareToIgnoreCase(String.valueOf(b.getUsn()));
        });
        int rank=1; for(Student s: all) s.setstudentRank(rank++);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, new java.awt.Color(37,99,235));
            Font subFont = new Font(Font.HELVETICA, 10, Font.NORMAL, new java.awt.Color(100,116,139));
            Font headerFont = new Font(Font.HELVETICA, 9, Font.BOLD, java.awt.Color.WHITE);
            Font cellFont = new Font(Font.HELVETICA, 8, Font.NORMAL, new java.awt.Color(23,32,51));
            Font smallFont = new Font(Font.HELVETICA, 7, Font.NORMAL, new java.awt.Color(100,116,139));

            Paragraph title = new Paragraph("College Result Analysis — Rank Analysis", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);
            Paragraph sub = new Paragraph("Generated on " + java.time.LocalDate.now() + "  |  Students: " + all.size(), subFont);
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(12);
            doc.add(sub);

            if (all.isEmpty()) {
                Paragraph empty = new Paragraph("No student data available.", cellFont);
                empty.setAlignment(Element.ALIGN_CENTER);
                doc.add(empty);
            } else {
                PdfPTable table = new PdfPTable(6);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{1,2.2f,3.5f,1.8f,1.2f,1.5f});
                table.setHeaderRows(1);

                String[] headers = {"Rank","USN","Name","Branch","CGPA","Result"};
                for (String h : headers) {
                    PdfPCell hc = new PdfPCell(new Phrase(h, headerFont));
                    hc.setBackgroundColor(new java.awt.Color(37,99,235));
                    hc.setHorizontalAlignment(Element.ALIGN_CENTER);
                    hc.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    hc.setPadding(6);
                    table.addCell(hc);
                }

                for (Student s : all) {
                    PdfPCell c0 = new PdfPCell(new Phrase(String.valueOf(s.getstudentRank()), cellFont));
                    c0.setHorizontalAlignment(Element.ALIGN_CENTER); c0.setPadding(4); table.addCell(c0);
                    PdfPCell c1 = new PdfPCell(new Phrase(s.getUsn()!=null?s.getUsn():"--", cellFont));
                    c1.setPadding(4); table.addCell(c1);
                    PdfPCell c2 = new PdfPCell(new Phrase(s.getName()!=null?s.getName():"--", cellFont));
                    c2.setPadding(4); table.addCell(c2);
                    PdfPCell c3 = new PdfPCell(new Phrase(s.getBranch()!=null?s.getBranch():"--", cellFont));
                    c3.setHorizontalAlignment(Element.ALIGN_CENTER); c3.setPadding(4); table.addCell(c3);
                    PdfPCell c4 = new PdfPCell(new Phrase(s.getCgpa()!=null?String.format("%.2f", s.getCgpa()):"--", cellFont));
                    c4.setHorizontalAlignment(Element.ALIGN_CENTER); c4.setPadding(4); table.addCell(c4);
                    String res = s.getResult()!=null?s.getResult().toUpperCase():"--";
                    Font resFont = new Font(Font.HELVETICA, 8, Font.BOLD, res.equals("PASS")?new java.awt.Color(21,128,61):res.equals("FAIL")?new java.awt.Color(220,38,38):new java.awt.Color(23,32,51));
                    PdfPCell c5 = new PdfPCell(new Phrase(res, resFont));
                    c5.setHorizontalAlignment(Element.ALIGN_CENTER); c5.setPadding(4); table.addCell(c5);
                }

                doc.add(table);

                // Summary
                Paragraph summary = new Paragraph("\nSummary: Total " + all.size() + " students ranked by CGPA. This PDF is generated from MySQL via Spring Boot and corresponds to Rank Analysis.", smallFont);
                summary.setSpacingBefore(10);
                doc.add(summary);
            }

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] generateRankPdfFiltered(String branch, String semester) {
        List<Student> all = studentRepository.findAll();
        for (Student s : all) { s.getResults().size(); if (s.getResults()!=null) for(SubjectResult sr: s.getResults()) sr.getGrade(); }
        // Filter by branch/semester if provided
        List<Student> filtered = new java.util.ArrayList<>();
        for(Student s: all){
            boolean branchOk = branch==null || branch.trim().isEmpty() || (s.getBranch()!=null && s.getBranch().equalsIgnoreCase(branch.trim()));
            boolean semOk = true;
            if(semester!=null && !semester.trim().isEmpty()){
                String norm = semester.trim().toLowerCase();
                String studSem = s.getSemester()!=null?s.getSemester().toLowerCase():"";
                boolean semMatch = studSem.contains(norm) || norm.contains(studSem);
                if(!semMatch && s.getResults()!=null){
                    for(SubjectResult sr: s.getResults()){
                        if(sr.getSemester()!=null && sr.getSemester().toLowerCase().contains(norm)){ semMatch=true; break; }
                    }
                }
                semOk = semMatch;
            }
            if(branchOk && semOk) filtered.add(s);
        }
        if(filtered.isEmpty()){
            // Return empty PDF with message
            try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
                com.lowagie.text.Document doc = new com.lowagie.text.Document(com.lowagie.text.PageSize.A4, 36, 36, 36, 36);
                com.lowagie.text.pdf.PdfWriter.getInstance(doc, baos);
                doc.open();
                com.lowagie.text.Font titleFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 18, com.lowagie.text.Font.BOLD, new java.awt.Color(37,99,235));
                com.lowagie.text.Paragraph title = new com.lowagie.text.Paragraph("Rank Analysis — No data for selected criteria", titleFont);
                title.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
                doc.add(title);
                com.lowagie.text.Paragraph p = new com.lowagie.text.Paragraph("No student data available for Department="+(branch!=null?branch:"All")+" Semester="+(semester!=null?semester:"All"), new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 10, com.lowagie.text.Font.NORMAL, new java.awt.Color(100,116,139)));
                p.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
                doc.add(p);
                doc.close();
                return baos.toByteArray();
            } catch (Exception e) { throw new RuntimeException(e); }
        }
        // Reuse main generation but with filtered list
        for (Student s : filtered) studentService.calculateStudentData(s);
        filtered.sort((a,b)->{
            Double ca = a.getCgpa(); Double cb = b.getCgpa();
            if (ca==null && cb==null) return 0;
            if (ca==null) return 1;
            if (cb==null) return -1;
            int r = Double.compare(cb,ca);
            if(r!=0) return r;
            return String.valueOf(a.getUsn()).compareToIgnoreCase(String.valueOf(b.getUsn()));
        });
        int rank=1; for(Student s: filtered) s.setstudentRank(rank++);
        // Generate PDF similar to main but with filtered
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
            com.lowagie.text.Document doc = new com.lowagie.text.Document(com.lowagie.text.PageSize.A4, 36, 36, 36, 36);
            com.lowagie.text.pdf.PdfWriter.getInstance(doc, baos);
            doc.open();
            com.lowagie.text.Font titleFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 18, com.lowagie.text.Font.BOLD, new java.awt.Color(37,99,235));
            com.lowagie.text.Font subFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 10, com.lowagie.text.Font.NORMAL, new java.awt.Color(100,116,139));
            com.lowagie.text.Font headerFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 9, com.lowagie.text.Font.BOLD, java.awt.Color.WHITE);
            com.lowagie.text.Font cellFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.NORMAL, new java.awt.Color(23,32,51));
            com.lowagie.text.Paragraph title = new com.lowagie.text.Paragraph("College Result Analysis — Rank Analysis" + (branch!=null&&!branch.isEmpty()?" — "+branch:"") + (semester!=null&&!semester.isEmpty()?" — Sem "+semester:""), titleFont);
            title.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
            doc.add(title);
            com.lowagie.text.Paragraph sub = new com.lowagie.text.Paragraph("Generated on " + java.time.LocalDate.now() + "  |  Students: " + filtered.size() + " | Filter: Dept="+(branch!=null?branch:"All")+" Semester="+(semester!=null?semester:"All"), subFont);
            sub.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
            sub.setSpacingAfter(12);
            doc.add(sub);
            com.lowagie.text.pdf.PdfPTable table = new com.lowagie.text.pdf.PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1,2.2f,3.5f,1.8f,1.2f,1.5f});
            table.setHeaderRows(1);
            String[] headers = {"Rank","USN","Name","Branch","CGPA","Result"};
            for (String h : headers) {
                com.lowagie.text.pdf.PdfPCell hc = new com.lowagie.text.pdf.PdfPCell(new com.lowagie.text.Phrase(h, headerFont));
                hc.setBackgroundColor(new java.awt.Color(37,99,235));
                hc.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER);
                hc.setVerticalAlignment(com.lowagie.text.Element.ALIGN_MIDDLE);
                hc.setPadding(6);
                table.addCell(hc);
            }
            for (Student s : filtered) {
                com.lowagie.text.pdf.PdfPCell c0 = new com.lowagie.text.pdf.PdfPCell(new com.lowagie.text.Phrase(String.valueOf(s.getstudentRank()), cellFont));
                c0.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER); c0.setPadding(4); table.addCell(c0);
                com.lowagie.text.pdf.PdfPCell c1 = new com.lowagie.text.pdf.PdfPCell(new com.lowagie.text.Phrase(s.getUsn()!=null?s.getUsn():"--", cellFont));
                c1.setPadding(4); table.addCell(c1);
                com.lowagie.text.pdf.PdfPCell c2 = new com.lowagie.text.pdf.PdfPCell(new com.lowagie.text.Phrase(s.getName()!=null?s.getName():"--", cellFont));
                c2.setPadding(4); table.addCell(c2);
                com.lowagie.text.pdf.PdfPCell c3 = new com.lowagie.text.pdf.PdfPCell(new com.lowagie.text.Phrase(s.getBranch()!=null?s.getBranch():"--", cellFont));
                c3.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER); c3.setPadding(4); table.addCell(c3);
                com.lowagie.text.pdf.PdfPCell c4 = new com.lowagie.text.pdf.PdfPCell(new com.lowagie.text.Phrase(s.getCgpa()!=null?String.format("%.2f", s.getCgpa()):"--", cellFont));
                c4.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER); c4.setPadding(4); table.addCell(c4);
                String res = s.getResult()!=null?s.getResult().toUpperCase():"--";
                com.lowagie.text.Font resFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 8, com.lowagie.text.Font.BOLD, res.equals("PASS")?new java.awt.Color(21,128,61):res.equals("FAIL")?new java.awt.Color(220,38,38):new java.awt.Color(23,32,51));
                com.lowagie.text.pdf.PdfPCell c5 = new com.lowagie.text.pdf.PdfPCell(new com.lowagie.text.Phrase(res, resFont));
                c5.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER); c5.setPadding(4); table.addCell(c5);
            }
            doc.add(table);
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Transactional(readOnly = true)
    public byte[] generateRankPdfForStudentFiltered(String usn, String semester) {
        var opt = studentRepository.findByUsnIgnoreCase(usn);
        if(opt.isEmpty()) return generateRankPdfForStudent(usn);
        Student s = opt.get();
        s.getResults().size();
        studentService.calculateStudentData(s);
        // If semester filter, ensure student has that semester, otherwise show empty
        if(semester!=null && !semester.trim().isEmpty()){
            String norm = semester.trim().toLowerCase();
            boolean hasSem = false;
            if(s.getSemester()!=null && s.getSemester().toLowerCase().contains(norm)) hasSem=true;
            else if(s.getResults()!=null){
                for(SubjectResult sr: s.getResults()) if(sr.getSemester()!=null && sr.getSemester().toLowerCase().contains(norm)) { hasSem=true; break; }
            }
            if(!hasSem){
                // Return PDF with no data message
                try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
                    com.lowagie.text.Document doc = new com.lowagie.text.Document(com.lowagie.text.PageSize.A4, 36, 36, 36, 36);
                    com.lowagie.text.pdf.PdfWriter.getInstance(doc, baos);
                    doc.open();
                    com.lowagie.text.Font titleFont = new com.lowagie.text.Font(com.lowagie.text.Font.HELVETICA, 18, com.lowagie.text.Font.BOLD, new java.awt.Color(37,99,235));
                    com.lowagie.text.Paragraph title = new com.lowagie.text.Paragraph("My Rank — " + usn + " — No data for Sem " + semester, titleFont);
                    title.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
                    doc.add(title);
                    doc.close();
                    return baos.toByteArray();
                } catch (Exception e) { throw new RuntimeException(e); }
            }
        }
        return generateRankPdfForStudent(usn);
    }

    @Transactional(readOnly = true)
    public byte[] generateRankPdfForStudent(String usn) {
        var opt = studentRepository.findByUsnIgnoreCase(usn);
        if(opt.isEmpty()) {
            // Return empty PDF with just header
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
                PdfWriter.getInstance(doc, baos);
                doc.open();
                Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, new java.awt.Color(37,99,235));
                Paragraph title = new Paragraph("My Rank — " + usn, titleFont);
                title.setAlignment(Element.ALIGN_CENTER);
                doc.add(title);
                Paragraph p = new Paragraph("No data found for USN " + usn, new Font(Font.HELVETICA, 10, Font.NORMAL, new java.awt.Color(100,116,139)));
                p.setAlignment(Element.ALIGN_CENTER);
                doc.add(p);
                doc.close();
                return baos.toByteArray();
            } catch (Exception e) { throw new RuntimeException(e); }
        }
        Student s = opt.get();
        s.getResults().size();
        studentService.calculateStudentData(s);
        // Calculate rank among all
        List<Student> all = studentRepository.findAll();
        for(Student st: all) { st.getResults().size(); studentService.calculateStudentData(st); }
        all.sort((a,b)->{
            Double ca = a.getCgpa(); Double cb = b.getCgpa();
            if (ca==null && cb==null) return 0;
            if (ca==null) return 1;
            if (cb==null) return -1;
            int r = Double.compare(cb,ca);
            if(r!=0) return r;
            return String.valueOf(a.getUsn()).compareToIgnoreCase(String.valueOf(b.getUsn()));
        });
        int rank=1; for(Student st: all){ st.setstudentRank(rank++); if(st.getUsn().equalsIgnoreCase(usn)) s.setstudentRank(st.getstudentRank()); }
        // Generate PDF with single student
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(doc, baos);
            doc.open();
            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, new java.awt.Color(37,99,235));
            Font subFont = new Font(Font.HELVETICA, 10, Font.NORMAL, new java.awt.Color(100,116,139));
            Font headerFont = new Font(Font.HELVETICA, 9, Font.BOLD, java.awt.Color.WHITE);
            Font cellFont = new Font(Font.HELVETICA, 8, Font.NORMAL, new java.awt.Color(23,32,51));
            Paragraph title = new Paragraph("My Rank — " + s.getUsn(), titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);
            Paragraph sub = new Paragraph("Name: " + (s.getName()!=null?s.getName():"--") + " | Branch: " + (s.getBranch()!=null?s.getBranch():"--") + " | CGPA: " + (s.getCgpa()!=null?String.format("%.2f",s.getCgpa()):"--") + " | Rank: " + s.getstudentRank(), subFont);
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(12);
            doc.add(sub);
            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1,2.2f,3.5f,1.8f,1.2f,1.5f});
            table.setHeaderRows(1);
            String[] headers = {"Rank","USN","Name","Branch","CGPA","Result"};
            for (String h : headers) {
                PdfPCell hc = new PdfPCell(new Phrase(h, headerFont));
                hc.setBackgroundColor(new java.awt.Color(37,99,235));
                hc.setHorizontalAlignment(Element.ALIGN_CENTER);
                hc.setVerticalAlignment(Element.ALIGN_MIDDLE);
                hc.setPadding(6);
                table.addCell(hc);
            }
            PdfPCell c0 = new PdfPCell(new Phrase(String.valueOf(s.getstudentRank()), cellFont));
            c0.setHorizontalAlignment(Element.ALIGN_CENTER); c0.setPadding(4); table.addCell(c0);
            PdfPCell c1 = new PdfPCell(new Phrase(s.getUsn()!=null?s.getUsn():"--", cellFont));
            c1.setPadding(4); table.addCell(c1);
            PdfPCell c2 = new PdfPCell(new Phrase(s.getName()!=null?s.getName():"--", cellFont));
            c2.setPadding(4); table.addCell(c2);
            PdfPCell c3 = new PdfPCell(new Phrase(s.getBranch()!=null?s.getBranch():"--", cellFont));
            c3.setHorizontalAlignment(Element.ALIGN_CENTER); c3.setPadding(4); table.addCell(c3);
            PdfPCell c4 = new PdfPCell(new Phrase(s.getCgpa()!=null?String.format("%.2f", s.getCgpa()):"--", cellFont));
            c4.setHorizontalAlignment(Element.ALIGN_CENTER); c4.setPadding(4); table.addCell(c4);
            String res = s.getResult()!=null?s.getResult().toUpperCase():"--";
            Font resFont = new Font(Font.HELVETICA, 8, Font.BOLD, res.equals("PASS")?new java.awt.Color(21,128,61):res.equals("FAIL")?new java.awt.Color(220,38,38):new java.awt.Color(23,32,51));
            PdfPCell c5 = new PdfPCell(new Phrase(res, resFont));
            c5.setHorizontalAlignment(Element.ALIGN_CENTER); c5.setPadding(4); table.addCell(c5);
            doc.add(table);
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
