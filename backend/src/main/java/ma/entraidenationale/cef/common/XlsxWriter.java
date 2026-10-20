package ma.entraidenationale.cef.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Export Excel d'une liste à plat : une feuille, en-têtes figés, de droite à gauche en arabe. */
public final class XlsxWriter {

    public static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private XlsxWriter() {
    }

    /** Valeurs acceptées : String, Number (écrit en nombre), null (cellule vide) ; le reste via toString. */
    public static byte[] write(String sheetName, boolean rtl, List<String> headers, List<List<Object>> rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(sheetName);
            sheet.setRightToLeft(rtl);

            XSSFFont bold = wb.createFont();
            bold.setBold(true);
            bold.setColor(new XSSFColor(new byte[] {(byte) 0x3C, (byte) 0x4D, (byte) 0x55}));
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);
            head.setFillForegroundColor(new XSSFColor(new byte[] {(byte) 0xEE, (byte) 0xF3, (byte) 0xF2}));
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            head.setBorderBottom(BorderStyle.THIN);
            head.setAlignment(HorizontalAlignment.CENTER);
            head.setVerticalAlignment(VerticalAlignment.CENTER);
            head.setWrapText(true);
            CellStyle body = wb.createCellStyle();
            body.setVerticalAlignment(VerticalAlignment.CENTER);
            body.setWrapText(true);

            Row h = sheet.createRow(0);
            h.setHeightInPoints(26);
            for (int c = 0; c < headers.size(); c++) {
                var cell = h.createCell(c);
                cell.setCellValue(headers.get(c));
                cell.setCellStyle(head);
            }
            int r = 1;
            for (List<Object> values : rows) {
                Row row = sheet.createRow(r++);
                for (int c = 0; c < values.size(); c++) {
                    Object v = values.get(c);
                    if (v == null) {
                        continue;
                    }
                    var cell = row.createCell(c);
                    if (v instanceof Number n) {
                        cell.setCellValue(n.doubleValue());
                    } else {
                        cell.setCellValue(v.toString());
                    }
                    cell.setCellStyle(body);
                }
            }
            for (int c = 0; c < headers.size(); c++) {
                sheet.autoSizeColumn(c);
                sheet.setColumnWidth(c, Math.min(Math.max(sheet.getColumnWidth(c) + 1024, 3000), 18000));
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(rows.size(), 1), 0, headers.size() - 1));
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Échec de la génération du fichier Excel", e);
        }
    }

    public static ResponseEntity<byte[]> download(byte[] body, String filename) {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }
}
