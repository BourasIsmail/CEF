package ma.entraidenationale.cef.beneficiary.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import ma.entraidenationale.cef.beneficiary.api.BeneficiaryDtos.Summary;

/** Export Excel de la liste des bénéficiaires (feuille de droite à gauche en arabe), mêmes colonnes que l'écran. */
@Component
class BeneficiaryExcelExporter {

    private static final String[] AR = {"الرقم الترتيبي", "رقم التسجيل", "الاسم بالعربية", "الاسم بالفرنسية", "الجنس", "السن",
            "المركز", "الشعبة", "الفئة", "الوضعية"};
    private static final String[] FR = {"N° d'ordre", "N° d'inscription", "Nom (arabe)", "Nom (français)", "Sexe", "Âge",
            "Centre", "Filière", "Catégorie", "Situation"};

    byte[] export(List<Summary> rows, boolean arabic) {
        String[] heads = arabic ? AR : FR;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(arabic ? "المستفيدون" : "Bénéficiaires");
            sheet.setRightToLeft(arabic);
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            head.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            head.setBorderBottom(BorderStyle.THIN);

            Row h = sheet.createRow(0);
            for (int i = 0; i < heads.length; i++) {
                h.createCell(i).setCellValue(heads[i]);
                h.getCell(i).setCellStyle(head);
            }
            int r = 1;
            for (Summary s : rows) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(s.rankNumber());
                row.createCell(1).setCellValue(s.registrationNumber());
                row.createCell(2).setCellValue(s.fullNameAr());
                row.createCell(3).setCellValue(s.fullNameFr() == null ? "" : s.fullNameFr());
                row.createCell(4).setCellValue(label(s.gender().name(), arabic));
                row.createCell(5).setCellValue(s.age());
                row.createCell(6).setCellValue(s.centerNameAr());
                row.createCell(7).setCellValue(s.filiereNameAr() == null ? "" : s.filiereNameAr());
                row.createCell(8).setCellValue(label(s.category().name(), arabic));
                row.createCell(9).setCellValue(s.status() == null ? "" : label(s.status().name(), arabic));
            }
            for (int i = 0; i < heads.length; i++) {
                sheet.autoSizeColumn(i);
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, r - 1), 0, heads.length - 1));
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String label(String code, boolean ar) {
        return switch (code) {
            case "M" -> ar ? "ذكر" : "Homme";
            case "F" -> ar ? "أنثى" : "Femme";
            case "MIGRANT" -> ar ? "مهاجر" : "Migrant";
            case "DISABLED" -> ar ? "شخص في وضعية إعاقة" : "Personne en situation de handicap";
            case "OTHER" -> ar ? "فئة أخرى" : "Autre catégorie";
            case "REMAINING" -> ar ? "باقي المستفيدين" : "Autres bénéficiaires";
            case "ENROLLED" -> ar ? "مسجل" : "Inscrit";
            case "DEPARTED" -> ar ? "مغادر" : "Parti";
            case "GRADUATED" -> ar ? "خريج" : "Diplômé";
            case "FAILED" -> ar ? "راسب" : "Échoué";
            case "ABSENT" -> ar ? "غائب" : "Absent";
            default -> code;
        };
    }
}
