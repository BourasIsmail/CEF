package ma.entraidenationale.cef.statistics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import ma.entraidenationale.cef.statistics.StatisticsDtos.CategoryRow;
import ma.entraidenationale.cef.statistics.StatisticsDtos.FiliereBlock;
import ma.entraidenationale.cef.statistics.StatisticsDtos.Metrics;
import ma.entraidenationale.cef.statistics.StatisticsDtos.ProvinceBlock;
import ma.entraidenationale.cef.statistics.StatisticsDtos.RegionBlock;
import ma.entraidenationale.cef.statistics.StatisticsDtos.Report;

/**
 * Export Excel (.xlsx) du tableau officiel : en-têtes fusionnés, feuille de droite à gauche en arabe, cellules
 * région / province / filière fusionnées, totaux par province et par région.
 */
@Component
class StatisticsExcelExporter {

    /** 4 colonnes d'identification + 5 groupes × 3 + pourcentage. */
    private static final int LAST_COL = 4 + 5 * 3;
    private static final int FIRST_METRIC_COL = 4;

    byte[] export(Report report, StatisticsLabels labels) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles st = new Styles(wb);
            Sheet sheet = wb.createSheet(labels.sheetName);
            sheet.setRightToLeft(labels.rtl);

            int row = 0;
            row = title(sheet, st, labels, report, row);
            int headerFirstRow = row;
            row = header(sheet, st, labels, row);
            row = body(sheet, st, labels, report, row);
            row = note(sheet, st, labels, row + 1);

