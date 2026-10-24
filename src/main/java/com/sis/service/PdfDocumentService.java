package com.sis.service;

import com.sis.dto.AcademicReportDto;
import com.sis.dto.CourseGradeDto;
import com.sis.entity.Exam;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PdfDocumentService {

    private static final float LEFT = 54;
    private static final float TOP = 760;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final String SCHOOL_NAME = "Makerere Institute of Technology";
    private static final String SCHOOL_ADDRESS = "Student Information Portal | Kampala, Uganda";
    private static final String SCHOOL_EMAIL = "registrar@mit.ac.ug";

    public byte[] academicReport(AcademicReportDto report) throws IOException {
        return renderAcademicTranscript(report);
    }

    public byte[] invoice(String invoiceNumber, String description, Object amount, Object amountPaid,
                          Object balance, Object dueDate, String status) throws IOException {
        return render("FEE INVOICE", List.of(
                "Invoice number: " + invoiceNumber,
                "Description: " + description,
                "Amount: UGX " + amount,
                "Amount paid: UGX " + amountPaid,
                "Balance due: UGX " + balance,
                "Due date: " + dueDate,
                "Status: " + status,
                "Issued: " + LocalDateTime.now().format(DATE_TIME)
        ));
    }

    public byte[] receipt(String receiptNumber, Object transactionId, Object feeId, Object amount,
                          String status, Object createdAt) throws IOException {
        return render("PAYMENT RECEIPT", List.of(
                "Receipt number: " + receiptNumber,
                "Transaction ID: " + transactionId,
                "Fee ID: " + feeId,
                "Amount paid: UGX " + amount,
                "Status: " + status,
                "Payment date: " + createdAt
        ));
    }

    public byte[] hallTicket(List<Exam> exams) throws IOException {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("Issued: " + LocalDateTime.now().format(DATE_TIME));
        lines.add("");
        if (exams.isEmpty()) {
            lines.add("No examinations are currently scheduled.");
        } else {
            for (Exam exam : exams) {
                lines.add(exam.getTitle() + " | " + exam.getStartsAt() + " | " + exam.getVenue());
            }
        }
        return render("EXAMINATION HALL TICKET", lines);
    }

    public byte[] serviceRequest(String requestNumber, String type, String subject, String details,
                                 String status, Object createdAt) throws IOException {
        return render("SERVICE REQUEST", List.of(
                "Request number: " + requestNumber,
                "Service type: " + type,
                "Subject: " + subject,
                "Details: " + details,
                "Status: " + status,
                "Submitted: " + createdAt
        ));
    }

    public byte[] report(String title, List<String> lines) throws IOException {
        return render(title, lines == null ? List.of() : lines);
    }

    private List<String> linesForAcademicReport(AcademicReportDto report) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("Student: " + report.getStudentName());
        lines.add("Class / Program: " + report.getClassName());
        lines.add("CGPA: " + report.getCgpa());
        lines.add("Average grade: " + report.getAverageGrade());
        lines.add("Total courses: " + report.getTotalCourses());
        lines.add("");
        for (CourseGradeDto grade : report.getCourseGrades()) {
            lines.add(grade.getCourseName() + " | " + grade.getGrade() + " | GPA " + grade.getGpaScore()
                    + " | Credits " + grade.getCredits());
        }
        lines.add("");
        lines.add("Summary: " + report.getPerformanceSummary());
        lines.add("Generated: " + report.getGeneratedDate());
        return lines;
    }

    private byte[] renderAcademicTranscript(AcademicReportDto report) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                drawTranscriptHeader(document, page, content, report);
                drawTranscriptMetadata(content, report);
                drawTranscriptCourseGrid(content, report);
                drawTranscriptFooter(content, report);
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private void drawTranscriptHeader(PDDocument document, PDPage page, PDPageContentStream content, AcademicReportDto report) throws IOException {
        content.setNonStrokingColor(new PDColor(new float[]{0.0f, 0.17f, 0.38f}, PDDeviceRGB.INSTANCE));
        content.addRect(36, 36, page.getMediaBox().getWidth() - 72, page.getMediaBox().getHeight() - 72);
        content.stroke();

        content.addRect(36, 738, page.getMediaBox().getWidth() - 72, 42);
        content.fill();
        content.setNonStrokingColor(new PDColor(new float[]{1f, 1f, 1f}, PDDeviceRGB.INSTANCE));
        content.beginText();
        content.setFont(PDType1Font.HELVETICA_BOLD, 20);
        content.newLineAtOffset(80, 750);
        content.showText("UGANDA CHRISTIAN");
        content.newLineAtOffset(0, -18);
        content.showText("UNIVERSITY");
        content.endText();

        content.setNonStrokingColor(new PDColor(new float[]{0.12f, 0.12f, 0.12f}, PDDeviceRGB.INSTANCE));
        content.beginText();
        content.setFont(PDType1Font.HELVETICA, 9);
        content.newLineAtOffset(380, 751);
        content.showText("P.O Box, Mukono, Uganda");
        content.newLineAtOffset(0, -14);
        content.showText("Email: registrar@ucu.ac.ug");
        content.newLineAtOffset(0, -14);
        content.showText("Website: www.ucu.ac.ug");
        content.endText();

        try {
            ClassPathResource logoResource = new ClassPathResource("static/icons/logo.jpg");
            if (logoResource.exists()) {
                PDImageXObject logo = PDImageXObject.createFromByteArray(document,
                        logoResource.getInputStream().readAllBytes(), "mit-logo");
                content.drawImage(logo, 42, 750, 30, 30);
            }
        } catch (IOException ignored) {
        }
    }

    private void drawTranscriptMetadata(PDPageContentStream content, AcademicReportDto report) throws IOException {
        content.setNonStrokingColor(new PDColor(new float[]{0.1f, 0.1f, 0.1f}, PDDeviceRGB.INSTANCE));
        content.beginText();
        content.setFont(PDType1Font.HELVETICA_BOLD, 14);
        content.newLineAtOffset(70, 700);
        content.showText("Name: ");
        content.setFont(PDType1Font.HELVETICA, 12);
        content.showText(report.getStudentName() == null ? "N/A" : report.getStudentName());
        content.newLineAtOffset(250, 0);
        content.setFont(PDType1Font.HELVETICA_BOLD, 14);
        content.showText("Campus: ");
        content.setFont(PDType1Font.HELVETICA, 12);
        content.showText(report.getClassName() == null ? "Main Campus" : "Main Campus");
        content.endText();

        content.beginText();
        content.setFont(PDType1Font.HELVETICA_BOLD, 12);
        content.newLineAtOffset(70, 680);
        content.showText("Registration Number:");
        content.setFont(PDType1Font.HELVETICA, 12);
        content.newLineAtOffset(170, 0);
        content.showText(report.getStudentId() == null ? "—" : String.valueOf(report.getStudentId()));
        content.newLineAtOffset(185, 0);
        content.setFont(PDType1Font.HELVETICA_BOLD, 12);
        content.showText("Current Year:");
        content.setFont(PDType1Font.HELVETICA, 12);
        content.newLineAtOffset(95, 0);
        content.showText("Year 1");
        content.endText();

        content.beginText();
        content.setFont(PDType1Font.HELVETICA_BOLD, 12);
        content.newLineAtOffset(70, 660);
        content.showText("Access Number:");
        content.setFont(PDType1Font.HELVETICA, 12);
        content.newLineAtOffset(140, 0);
        content.showText("—");
        content.newLineAtOffset(180, 0);
        content.setFont(PDType1Font.HELVETICA_BOLD, 12);
        content.showText("Current CGPA:");
        content.setFont(PDType1Font.HELVETICA, 12);
        content.newLineAtOffset(95, 0);
        content.showText(report.getCgpa() == null ? "0.00" : report.getCgpa().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        content.endText();

        content.beginText();
        content.setFont(PDType1Font.HELVETICA_BOLD, 14);
        content.newLineAtOffset(160, 628);
        content.showText("Faculty: Faculty of Agricultural Sciences");
        content.endText();

        content.beginText();
        content.setFont(PDType1Font.HELVETICA_BOLD, 14);
        content.newLineAtOffset(160, 610);
        content.showText("Program: Bachelor of Agricultural Science and Entrepreneurship");
        content.endText();
    }

    private void drawTranscriptCourseGrid(PDPageContentStream content, AcademicReportDto report) throws IOException {
        content.setNonStrokingColor(new PDColor(new float[]{0.0f, 0.0f, 0.0f}, PDDeviceRGB.INSTANCE));
        content.beginText();
        content.setFont(PDType1Font.HELVETICA_BOLD, 11);
        content.newLineAtOffset(70, 570);
        content.showText("Year 1");
        content.newLineAtOffset(245, 0);
        content.showText("Year 1");
        content.endText();

        content.beginText();
        content.setFont(PDType1Font.HELVETICA_BOLD, 11);
        content.newLineAtOffset(70, 555);
        content.showText("Semester 1");
        content.newLineAtOffset(245, 0);
        content.showText("Semester 2");
        content.endText();

        List<CourseGradeDto> courses = report.getCourseGrades() == null ? List.of() : report.getCourseGrades();
        int leftSize = Math.min(courses.size(), Math.max(1, (courses.size() + 1) / 2));
        int rightSize = Math.max(0, courses.size() - leftSize);

        drawCourseColumn(content, 70, 520, leftSize, courses.subList(0, leftSize), true);
        drawCourseColumn(content, 360, 520, rightSize, courses.subList(leftSize, courses.size()), false);
    }

    private void drawCourseColumn(PDPageContentStream content, float x, float y, int count, List<CourseGradeDto> rows, boolean left) throws IOException {
        float columnX = x;
        float currentY = y;

        content.beginText();
        content.setFont(PDType1Font.HELVETICA_BOLD, 10);
        content.newLineAtOffset(columnX, currentY);
        content.showText(left ? "COURSE NAME" : "COURSE NAME");
        content.newLineAtOffset(120, 0);
        content.showText("MARK");
        content.newLineAtOffset(45, 0);
        content.showText("CU");
        content.newLineAtOffset(28, 0);
        content.showText("GP");
        content.endText();

        currentY -= 18;
        for (int index = 0; index < count; index++) {
            CourseGradeDto row = rows.get(index);
            if (currentY < 120) break;
            content.beginText();
            content.setFont(PDType1Font.HELVETICA, 9);
            content.newLineAtOffset(columnX, currentY);
            String courseName = row.getCourseName() == null ? "Course" : row.getCourseName();
            String courseLabel = courseName.length() > 26 ? courseName.substring(0, 26) : courseName;
            content.showText(courseLabel);
            content.newLineAtOffset(120, 0);
            content.showText(row.getGrade() == null ? "-" : row.getGrade());
            content.newLineAtOffset(38, 0);
            String credits = row.getCredits() == null ? "-" : String.valueOf(row.getCredits());
            content.showText(credits);
            content.newLineAtOffset(30, 0);
            content.showText(row.getGpaScore() == null ? "0.0" : row.getGpaScore().setScale(1, java.math.RoundingMode.HALF_UP).toPlainString());
            content.endText();
            currentY -= 16;
        }
    }

    private void drawTranscriptFooter(PDPageContentStream content, AcademicReportDto report) throws IOException {
        content.beginText();
        content.setFont(PDType1Font.HELVETICA_BOLD, 20);
        content.newLineAtOffset(115, 90);
        String gpa = report.getCgpa() == null ? "0.00" : report.getCgpa().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
        content.showText("GPA " + gpa);
        content.endText();

        content.beginText();
        content.setFont(PDType1Font.HELVETICA_BOLD, 13);
        content.newLineAtOffset(150, 118);
        content.showText("The results will be displayed as shown above");
        content.endText();

        if (Boolean.TRUE.equals(report.getApproved())) {
            content.beginText();
            content.setFont(PDType1Font.HELVETICA_BOLD, 12);
            content.newLineAtOffset(420, 90);
            content.showText("Approved by: " + (report.getApprovedBy() == null ? "ADMIN" : report.getApprovedBy()));
            content.newLineAtOffset(0, -14);
            content.showText("Approved at: " + (report.getApprovedAt() == null ? "N/A" : report.getApprovedAt()));
            content.endText();
        }
    }

    private byte[] render(String title, List<String> lines) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                drawHeader(document, page, content, title);
                content.beginText();
                content.setFont(PDType1Font.HELVETICA_BOLD, 16);
                content.newLineAtOffset(LEFT, TOP);
                content.newLineAtOffset(0, -78);
                content.showText(safe(title));
                content.setFont(PDType1Font.HELVETICA, 10);
                content.setLeading(15);
                content.newLine();
                content.newLine();
                for (String line : lines) {
                    for (String wrapped : wrap(safe(line), 105)) {
                        content.showText(wrapped);
                        content.newLine();
                    }
                }
                content.endText();
                drawFooter(page, content);
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private void drawHeader(PDDocument document, PDPage page, PDPageContentStream content, String title) throws IOException {
        try {
            ClassPathResource logoResource = new ClassPathResource("static/icons/logo.jpg");
            if (logoResource.exists()) {
                PDImageXObject logo = PDImageXObject.createFromByteArray(document,
                        logoResource.getInputStream().readAllBytes(), "mit-logo");
                content.drawImage(logo, LEFT, 700, 52, 52);
            }
        } catch (IOException ignored) {
            // Text branding remains available if the optional image asset is unavailable.
        }
        content.beginText();
        content.setFont(PDType1Font.HELVETICA_BOLD, 13);
        content.newLineAtOffset(118, 735);
        content.showText(SCHOOL_NAME);
        content.setFont(PDType1Font.HELVETICA, 9);
        content.newLineAtOffset(0, -15);
        content.showText(SCHOOL_ADDRESS);
        content.newLineAtOffset(0, -13);
        content.showText(SCHOOL_EMAIL);
        content.endText();
        content.moveTo(LEFT, 680);
        content.lineTo(page.getMediaBox().getWidth() - LEFT, 680);
        content.stroke();
    }

    private void drawFooter(PDPage page, PDPageContentStream content) throws IOException {
        content.beginText();
        content.setFont(PDType1Font.HELVETICA, 8);
        content.newLineAtOffset(LEFT, 34);
        content.showText(SCHOOL_NAME + " | Official digital document | Generated "
                + LocalDateTime.now().format(DATE_TIME));
        content.endText();
    }

    private List<String> wrap(String value, int width) {
        if (value.length() <= width) return List.of(value);
        List<String> result = new java.util.ArrayList<>();
        for (int start = 0; start < value.length(); start += width) {
            result.add(value.substring(start, Math.min(start + width, value.length())));
        }
        return result;
    }

    private String safe(String value) {
        return value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", "?");
    }
}