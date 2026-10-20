package ma.entraidenationale.cef.organization.api;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.common.PageResponse;
import ma.entraidenationale.cef.organization.api.CenterDtos.Detail;
import ma.entraidenationale.cef.organization.api.CenterDtos.Filter;
import ma.entraidenationale.cef.organization.api.CenterDtos.Request;
import ma.entraidenationale.cef.organization.api.CenterDtos.Summary;
import ma.entraidenationale.cef.organization.domain.AreaType;
import ma.entraidenationale.cef.organization.domain.LandStatus;
import ma.entraidenationale.cef.organization.domain.ManagementMode;
import ma.entraidenationale.cef.organization.service.CenterExcelExporter;
import ma.entraidenationale.cef.organization.service.CenterService;
import ma.entraidenationale.cef.common.XlsxWriter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/centers")
@RequiredArgsConstructor
@Tag(name = "Centres")
class CenterController {

    private final CenterService centers;
    private final CenterExcelExporter excel;

    @GetMapping
    PageResponse<Summary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long provinceId,
            @RequestParam(required = false) Long communeId,
            @RequestParam(required = false) AreaType areaType,
            @RequestParam(required = false) ManagementMode managementMode,
            @RequestParam(required = false) LandStatus landStatus,
            @RequestParam(required = false) Long filiereId,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "nameAr") Pageable pageable) {
        Filter filter = new Filter(search, regionId, provinceId, communeId, areaType, managementMode, landStatus, filiereId, active);
        return PageResponse.of(centers.list(filter, pageable), c -> c);
    }

    /** Export Excel de la liste filtrée (tout le périmètre, sans pagination). */
    @GetMapping("/export.xlsx")
    ResponseEntity<byte[]> export(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long provinceId,
            @RequestParam(required = false) Long communeId,
            @RequestParam(required = false) AreaType areaType,
            @RequestParam(required = false) ManagementMode managementMode,
            @RequestParam(required = false) LandStatus landStatus,
            @RequestParam(required = false) Long filiereId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "ar") String lang) {
        Filter filter = new Filter(search, regionId, provinceId, communeId, areaType, managementMode, landStatus, filiereId, active);
        var rows = centers.list(filter, PageRequest.of(0, 10_000, Sort.by("nameAr"))).getContent();
        return XlsxWriter.download(excel.export(rows, !"fr".equals(lang)), "centres-cef.xlsx");
    }

    @GetMapping("/{id}")
    Detail get(@PathVariable Long id) {
        return centers.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','PROVINCIAL')")
    Detail create(@Valid @RequestBody Request req) {
        return centers.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PROVINCIAL')")
    Detail update(@PathVariable Long id, @Valid @RequestBody Request req) {
        return centers.update(id, req);
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasAnyRole('ADMIN','PROVINCIAL')")
    Detail setActive(@PathVariable Long id, @RequestParam boolean value) {
        return centers.setActive(id, value);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','PROVINCIAL')")
    void delete(@PathVariable Long id) {
        centers.delete(id);
    }
}
