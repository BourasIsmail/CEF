package ma.entraidenationale.cef.beneficiary.api;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
import ma.entraidenationale.cef.audit.AuditEntry;
import ma.entraidenationale.cef.beneficiary.api.BeneficiaryDtos.Detail;
import ma.entraidenationale.cef.beneficiary.api.BeneficiaryDtos.Filter;
import ma.entraidenationale.cef.beneficiary.api.BeneficiaryDtos.Request;
import ma.entraidenationale.cef.beneficiary.api.BeneficiaryDtos.Summary;
import ma.entraidenationale.cef.beneficiary.domain.Gender;
import ma.entraidenationale.cef.beneficiary.domain.StatCategory;
import ma.entraidenationale.cef.beneficiary.service.BeneficiaryService;
import ma.entraidenationale.cef.common.PageResponse;
import ma.entraidenationale.cef.enrollment.domain.EnrollmentStatus;

@RestController
@RequestMapping("/api/beneficiaries")
@RequiredArgsConstructor
@Tag(name = "Bénéficiaires")
class BeneficiaryController {

    private final BeneficiaryService beneficiaries;

    @GetMapping
    PageResponse<Summary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long provinceId,
            @RequestParam(required = false) Long centerId,
            @RequestParam(required = false) Long filiereId,
            @RequestParam(required = false) Long seasonId,
            @RequestParam(required = false) EnrollmentStatus status,
            @RequestParam(required = false) StatCategory category,
            @RequestParam(required = false) Gender gender,
            @RequestParam(required = false) Boolean vulnerable,
            @RequestParam(required = false) Boolean withoutEnrollment,
            @PageableDefault(size = 20) Pageable pageable) {
        return beneficiaries.list(new Filter(search, regionId, provinceId, centerId, filiereId, seasonId, status,
                category, gender, vulnerable, withoutEnrollment), pageable);
    }

    @GetMapping("/export.xlsx")
    org.springframework.http.ResponseEntity<byte[]> export(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long provinceId,
            @RequestParam(required = false) Long centerId,
            @RequestParam(required = false) Long filiereId,
            @RequestParam(required = false) Long seasonId,
            @RequestParam(required = false) EnrollmentStatus status,
            @RequestParam(required = false) StatCategory category,
            @RequestParam(required = false) Gender gender,
            @RequestParam(required = false) Boolean vulnerable,
            @RequestParam(required = false) Boolean withoutEnrollment,
            @RequestParam(defaultValue = "ar") String lang,
            @PageableDefault(size = 20) Pageable pageable) {
        byte[] body = beneficiaries.exportXlsx(new Filter(search, regionId, provinceId, centerId, filiereId, seasonId,
                status, category, gender, vulnerable, withoutEnrollment), pageable.getSort(), !"fr".equals(lang));
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition.attachment().filename("beneficiaires.xlsx").build().toString())
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    @GetMapping("/{id}")
    Detail get(@PathVariable Long id) {
        return beneficiaries.get(id);
    }

    @GetMapping("/{id}/history")
    List<AuditEntry> history(@PathVariable Long id) {
        return beneficiaries.history(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','PROVINCIAL')")
    Detail create(@Valid @RequestBody Request req) {
        return beneficiaries.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PROVINCIAL')")
    Detail update(@PathVariable Long id, @Valid @RequestBody Request req) {
        return beneficiaries.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','PROVINCIAL')")
    void delete(@PathVariable Long id) {
        beneficiaries.delete(id);
    }
}
