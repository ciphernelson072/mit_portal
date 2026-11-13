import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

public final class OfficePackGenerator {
    private static final String[] FILES = {
        "01-system-architecture", "02-dfd-context", "03-dfd-level1",
        "04-erd-academic-core", "05-erd-admissions-finance", "06-uml-use-cases",
        "07-uml-sequence-authentication", "08-uml-sequence-teacher-workflow"
    };
    private static final String[] TITLES = {
        "System Architecture", "DFD 0 — System Context", "DFD 1 — Processes and Data Stores",
        "ERD 1 — Identity and Academic Core", "ERD 2 — Admissions and Finance",
        "UML Use Cases — Role Capabilities", "UML Sequence — Authentication and Access Control",
        "UML Sequence — Teacher Roster, Photo, and CSV"
    };
    private static final String[] DASHBOARD_FILES = {
        "student-dashboard", "teacher-dashboard", "administrator-dashboard"
    };
    private static final String[] DASHBOARD_TITLES = {
        "Student Dashboard · Illustrative Mock Data",
        "Teacher Dashboard · Illustrative Mock Data",
        "Administrator Dashboard · Illustrative Mock Data"
    };

    public static void main(String[] args) throws Exception {
        Path pack = Path.of(args[0]);
        Path diagrams = pack.resolve("diagrams");
        createPowerPoint(pack, diagrams);
        createWord(pack, diagrams);
    }

    private static void createPowerPoint(Path pack, Path diagrams) throws Exception {
        try (XMLSlideShow deck = new XMLSlideShow()) {
            deck.setPageSize(new Dimension(960, 540));
            addTitleSlide(deck);
            for (int i = 0; i < FILES.length; i++) {
                XSLFSlide slide = deck.createSlide();
                File image = diagrams.resolve(FILES[i] + ".png").toFile();
                byte[] bytes;
                try (FileInputStream input = new FileInputStream(image)) {
                    bytes = input.readAllBytes();
                }
                XSLFPictureShape picture = slide.createPicture(deck.addPicture(bytes, PictureData.PictureType.PNG));
                java.awt.image.BufferedImage bitmap = ImageIO.read(image);
                double scale = Math.min(920.0 / bitmap.getWidth(), 490.0 / bitmap.getHeight());
                double width = bitmap.getWidth() * scale;
                double height = bitmap.getHeight() * scale;
                picture.setAnchor(new Rectangle2D.Double((960 - width) / 2, (540 - height) / 2, width, height));
            }
            for (int i = 0; i < DASHBOARD_FILES.length; i++) {
                XSLFSlide slide = deck.createSlide();
                addText(slide, DASHBOARD_TITLES[i], 0.35, 0.12, 12.6, 0.55, 20, true, new Color(23, 50, 77));
                File image = pack.resolve("screenshots").resolve(DASHBOARD_FILES[i] + ".png").toFile();
                byte[] bytes;
                try (FileInputStream input = new FileInputStream(image)) {
                    bytes = input.readAllBytes();
                }
                XSLFPictureShape picture = slide.createPicture(deck.addPicture(bytes, PictureData.PictureType.PNG));
                java.awt.image.BufferedImage bitmap = ImageIO.read(image);
                double scale = Math.min(920.0 / bitmap.getWidth(), 440.0 / bitmap.getHeight());
                double width = bitmap.getWidth() * scale;
                double height = bitmap.getHeight() * scale;
                picture.setAnchor(new Rectangle2D.Double((960 - width) / 2, 58 + (440 - height) / 2, width, height));
            }
            XSLFSlide finalSlide = deck.createSlide();
            finalSlide.getBackground().setFillColor(new Color(241, 246, 251));
            addText(finalSlide, "Design decisions, limitations, and next steps", 0.7, 0.75, 12.0, 0.6, 28, true, new Color(23, 50, 77));
            addText(finalSlide,
                "• Authorization is enforced by the backend; UI visibility is not the security boundary.\n" +
                "• Quick Tunnel is temporary testing ingress, not a permanent service address.\n" +
                "• Provider integrations and operational controls must be qualified against the current readiness checklist.\n" +
                "• Live health checks do not replace authenticated role-by-role acceptance testing.\n" +
                "• Present synthetic data only; do not include credentials, database dumps, or real personal records.\n" +
                "• These diagrams are bounded views, not an exhaustive catalog of every portal domain.",
                0.9, 1.8, 11.6, 4.5, 20, false, new Color(51, 73, 92));
            try (FileOutputStream output = new FileOutputStream(pack.resolve("Student_Portal_System_Analysis_With_Dashboard_Screenshots.pptx").toFile())) {
                deck.write(output);
            }
        }
    }

