package ma.entraidenationale.cef.statistics;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
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

import ma.entraidenationale.cef.beneficiary.domain.StatCategory;
import ma.entraidenationale.cef.common.PdfFonts;
import ma.entraidenationale.cef.statistics.StatisticsDtos.CategoryRow;
import ma.entraidenationale.cef.statistics.StatisticsDtos.FiliereBlock;
import ma.entraidenationale.cef.statistics.StatisticsDtos.Metrics;
import ma.entraidenationale.cef.statistics.StatisticsDtos.ProvinceBlock;
import ma.entraidenationale.cef.statistics.StatisticsDtos.RegionBlock;
import ma.entraidenationale.cef.statistics.StatisticsDtos.Report;

/**
 * Export PDF du tableau officiel (A4 paysage). Le texte arabe est mis en forme (ligatures, sens de lecture) par
 * OpenPDF avec la police Noto Naskh Arabic embarquée (licence SIL OFL, voir {@code resources/fonts}).
 */
@Component
class StatisticsPdfExporter {

    private static final Color HEADER_BG = new Color(0x0C, 0x47, 0x97);
    private static final Color GROUP_BG = new Color(0xF4, 0xF5, 0xF7);
    private static final Color ALT_BG = new Color(0xF8, 0xF9, 0xFB);
    private static final Color PROVINCE_BG = new Color(0xE6, 0xEE, 0xF9);
    private static final Color REGION_BG = new Color(0xCD, 0xDC, 0xF1);
    private static final Color GRAND_BG = new Color(0xAF, 0xC6, 0xE8);
    private static final Color BORDER = new Color(0x8A, 0x94, 0xA6);

    /** 4 colonnes d'identification + 15 colonnes de chiffres + pourcentage. */
    private static final float[] WIDTHS = {13, 13, 15, 19,
            4.6f, 4.6f, 5.2f, 4.6f, 4.6f, 5.2f, 4.6f, 4.6f, 5.2f, 4.6f, 4.6f, 5.2f, 4.6f, 4.6f, 5.2f, 7};

