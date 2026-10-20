package ma.entraidenationale.cef.filiererequest.api;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.audit.AuditEntry;
import ma.entraidenationale.cef.common.PageResponse;
import ma.entraidenationale.cef.filiererequest.api.FiliereRequestDtos.ApproveRequest;
import ma.entraidenationale.cef.filiererequest.api.FiliereRequestDtos.Counts;
import ma.entraidenationale.cef.filiererequest.api.FiliereRequestDtos.DecisionRequest;
import ma.entraidenationale.cef.filiererequest.api.FiliereRequestDtos.Detail;
import ma.entraidenationale.cef.filiererequest.api.FiliereRequestDtos.DocumentType;
import ma.entraidenationale.cef.filiererequest.api.FiliereRequestDtos.DocumentView;
import ma.entraidenationale.cef.filiererequest.api.FiliereRequestDtos.Filter;
import ma.entraidenationale.cef.filiererequest.api.FiliereRequestDtos.Request;
import ma.entraidenationale.cef.filiererequest.api.FiliereRequestDtos.Summary;
import ma.entraidenationale.cef.filiererequest.domain.RequestStatus;
import ma.entraidenationale.cef.filiererequest.domain.RequestType;
import ma.entraidenationale.cef.filiererequest.service.FiliereRequestService;

/** Demandes de filière : dépôt par la province, validation régionale, approbation nationale. */
@RestController
@RequestMapping("/api/filiere-requests")
@RequiredArgsConstructor
@Tag(name = "Demandes de filière")
class FiliereRequestController {

    private static final String PROVINCE_SIDE = "hasAnyRole('ADMIN','PROVINCIAL')";
    private static final String REGION_SIDE = "hasAnyRole('ADMIN','REGIONAL')";

    private final FiliereRequestService requests;

    @GetMapping
    PageResponse<Summary> list(@RequestParam(required = false) String search,
                               @RequestParam(required = false) RequestStatus status,
                               @RequestParam(required = false) RequestType type,
                               @RequestParam(required = false) Long regionId,
                               @RequestParam(required = false) Long provinceId,
                               @RequestParam(required = false) Long centerId,
                               @RequestParam(required = false) Boolean actionable,
                               @PageableDefault(size = 20) Pageable pageable) {
        return requests.list(new Filter(search, status, type, regionId, provinceId, centerId, actionable), pageable);
    }

    @GetMapping("/counts")
    Counts counts() {
        return requests.counts();
    }

    @GetMapping("/document-types")
    List<DocumentType> documentTypes() {
        return requests.documentTypes();
    }

    @GetMapping("/{id}")
    Detail get(@PathVariable Long id) {
        return requests.get(id);
    }

    @GetMapping("/{id}/history")
    List<AuditEntry> history(@PathVariable Long id) {
        return requests.history(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(PROVINCE_SIDE)
    Detail create(@Valid @RequestBody Request req) {
        return requests.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(PROVINCE_SIDE)
    Detail update(@PathVariable Long id, @Valid @RequestBody Request req) {
        return requests.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(PROVINCE_SIDE)
    void delete(@PathVariable Long id) {
        requests.delete(id);
    }

    // ---- pièces

    /** Dépose (ou remplace) la pièce {@code typeCode} : PDF uniquement, 20 Mo maximum. */
    @PostMapping(value = "/{id}/documents/{typeCode}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(PROVINCE_SIDE)
    DocumentView upload(@PathVariable Long id, @PathVariable String typeCode, @RequestPart("file") MultipartFile file) {
        return requests.upload(id, typeCode, file);
    }

    @GetMapping("/{id}/documents/{documentId}")
    ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long documentId) {
        return requests.download(id, documentId);
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(PROVINCE_SIDE)
    void deleteDocument(@PathVariable Long id, @PathVariable Long documentId) {
        requests.deleteDocument(id, documentId);
    }

    // ---- circuit

    @PostMapping("/{id}/submit")
    @PreAuthorize(PROVINCE_SIDE)
    Detail submit(@PathVariable Long id) {
        return requests.submit(id);
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize(PROVINCE_SIDE)
    Detail withdraw(@PathVariable Long id) {
        return requests.withdraw(id);
    }

    @PostMapping("/{id}/regional-validation")
    @PreAuthorize(REGION_SIDE)
    Detail regionalValidate(@PathVariable Long id, @Valid @RequestBody(required = false) DecisionRequest req) {
        return requests.regionalValidate(id, req == null ? null : req.comment());
    }

    @PostMapping("/{id}/approval")
    @PreAuthorize("hasRole('ADMIN')")
    Detail approve(@PathVariable Long id, @Valid @RequestBody(required = false) ApproveRequest req) {
        return requests.approve(id, req == null ? new ApproveRequest(null, null, null) : req);
    }

    @PostMapping("/{id}/return")
    @PreAuthorize(REGION_SIDE)
    Detail returnRequest(@PathVariable Long id, @Valid @RequestBody DecisionRequest req) {
        return requests.returnRequest(id, req.comment());
    }

    @PostMapping("/{id}/rejection")
    @PreAuthorize(REGION_SIDE)
    Detail reject(@PathVariable Long id, @Valid @RequestBody DecisionRequest req) {
        return requests.reject(id, req.comment());
    }
}
