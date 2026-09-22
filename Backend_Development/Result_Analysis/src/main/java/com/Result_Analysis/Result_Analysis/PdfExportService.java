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
        // For now, ignore filters and generate same as full; could filter similarly to Excel
        return generateRankPdf();
    }
}