    byte[] export(Report report, StatisticsLabels l) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 18, 18, 20, 20);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setRunDirection(l.rtl ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR);
            doc.addTitle(l.title + " " + report.season().label());
            doc.open();

            Fonts f = new Fonts();
            doc.add(heading(l, report, f));
            doc.add(table(report, l, f));
            doc.add(note(l, f));
        } catch (DocumentException e) {
            throw new IllegalStateException("Échec de la génération du PDF", e);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------

    private PdfPTable heading(StatisticsLabels l, Report report, Fonts f) {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setRunDirection(dir(l));
        t.addCell(plain(l.title, f.title, l, Element.ALIGN_CENTER, 4));
        t.addCell(plain(l.season + " " + report.season().label(), f.normal(10), l, Element.ALIGN_CENTER, 8));
        return t;
    }

    /**
     * Le tableau est construit « de gauche à droite » (les tableaux RTL d'OpenPDF échouent avec des cellules fusionnées) :
     * pour l'arabe, chaque ligne est émise dans l'ordre inverse des colonnes, ce qui donne visuellement un tableau
     * de droite à gauche (région à l'extrême droite). Le texte des cellules reste en sens RTL.
     */
    private PdfPTable table(Report report, StatisticsLabels l, Fonts f) {
        PdfPTable t = new PdfPTable(l.rtl ? reversed(WIDTHS) : WIDTHS);
        t.setWidthPercentage(100);
        t.setHeaderRows(2);
        int right = l.rtl ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT;

        // --- en-têtes (2 lignes)
        List<PdfPCell> h1 = new ArrayList<>();
        h1.add(cell(l.region, f.header, HEADER_BG, l, Element.ALIGN_CENTER, 1, 2, false));
        h1.add(cell(l.province, f.header, HEADER_BG, l, Element.ALIGN_CENTER, 1, 2, false));
        h1.add(cell(l.filiere, f.header, HEADER_BG, l, Element.ALIGN_CENTER, 1, 2, false));
        h1.add(cell(l.category, f.header, HEADER_BG, l, Element.ALIGN_CENTER, 1, 2, false));
        for (String g : new String[] {l.registered, l.departed, l.graduates, l.failed, l.absent}) {
            h1.add(cell(g, f.header, HEADER_BG, l, Element.ALIGN_CENTER, 3, 1, false));
        }
        h1.add(cell(l.passRate, f.header, HEADER_BG, l, Element.ALIGN_CENTER, 1, 2, false));
        flush(t, h1, l);
        List<PdfPCell> h2 = new ArrayList<>();
        for (int g = 0; g < 5; g++) {
            h2.add(cell(l.male, f.header, HEADER_BG, l, Element.ALIGN_CENTER, 1, 1, false));
            h2.add(cell(l.female, f.header, HEADER_BG, l, Element.ALIGN_CENTER, 1, 1, false));
            h2.add(cell(l.total, f.header, HEADER_BG, l, Element.ALIGN_CENTER, 1, 1, false));
        }
        flush(t, h2, l);

        // --- corps
        for (RegionBlock region : report.regions()) {
            int regionRows = region.provinces().stream().mapToInt(p -> p.filieres().size() * 4 + 1).sum();
            boolean firstRegionRow = true;
            for (ProvinceBlock province : region.provinces()) {
                int provinceRows = province.filieres().size() * 4 + 1;
                boolean firstProvinceRow = true;
                for (FiliereBlock filiere : province.filieres()) {
                    boolean firstFiliereRow = true;
                    for (CategoryRow category : filiere.categories()) {
                        List<PdfPCell> row = new ArrayList<>();
                        if (firstRegionRow) {
                            row.add(cell(name(l, region.nameAr(), region.nameFr()), f.boldSmall, GROUP_BG, l, Element.ALIGN_CENTER, 1, regionRows, false));
                            firstRegionRow = false;
                        }
                        if (firstProvinceRow) {
                            row.add(cell(name(l, province.nameAr(), province.nameFr()), f.boldSmall, GROUP_BG, l, Element.ALIGN_CENTER, 1, provinceRows, false));
                            firstProvinceRow = false;
                        }
                        if (firstFiliereRow) {
                            row.add(cell(name(l, filiere.nameAr(), filiere.nameFr()), f.boldSmall, GROUP_BG, l, Element.ALIGN_CENTER, 1, 4, false));
                            firstFiliereRow = false;
                        }
                        Color bg = category.category() == StatCategory.REMAINING ? ALT_BG : null;
                        row.add(cell(l.category(category.category(), category.specification()), f.small, bg, l, right, 1, 1, false));
                        metrics(row, category.metrics(), f.small, bg, l);
                        flush(t, row, l);
                    }
                }
                List<PdfPCell> total = new ArrayList<>();
                total.add(cell(l.provinceTotal, f.boldSmall, PROVINCE_BG, l, right, 2, 1, false));
                metrics(total, province.total(), f.boldSmall, PROVINCE_BG, l);
                flush(t, total, l);
            }
            List<PdfPCell> total = new ArrayList<>();
            total.add(cell(l.regionTotal, f.boldSmall, REGION_BG, l, right, 4, 1, false));
            metrics(total, region.total(), f.boldSmall, REGION_BG, l);
            flush(t, total, l);
        }
        if (report.regions().size() > 1) {
            List<PdfPCell> total = new ArrayList<>();
            total.add(cell(l.grandTotal, f.boldSmall, GRAND_BG, l, right, 4, 1, false));
            metrics(total, report.grandTotal(), f.boldSmall, GRAND_BG, l);
            flush(t, total, l);
        }
        return t;
    }

    private void metrics(List<PdfPCell> row, Metrics m, Font font, Color bg, StatisticsLabels l) {
        for (StatisticsDtos.Cell c : List.of(m.registered(), m.departed(), m.graduates(), m.failed(), m.absent())) {
            row.add(cell(String.valueOf(c.male()), font, bg, l, Element.ALIGN_CENTER, 1, 1, true));
            row.add(cell(String.valueOf(c.female()), font, bg, l, Element.ALIGN_CENTER, 1, 1, true));
            row.add(cell(String.valueOf(c.total()), font, bg, l, Element.ALIGN_CENTER, 1, 1, true));
        }
        String rate = m.passRatePercent() == null ? "—" : m.passRatePercent().toPlainString() + "%";
        row.add(cell(rate, font, bg, l, Element.ALIGN_CENTER, 1, 1, true));
    }

    /** Ajoute une ligne logique ; en arabe, les colonnes sont émises de la dernière à la première. */
    private static void flush(PdfPTable t, List<PdfPCell> row, StatisticsLabels l) {
        if (l.rtl) {
            java.util.Collections.reverse(row);
        }
        row.forEach(t::addCell);
    }

    private static float[] reversed(float[] a) {
        float[] r = new float[a.length];
        for (int i = 0; i < a.length; i++) {
            r[i] = a[a.length - 1 - i];
        }
        return r;
    }

    private PdfPTable note(StatisticsLabels l, Fonts f) {
        PdfPTable t = new PdfPTable(1);
        t.setWidthPercentage(100);
        t.setRunDirection(dir(l));
        t.setSpacingBefore(8);
        t.addCell(plain(l.note, f.normal(7.5f), l, l.rtl ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT, 2));
        return t;
    }

    // ------------------------------------------------------------------ cellules

    /** @param numeric chiffres : lus de gauche à droite pour ne pas inverser « 87.1% ». */
    private PdfPCell cell(String text, Font font, Color bg, StatisticsLabels l, int align, int colspan, int rowspan, boolean numeric) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setRunDirection(numeric ? PdfWriter.RUN_DIRECTION_LTR : dir(l));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setColspan(colspan);
        c.setRowspan(rowspan);
        c.setPadding(2.5f);
        c.setPaddingTop(1f);
        c.setBorderColor(BORDER);
        c.setBorderWidth(0.5f);
        if (bg != null) {
            c.setBackgroundColor(bg);
        }
        return c;
    }

    private PdfPCell plain(String text, Font font, StatisticsLabels l, int align, float padding) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setRunDirection(dir(l));
        c.setHorizontalAlignment(align);
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(padding);
        return c;
    }

    private static String name(StatisticsLabels l, String ar, String fr) {
        return l.rtl ? ar : fr;
    }

    private static int dir(StatisticsLabels l) {
        return l.rtl ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR;
    }

    // ------------------------------------------------------------------ polices

    private static final class Fonts {
        final BaseFont reg = PdfFonts.regular();
        final BaseFont bld = PdfFonts.bold();
        final Font title = new Font(bld, 15);
        final Font header = new Font(bld, 7.5f, Font.NORMAL, Color.WHITE);
        final Font small = new Font(reg, 7.5f);
        final Font boldSmall = new Font(bld, 7.5f);

        Font normal(float size) {
            return new Font(reg, size);
        }
    }
}