            widths(sheet);
            sheet.createFreezePane(0, headerFirstRow + 2);
            sheet.setRepeatingRows(CellRangeAddress.valueOf((headerFirstRow + 1) + ":" + (headerFirstRow + 2)));
            sheet.getPrintSetup().setLandscape(true);
            sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
            sheet.setFitToPage(true);
            sheet.getPrintSetup().setFitWidth((short) 1);
            sheet.getPrintSetup().setFitHeight((short) 0);
            wb.setSheetName(0, labels.sheetName);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Échec de la génération du fichier Excel", e);
        }
    }

    // ------------------------------------------------------------------ titre, en-têtes

    private int title(Sheet sheet, Styles st, StatisticsLabels l, Report report, int row) {
        merge(sheet, row, row, 0, LAST_COL, l.title, st.title);
        sheet.getRow(row).setHeightInPoints(28);
        merge(sheet, row + 1, row + 1, 0, LAST_COL, l.season + " " + report.season().label(), st.subtitle);
        return row + 3; // une ligne vide
    }

    private int header(Sheet sheet, Styles st, StatisticsLabels l, int row) {
        int r1 = row;
        int r2 = row + 1;
        merge(sheet, r1, r2, 0, 0, l.region, st.header);
        merge(sheet, r1, r2, 1, 1, l.province, st.header);
        merge(sheet, r1, r2, 2, 2, l.filiere, st.header);
        merge(sheet, r1, r2, 3, 3, l.category, st.header);
        String[] groups = {l.registered, l.departed, l.graduates, l.failed, l.absent};
        for (int g = 0; g < groups.length; g++) {
            int c = FIRST_METRIC_COL + g * 3;
            merge(sheet, r1, r1, c, c + 2, groups[g], st.header);
            set(sheet, r2, c, l.male, st.header);
            set(sheet, r2, c + 1, l.female, st.header);
            set(sheet, r2, c + 2, l.total, st.header);
        }
        merge(sheet, r1, r2, LAST_COL, LAST_COL, l.passRate, st.header);
        sheet.getRow(r1).setHeightInPoints(22);
        sheet.getRow(r2).setHeightInPoints(20);
        return row + 2;
    }

    // ------------------------------------------------------------------ corps

    private int body(Sheet sheet, Styles st, StatisticsLabels l, Report report, int row) {
        Function<String[], String> pick = names -> l.rtl ? names[0] : names[1];
        for (RegionBlock region : report.regions()) {
            int regionStart = row;
            for (ProvinceBlock province : region.provinces()) {
                int provinceStart = row;
                for (FiliereBlock filiere : province.filieres()) {
                    int filiereStart = row;
                    for (CategoryRow category : filiere.categories()) {
                        boolean remaining = category.category() == ma.entraidenationale.cef.beneficiary.domain.StatCategory.REMAINING;
                        set(sheet, row, 3, l.category(category.category(), category.specification()), st.label);
                        metrics(sheet, row, category.metrics(), remaining ? st.numberAlt : st.number, remaining ? st.percentAlt : st.percent);
                        row++;
                    }
                    merge(sheet, filiereStart, row - 1, 2, 2, pick.apply(new String[] {filiere.nameAr(), filiere.nameFr()}), st.group);
                }
                // total de la province : libellé sur les colonnes « filière » + « catégorie »
                merge(sheet, row, row, 2, 3, l.provinceTotal, st.provinceTotalLabel);
                metrics(sheet, row, province.total(), st.provinceTotal, st.provinceTotalPercent);
                row++;
                merge(sheet, provinceStart, row - 1, 1, 1, pick.apply(new String[] {province.nameAr(), province.nameFr()}), st.group);
            }
            // la cellule « région » couvre tout sauf sa ligne de total
            if (!region.provinces().isEmpty()) {
                merge(sheet, regionStart, row - 1, 0, 0, pick.apply(new String[] {region.nameAr(), region.nameFr()}), st.group);
            }
            merge(sheet, row, row, 0, 3, l.regionTotal, st.regionTotalLabel);
            metrics(sheet, row, region.total(), st.regionTotal, st.regionTotalPercent);
            row++;
        }
        if (report.regions().size() > 1) {
            merge(sheet, row, row, 0, 3, l.grandTotal, st.grandTotalLabel);
            metrics(sheet, row, report.grandTotal(), st.grandTotal, st.grandTotalPercent);
            row++;
        }
        return row;
    }

    private void metrics(Sheet sheet, int row, Metrics m, CellStyle number, CellStyle percent) {
        List<StatisticsDtos.Cell> cells = List.of(m.registered(), m.departed(), m.graduates(), m.failed(), m.absent());
        for (int g = 0; g < cells.size(); g++) {
            int c = FIRST_METRIC_COL + g * 3;
            num(sheet, row, c, cells.get(g).male(), number);
            num(sheet, row, c + 1, cells.get(g).female(), number);
            num(sheet, row, c + 2, cells.get(g).total(), number);
        }
        Cell rate = row(sheet, row).createCell(LAST_COL);
        rate.setCellStyle(percent);
        BigDecimal pct = m.passRatePercent();
        if (pct == null) {
            rate.setCellValue("—");
        } else {
            rate.setCellValue(pct.doubleValue() / 100.0); // stocké en fraction, affiché « 87,1 % »
        }
    }

    private int note(Sheet sheet, Styles st, StatisticsLabels l, int row) {
        merge(sheet, row, row, 0, LAST_COL, l.note, st.note);
        sheet.getRow(row).setHeightInPoints(44);
        return row + 1;
    }

    private void widths(Sheet sheet) {
        int[] w = {24, 24, 28, 32};
        for (int i = 0; i < w.length; i++) {
            sheet.setColumnWidth(i, w[i] * 256);
        }
        for (int c = FIRST_METRIC_COL; c < LAST_COL; c++) {
            sheet.setColumnWidth(c, 8 * 256);
        }
        sheet.setColumnWidth(LAST_COL, 13 * 256);
    }

    // ------------------------------------------------------------------ cellules

    private static Row row(Sheet sheet, int index) {
        Row r = sheet.getRow(index);
        return r != null ? r : sheet.createRow(index);
    }

    private static void set(Sheet sheet, int row, int col, String value, CellStyle style) {
        Cell c = row(sheet, row).createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static void num(Sheet sheet, int row, int col, long value, CellStyle style) {
        Cell c = row(sheet, row).createCell(col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    /** Fusionne une plage en appliquant le style à TOUTES ses cellules (bordures complètes). */
    private static void merge(Sheet sheet, int r1, int r2, int c1, int c2, String value, CellStyle style) {
        for (int r = r1; r <= r2; r++) {
            for (int c = c1; c <= c2; c++) {
                Cell cell = row(sheet, r).createCell(c);
                cell.setCellStyle(style);
            }
        }
        row(sheet, r1).getCell(c1).setCellValue(value);
        if (r1 != r2 || c1 != c2) {
            sheet.addMergedRegion(new CellRangeAddress(r1, r2, c1, c2));
        }
    }

    // ------------------------------------------------------------------ styles

    private static final class Styles {
        final CellStyle title, subtitle, header, label, group, number, numberAlt, percent, percentAlt;
        final CellStyle provinceTotalLabel, provinceTotal, provinceTotalPercent;
        final CellStyle regionTotalLabel, regionTotal, regionTotalPercent;
        final CellStyle grandTotalLabel, grandTotal, grandTotalPercent;
        final CellStyle note;

        Styles(XSSFWorkbook wb) {
            XSSFFont normal = font(wb, false, 10, null);
            XSSFFont bold = font(wb, true, 10, null);
            XSSFFont white = font(wb, true, 10, new byte[] {(byte) 255, (byte) 255, (byte) 255});
            XSSFFont big = font(wb, true, 15, null);
            XSSFFont mid = font(wb, false, 11, null);
            XSSFFont small = font(wb, false, 9, new byte[] {0x3C, 0x4D, 0x55});

            title = style(wb, big, null, HorizontalAlignment.CENTER, false, null);
            subtitle = style(wb, mid, null, HorizontalAlignment.CENTER, false, null);
            header = style(wb, white, new byte[] {0x0F, 0x5C, 0x5A}, HorizontalAlignment.CENTER, true, null);
            label = style(wb, normal, null, HorizontalAlignment.RIGHT, true, null);
            group = style(wb, bold, new byte[] {(byte) 0xF3, (byte) 0xF6, (byte) 0xF5}, HorizontalAlignment.CENTER, true, null);
            number = style(wb, normal, null, HorizontalAlignment.CENTER, true, "0");
            numberAlt = style(wb, normal, new byte[] {(byte) 0xF7, (byte) 0xFA, (byte) 0xF9}, HorizontalAlignment.CENTER, true, "0");
            percent = style(wb, normal, null, HorizontalAlignment.CENTER, true, "0.0%");
            percentAlt = style(wb, normal, new byte[] {(byte) 0xF7, (byte) 0xFA, (byte) 0xF9}, HorizontalAlignment.CENTER, true, "0.0%");
            provinceTotalLabel = style(wb, bold, new byte[] {(byte) 0xE3, (byte) 0xF0, (byte) 0xEE}, HorizontalAlignment.RIGHT, true, null);
            provinceTotal = style(wb, bold, new byte[] {(byte) 0xE3, (byte) 0xF0, (byte) 0xEE}, HorizontalAlignment.CENTER, true, "0");
            provinceTotalPercent = style(wb, bold, new byte[] {(byte) 0xE3, (byte) 0xF0, (byte) 0xEE}, HorizontalAlignment.CENTER, true, "0.0%");
            regionTotalLabel = style(wb, bold, new byte[] {(byte) 0xCF, (byte) 0xE5, (byte) 0xE2}, HorizontalAlignment.RIGHT, true, null);
            regionTotal = style(wb, bold, new byte[] {(byte) 0xCF, (byte) 0xE5, (byte) 0xE2}, HorizontalAlignment.CENTER, true, "0");
            regionTotalPercent = style(wb, bold, new byte[] {(byte) 0xCF, (byte) 0xE5, (byte) 0xE2}, HorizontalAlignment.CENTER, true, "0.0%");
            grandTotalLabel = style(wb, bold, new byte[] {(byte) 0xB0, (byte) 0xD4, (byte) 0xCF}, HorizontalAlignment.RIGHT, true, null);
            grandTotal = style(wb, bold, new byte[] {(byte) 0xB0, (byte) 0xD4, (byte) 0xCF}, HorizontalAlignment.CENTER, true, "0");
            grandTotalPercent = style(wb, bold, new byte[] {(byte) 0xB0, (byte) 0xD4, (byte) 0xCF}, HorizontalAlignment.CENTER, true, "0.0%");
            note = style(wb, small, null, HorizontalAlignment.RIGHT, false, null);
            ((XSSFCellStyle) note).setWrapText(true);
            ((XSSFCellStyle) note).setVerticalAlignment(VerticalAlignment.TOP);
        }

        private static XSSFFont font(XSSFWorkbook wb, boolean bold, int size, byte[] rgb) {
            XSSFFont f = wb.createFont();
            f.setFontName("Arial");
            f.setBold(bold);
            f.setFontHeightInPoints((short) size);
            if (rgb != null) {
                f.setColor(new XSSFColor(rgb, null));
            }
            return f;
        }

        private static CellStyle style(XSSFWorkbook wb, XSSFFont font, byte[] fill, HorizontalAlignment align,
                                       boolean border, String format) {
            XSSFCellStyle s = wb.createCellStyle();
            s.setFont(font);
            s.setAlignment(align);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            s.setWrapText(true);
            if (fill != null) {
                s.setFillForegroundColor(new XSSFColor(fill, null));
                s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            if (border) {
                BorderStyle thin = BorderStyle.THIN;
                s.setBorderTop(thin);
                s.setBorderBottom(thin);
                s.setBorderLeft(thin);
                s.setBorderRight(thin);
                byte[] grey = {(byte) 0x8A, (byte) 0xA3, (byte) 0xA0};
                s.setTopBorderColor(new XSSFColor(grey, null));
                s.setBottomBorderColor(new XSSFColor(grey, null));
                s.setLeftBorderColor(new XSSFColor(grey, null));
                s.setRightBorderColor(new XSSFColor(grey, null));
            }
            if (format != null) {
                s.setDataFormat(wb.createDataFormat().getFormat(format));
            }
            return s;
        }
    }
}
