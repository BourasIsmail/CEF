package ma.entraidenationale.cef.statistics;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.statistics.StatisticsDtos.Dashboard;
import ma.entraidenationale.cef.statistics.StatisticsDtos.Filters;
import ma.entraidenationale.cef.statistics.StatisticsDtos.Report;

/** Statistiques : tous les profils, chacun dans son périmètre. */
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
@Tag(name = "Statistiques", description = "Tableau officiel région → province → filière → catégorie, exports Excel et PDF")
class StatisticsController {

    static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final StatisticsService statistics;
    private final StatisticsExcelExporter excel;
    private final StatisticsPdfExporter pdf;

    @GetMapping("/report")
    Report report(@RequestParam(required = false) Long seasonId,
                  @RequestParam(required = false) Long regionId,
                  @RequestParam(required = false) Long provinceId,
                  @RequestParam(required = false) Long centerId,
                  @RequestParam(required = false) Long filiereId) {
        return statistics.report(seasonId, new Filters(regionId, provinceId, centerId, filiereId));
    }

    /** Export Excel reproduisant la mise en page officielle ; {@code lang=ar} (défaut, feuille RTL) ou {@code fr}. */
    @GetMapping("/report.xlsx")
    ResponseEntity<byte[]> reportXlsx(@RequestParam(required = false) Long seasonId,
                                      @RequestParam(required = false) Long regionId,
                                      @RequestParam(required = false) Long provinceId,
                                      @RequestParam(required = false) Long centerId,
                                      @RequestParam(required = false) Long filiereId,
                                      @RequestParam(defaultValue = "ar") String lang) {
        Report report = statistics.report(seasonId, new Filters(regionId, provinceId, centerId, filiereId));
        byte[] body = excel.export(report, StatisticsLabels.of(lang));
        return download(body, XLSX, "statistiques-cef-" + report.season().label() + ".xlsx");
    }

    @GetMapping("/report.pdf")
    ResponseEntity<byte[]> reportPdf(@RequestParam(required = false) Long seasonId,
                                     @RequestParam(required = false) Long regionId,
                                     @RequestParam(required = false) Long provinceId,
                                     @RequestParam(required = false) Long centerId,
                                     @RequestParam(required = false) Long filiereId,
                                     @RequestParam(defaultValue = "ar") String lang) {
        Report report = statistics.report(seasonId, new Filters(regionId, provinceId, centerId, filiereId));
        byte[] body = pdf.export(report, StatisticsLabels.of(lang));
        return download(body, MediaType.APPLICATION_PDF, "statistiques-cef-" + report.season().label() + ".pdf");
    }

    @GetMapping("/dashboard")
    Dashboard dashboard(@RequestParam(required = false) Long seasonId) {
        return statistics.dashboard(seasonId);
    }

    private static ResponseEntity<byte[]> download(byte[] body, MediaType type, String filename) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }
}
