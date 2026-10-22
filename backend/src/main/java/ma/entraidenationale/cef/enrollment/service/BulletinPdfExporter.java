package ma.entraidenationale.cef.enrollment.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import ma.entraidenationale.cef.common.PdfFonts;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.Bulletin;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.GradeView;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.SessionView;
import ma.entraidenationale.cef.enrollment.domain.EnrollmentStatus;
import ma.entraidenationale.cef.enrollment.domain.ExamResult;
import ma.entraidenationale.cef.enrollment.domain.ExamStatus;
import ma.entraidenationale.cef.enrollment.domain.SessionType;

/**
 * Bulletin de notes (بيان النقط) en PDF, A4 portrait : identité, notes par module pour la session normale et le
 * rattrapage, moyennes, issue, mention. Arabe (défaut, sens de lecture RTL) ou français ; police Noto Naskh Arabic
 * embarquée. Les chiffres sont toujours lus de gauche à droite.
 */
@Component
public class BulletinPdfExporter {

    private static final Color BRAND = new Color(0x0C, 0x47, 0x97);
    private static final Color SURFACE = new Color(0xF4, 0xF5, 0xF7);
    private static final Color HIGHLIGHT = new Color(0xE6, 0xEE, 0xF9);
    private static final Color BORDER = new Color(0x8A, 0x94, 0xA6);

    /** Libellés par langue. */
    private enum Labels {
        AR(true, "التعاون الوطني", "بيان النقط", "التكوين التأهيلي بمراكز التربية و التكوين - منصة التتبع",
                "الاسم الكامل", "رقم التسجيل", "المركز", "الشعبة", "الموسم التكويني", "تاريخ الامتحان", "الوضعية",
                "الوحدة", "المعامل", "معدل الوحدات (مرجح بالمعاملات)", "الامتحان النهائي", "المعدل العام", "النتيجة", "الميزة",
                "عتبة النجاح", "نجاح استثنائي", "الدبلوم", "غائب", "الدورة العادية", "دورة الاستدراك", "لا توجد نتائج بعد",
                "أُنجز بتاريخ"),
        FR(false, "Entraide Nationale", "Bulletin de notes", "Formation qualifiante dans les Centres d'Éducation et de Formation — Plateforme de suivi",
                "Nom complet", "N° d'inscription", "Centre", "Filière", "Saison de formation", "Date de l'examen", "Statut",
                "Module", "Coefficient", "Moyenne des modules (pondérée)", "Examen final", "Moyenne générale", "Résultat", "Mention",
                "Seuil de réussite", "Réussite exceptionnelle", "Diplôme", "Absent", "Session normale", "Session de rattrapage", "Aucun résultat saisi",
                "Généré le");

        final boolean rtl;
        final String org, title, app, name, reg, center, filiere, season, examDate, status, module, coef, modulesAvg, finalExam,
                overall, result, mention, threshold, exceptional, diploma, absent, normal, makeup, none, generated;

        Labels(boolean rtl, String org, String title, String app, String name, String reg, String center, String filiere, String season,
               String examDate, String status, String module, String coef, String modulesAvg, String finalExam, String overall,
               String result, String mention, String threshold, String exceptional, String diploma, String absent, String normal,
               String makeup, String none, String generated) {
            this.rtl = rtl;
            this.org = org;
            this.title = title;
            this.app = app;
            this.name = name;
            this.reg = reg;
            this.center = center;
            this.filiere = filiere;
            this.season = season;
            this.examDate = examDate;
            this.status = status;
            this.module = module;
            this.coef = coef;
            this.modulesAvg = modulesAvg;
            this.finalExam = finalExam;
            this.overall = overall;
            this.result = result;
            this.mention = mention;
            this.threshold = threshold;
            this.exceptional = exceptional;
            this.diploma = diploma;
            this.absent = absent;
            this.normal = normal;
            this.makeup = makeup;
            this.none = none;
            this.generated = generated;
        }

        static Labels of(String lang) {
            return "fr".equalsIgnoreCase(lang) ? FR : AR;
        }

        String result(ExamResult r) {
            return switch (r) {
                case PASSED -> rtl ? "ناجح" : "Admis";
                case FAILED -> rtl ? "راسب" : "Ajourné";
                case ABSENT -> rtl ? "غائب" : "Absent";
                case INCOMPLETE -> rtl ? "غير مكتمل" : "Incomplet";
            };
        }

        String status(EnrollmentStatus s) {
            return switch (s) {
                case ENROLLED -> rtl ? "مسجل" : "Inscrit";
                case DEPARTED -> rtl ? "مغادر" : "Parti";
                case GRADUATED -> rtl ? "خريج" : "Diplômé";
                case FAILED -> rtl ? "راسب" : "Recalé";
                case ABSENT -> rtl ? "غائب" : "Absent";
            };
        }