    private static void addTitleSlide(XMLSlideShow deck) {
        XSLFSlide slide = deck.createSlide();
        slide.getBackground().setFillColor(new Color(238, 245, 251));
        addText(slide, "Student Portal", 0.8, 1.4, 11.8, 0.8, 38, true, new Color(23, 50, 77));
        addText(slide, "System Analysis and Design", 0.85, 2.3, 11.6, 0.7, 28, true, new Color(47, 111, 159));
        addText(slide, "Architecture · Data flows · ERDs · UML use cases and sequences", 0.9, 3.3, 11.2, 0.6, 20, false, new Color(64, 87, 107));
        addText(slide, "Code-informed presentation pack  |  20 October 2026", 0.9, 5.8, 11.2, 0.5, 15, false, new Color(99, 119, 138));
        addText(slide, "Selected views · confirm environment and integration readiness before presenting", 0.9, 6.3, 11.2, 0.5, 14, false, new Color(99, 119, 138));
    }

    private static void addText(XSLFSlide slide, String text, double x, double y, double w, double h, int size, boolean bold, Color color) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle2D.Double(x * 72, y * 72, w * 72, h * 72));
        box.setFillColor(new Color(0, 0, 0, 0));
        box.setLineColor(new Color(0, 0, 0, 0));
        XSLFTextParagraph paragraph = box.addNewTextParagraph();
        XSLFTextRun run = paragraph.addNewTextRun();
        run.setText(text);
        run.setFontFamily("Aptos");
        run.setFontSize((double) size);
        run.setBold(bold);
        run.setFontColor(color);
    }

    private static void createWord(Path pack, Path diagrams) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createStyles();
            XWPFParagraph title = document.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            titleRun.setText("Student Portal — System Analysis and Design");
            titleRun.setBold(true);
            titleRun.setFontSize(24);
            titleRun.setFontFamily("Aptos Display");
            titleRun.setColor("17324D");

            XWPFParagraph subtitle = document.createParagraph();
            subtitle.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun subtitleRun = subtitle.createRun();
            subtitleRun.setText("Code-informed presentation companion · 20 October 2026");
            subtitleRun.setFontSize(12);
            subtitleRun.setColor("587087");

            heading(document, "Purpose and scope", 1);
            paragraph(document, "This companion describes selected system-design views of the Student Information Portal: its current application architecture, major data flows, a subset of the relational model, role-based use cases, and representative authentication and teacher workflow sequences.");
            paragraph(document, "The diagrams are based on the code and schema artifacts reviewed for this pack. They are deliberately bounded for presentation clarity and should not be treated as an exhaustive feature or entity inventory.");
            heading(document, "Recommended presentation flow", 1);
            paragraph(document, "Introduce the portal and its actors; explain the system boundary and architecture; walk through DFD context and level 1; use the two ERDs to describe academic and admissions/finance data; then explain role use cases and one sequence. Conclude with limitations, readiness checks, and the next acceptance-test steps.");
            heading(document, "Trust and implementation notes", 1);
            paragraph(document, "Protected operations rely on backend authorization and ownership checks. The browser is a client, not the security boundary. Private files require authorized server-side access. The temporary Quick Tunnel is a testing route and must not be presented as a stable production endpoint.");
            paragraph(document, "Payment attempts have database foreign keys to fees and students, and reconciliation entries reference payment attempts. Admission application approval and student-account provisioning are workflow-related but are not represented as a direct application-to-student foreign key in the selected ERD.");
            paragraph(document, "A configured email or payment-related screen does not by itself establish provider integration readiness. Check the project integration and release checklist, and verify the exact pilot environment before quoting database, migration, operational, or provider status.");
            heading(document, "Presentation and confidentiality checklist", 1);
            paragraph(document, "Use synthetic data. Do not share .env files, secrets, tokens, MFA material, database dumps, real student/staff/applicant information, payment records, internal hostnames, or the temporary tunnel URL as a permanent address. Keep demo access details separate and share them only through an approved secure channel.");
            paragraph(document, "A live health check is not a substitute for authenticated acceptance testing of student, teacher, and administrator workflows. Present optional or partial integrations as such and clearly distinguish current implementation from proposed or pending operational controls.");

            heading(document, "Current dashboards — illustrative mock data", 1);
            paragraph(document, "These screenshots use the project's current dashboard HTML and styles, with JavaScript/API calls disabled and clearly synthetic sample values inserted for presentation. They are not authenticated captures and do not show live account or system data.");
            for (int i = 0; i < FILES.length; i++) {
                document.createParagraph().createRun().addBreak(BreakType.PAGE);
                heading(document, TITLES[i], 1);
                File image = diagrams.resolve(FILES[i] + ".png").toFile();
                java.awt.image.BufferedImage bitmap = ImageIO.read(image);
                Dimension dimensions = new Dimension(bitmap.getWidth(), bitmap.getHeight());
                double aspect = dimensions.width / (double) dimensions.height;
                double widthInches = Math.min(6.4, 9.0 * aspect);
                double heightInches = widthInches / aspect;
                XWPFParagraph figure = document.createParagraph();
                figure.setAlignment(ParagraphAlignment.CENTER);
                try (FileInputStream input = new FileInputStream(image)) {
                    figure.createRun().addPicture(input, Document.PICTURE_TYPE_PNG, image.getName(),
                        org.apache.poi.util.Units.toEMU(widthInches), org.apache.poi.util.Units.toEMU(heightInches));
                }
                XWPFParagraph caption = document.createParagraph();
                caption.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun captionRun = caption.createRun();
                captionRun.setText(TITLES[i] + " — selected code-informed view.");
                captionRun.setItalic(true);
                captionRun.setFontSize(10);
                captionRun.setColor("587087");
            }
            for (int i = 0; i < DASHBOARD_FILES.length; i++) {
                document.createParagraph().createRun().addBreak(BreakType.PAGE);
                heading(document, DASHBOARD_TITLES[i], 1);
                File image = pack.resolve("screenshots").resolve(DASHBOARD_FILES[i] + ".png").toFile();
                XWPFParagraph figure = document.createParagraph();
                figure.setAlignment(ParagraphAlignment.CENTER);
                try (FileInputStream input = new FileInputStream(image)) {
                    figure.createRun().addPicture(input, Document.PICTURE_TYPE_PNG, image.getName(),
                        org.apache.poi.util.Units.toEMU(6.4), org.apache.poi.util.Units.toEMU(3.6));
                }
                XWPFParagraph caption = document.createParagraph();
                caption.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun captionRun = caption.createRun();
                captionRun.setText("Illustrative dashboard view · synthetic data only · not a live user account.");
                captionRun.setItalic(true);
                captionRun.setFontSize(10);
                captionRun.setColor("587087");
            }
            try (FileOutputStream output = new FileOutputStream(pack.resolve("Student_Portal_System_Analysis_With_Dashboard_Screenshots.docx").toFile())) {
                document.write(output);
            }
        }
    }

    private static void heading(XWPFDocument document, String text, int level) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setStyle("Heading" + level);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontFamily("Aptos Display");
        run.setColor("17324D");
    }

    private static void paragraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setSpacingAfter(120);
        XWPFRun run = paragraph.createRun();
        run.setText(text);
        run.setFontFamily("Aptos");
        run.setFontSize(11);
    }
}