        /** La mention est calculée en arabe par le serveur (MentionScale) ; traduite ici pour le français. */
        String mention(String arabic) {
            if (arabic == null) {
                return "—";
            }
            if (rtl) {
                return arabic;
            }
            return switch (arabic) {
                case "مقبول" -> "Passable";
                case "مستحسن" -> "Assez bien";
                case "جيد" -> "Bien";
                case "جيد جدا" -> "Très bien";
                default -> arabic;
            };
        }
    }

    public byte[] export(Bulletin b, String lang) {
        Labels l = Labels.of(lang);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setRunDirection(dir(l));
            doc.addTitle(l.title + " " + b.registrationNumber());
            doc.open();
            Fonts f = new Fonts();
            doc.add(header(l, f));
            doc.add(identity(b, l, f));
            doc.add(grades(b, l, f));
            if (b.diploma() != null) {
                doc.add(simple(l.diploma + " : " + b.diploma().serialNumber(), f.bold(10), l, 10));
            }
            doc.add(simple(l.generated + " " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), f.normal(8), l, 14));
        } catch (DocumentException e) {
            throw new IllegalStateException("Échec de la génération du PDF", e);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ sections

    private PdfPTable header(Labels l, Fonts f) {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setRunDirection(dir(l));
        t.addCell(plain(l.org, f.normal(10), l, Element.ALIGN_CENTER, 2));
        t.addCell(plain(l.title, f.bold(20), l, Element.ALIGN_CENTER, 6));
        PdfPCell app = plain(l.app, f.normal(9), l, Element.ALIGN_CENTER, 2);
        app.setPaddingBottom(12);
        t.addCell(app);
        return t;
    }

    private PdfPTable identity(Bulletin b, Labels l, Fonts f) {
        PdfPTable t = new PdfPTable(new float[] {1, 2, 1, 2});
        t.setWidthPercentage(100);
        t.setRunDirection(dir(l));
        t.setSpacingAfter(12);
        String name = l.rtl ? b.beneficiaryNameAr() : (b.beneficiaryNameFr() != null ? b.beneficiaryNameFr() : b.beneficiaryNameAr());
        String center = l.rtl || b.centerNameFr() == null ? b.centerNameAr() : b.centerNameFr();
        String filiere = l.rtl || b.filiereNameFr() == null ? b.filiereNameAr() : b.filiereNameFr();
        pair(t, l.name, name, false, l, f);
        pair(t, l.reg, b.registrationNumber(), true, l, f);
        pair(t, l.center, center, false, l, f);
        pair(t, l.filiere, filiere, false, l, f);
        pair(t, l.season, b.seasonLabel(), true, l, f);
        pair(t, l.examDate, b.examDate() == null ? "—" : b.examDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), true, l, f);
        pair(t, l.status, l.status(b.status()), false, l, f);
        t.addCell(plain("", f.normal(9), l, start(l), 3));
        t.addCell(plain("", f.normal(9), l, start(l), 3));
        return t;
    }

    private void pair(PdfPTable t, String label, String value, boolean numeric, Labels l, Fonts f) {
        PdfPCell k = plain(label, f.normal(9, new Color(0x50, 0x5A, 0x68)), l, start(l), 3);
        k.setBorder(Rectangle.BOTTOM);
        k.setBorderColor(BORDER);
        PdfPCell v = plain(value, f.bold(10), l, start(l), 3);
        v.setBorder(Rectangle.BOTTOM);
        v.setBorderColor(BORDER);
        if (numeric) {
            v.setRunDirection(PdfWriter.RUN_DIRECTION_LTR);
            v.setHorizontalAlignment(l.rtl ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        }
        t.addCell(k);
        t.addCell(v);
    }

    private PdfPTable grades(Bulletin b, Labels l, Fonts f) {
        List<SessionView> sessions = b.sessions().stream()
                .sorted(Comparator.comparing(s -> s.type() == SessionType.NORMAL ? 0 : 1)).toList();
        if (sessions.isEmpty()) {
            PdfPTable t = new PdfPTable(1);
            t.setWidthPercentage(100);
            t.setRunDirection(dir(l));
            t.addCell(plain(l.none, f.normal(11), l, Element.ALIGN_CENTER, 12));
            return t;
        }
        int n = sessions.size();
        float[] widths = new float[2 + n];
        widths[0] = 5;
        widths[1] = 1.4f;
        for (int i = 0; i < n; i++) {
            widths[2 + i] = 1.8f;
        }
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        t.setRunDirection(dir(l));
        t.setHeaderRows(1);

        t.addCell(cell(l.module, f.header(), BRAND, l, Element.ALIGN_CENTER, false));
        t.addCell(cell(l.coef, f.header(), BRAND, l, Element.ALIGN_CENTER, false));
        for (SessionView s : sessions) {
            t.addCell(cell(s.type() == SessionType.NORMAL ? l.normal : l.makeup, f.header(), BRAND, l, Element.ALIGN_CENTER, false));
        }
        int start = l.rtl ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT;
        for (var m : b.modules()) {
            String name = l.rtl || m.nameFr() == null || m.nameFr().isBlank() ? m.nameAr() : m.nameFr();
            t.addCell(cell(name, f.normal(10), null, l, start, false));
            t.addCell(cell(num(m.coefficient()), f.normal(10), null, l, Element.ALIGN_CENTER, true));
            for (SessionView s : sessions) {
                t.addCell(cell(grade(s, m.id(), l), f.normal(10), null, l, Element.ALIGN_CENTER, true));
            }
        }
        summary(t, l.modulesAvg, sessions, s -> s.modulesAverage() == null ? "—" : num(s.modulesAverage()), SURFACE, f.bold(10), l);
        summary(t, l.finalExam, sessions, s -> s.finalExamStatus() == ExamStatus.ABSENT ? l.absent
                : s.finalExamGrade() == null ? "—" : num(s.finalExamGrade()), SURFACE, f.bold(10), l);
        summary(t, l.overall, sessions, s -> s.overallAverage() == null ? "—" : num(s.overallAverage()), HIGHLIGHT, f.bold(11), l);
        summary(t, l.threshold, sessions, s -> num(s.passThreshold()), null, f.normal(9), l);
        summary(t, l.result, sessions, s -> l.result(s.result()) + (s.exceptionalPass() ? " (" + l.exceptional + ")" : ""), null, f.bold(10), l);
        summary(t, l.mention, sessions, s -> l.mention(s.mention()), null, f.bold(10), l);
        return t;
    }

    private interface Value {
        String of(SessionView s);
    }

    /** Ligne de synthèse : libellé sur les deux premières colonnes, une valeur par session. */
    private void summary(PdfPTable t, String label, List<SessionView> sessions, Value value, Color bg, Font font, Labels l) {
        PdfPCell k = cell(label, font, bg, l, l.rtl ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT, false);
        k.setColspan(2);
        t.addCell(k);
        for (SessionView s : sessions) {
            String v = value.of(s);
            boolean numeric = v.matches("[0-9.,—\\- ]+");
            t.addCell(cell(v, font, bg, l, Element.ALIGN_CENTER, numeric));
        }
    }

    private static String grade(SessionView s, Long moduleId, Labels l) {
        GradeView g = s.grades().stream().filter(x -> x.moduleId().equals(moduleId)).findFirst().orElse(null);
        if (g == null || g.status() == ExamStatus.NOT_TAKEN) {
            return "—";
        }
        if (g.status() == ExamStatus.ABSENT) {
            return l.absent;
        }
        return g.grade() == null ? "—" : num(g.grade());
    }

    private static String num(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // ------------------------------------------------------------------ cellules

    private PdfPCell cell(String text, Font font, Color bg, Labels l, int align, boolean numeric) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setRunDirection(numeric ? PdfWriter.RUN_DIRECTION_LTR : dir(l));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(5);
        c.setPaddingTop(2);
        c.setBorderColor(BORDER);
        c.setBorderWidth(0.5f);
        if (bg != null) {
            c.setBackgroundColor(bg);
        }
        return c;
    }

    private PdfPCell plain(String text, Font font, Labels l, int align, float padding) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setRunDirection(dir(l));
        c.setHorizontalAlignment(align);
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(padding);
        return c;
    }

    private PdfPTable simple(String text, Font font, Labels l, float spacing) {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setRunDirection(dir(l));
        t.setSpacingBefore(spacing);
        t.addCell(plain(text, font, l, l.rtl ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT, 2));
        return t;
    }

    private static int start(Labels l) {
        return l.rtl ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT;
    }

    private static int dir(Labels l) {
        return l.rtl ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR;
    }

    private static final class Fonts {
        final BaseFont reg = PdfFonts.regular();
        final BaseFont bld = PdfFonts.bold();

        Font normal(float size) {
            return new Font(reg, size);
        }

        Font normal(float size, Color color) {
            return new Font(reg, size, Font.NORMAL, color);
        }

        Font bold(float size) {
            return new Font(bld, size);
        }

        Font header() {
            return new Font(bld, 10, Font.NORMAL, Color.WHITE);
        }
    }
}
