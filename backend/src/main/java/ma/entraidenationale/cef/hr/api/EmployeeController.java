package ma.entraidenationale.cef.hr.api;

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
import ma.entraidenationale.cef.beneficiary.domain.Gender;
import ma.entraidenationale.cef.common.PageResponse;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.AssignmentView;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.Detail;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.Filter;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.GuestContractRequest;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.GuestContractView;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.Request;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.StaffSummary;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.Summary;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.TeachingRequest;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.TeachingView;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.TransferRequest;
import ma.entraidenationale.cef.hr.domain.ActivityStatus;
import ma.entraidenationale.cef.hr.domain.AdministrativeStatus;
import ma.entraidenationale.cef.hr.domain.EmployeeType;
import ma.entraidenationale.cef.hr.service.EmployeeService;

/** Ressources humaines : lecture dans le périmètre de l'utilisateur, écriture ADMIN et PROVINCIAL (sa province). */
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
@Tag(name = "Ressources humaines")
class EmployeeController {

    private static final String WRITERS = "hasAnyRole('ADMIN','PROVINCIAL')";

    private final EmployeeService employees;

    @GetMapping
    PageResponse<Summary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long regionId,
            @RequestParam(required = false) Long provinceId,
            @RequestParam(required = false) Long centerId,
            @RequestParam(required = false) EmployeeType type,
            @RequestParam(required = false) AdministrativeStatus administrativeStatus,
            @RequestParam(required = false) ActivityStatus activityStatus,
            @RequestParam(required = false) Gender gender,
            @RequestParam(required = false) Boolean withoutCenter,
            @RequestParam(required = false) Long filiereId,
            @PageableDefault(size = 20) Pageable pageable) {
        return employees.list(new Filter(search, regionId, provinceId, centerId, type, administrativeStatus,
                activityStatus, gender, withoutCenter, filiereId), pageable);
    }

    /** Effectifs par sexe, par صفة et par وضعية إدارية (agents en activité ou non, selon les filtres). */
    @GetMapping("/summary")
    StaffSummary summary(@RequestParam(required = false) Long regionId,
                         @RequestParam(required = false) Long provinceId,
                         @RequestParam(required = false) Long centerId) {
        return employees.summary(regionId, provinceId, centerId);
    }

    @GetMapping("/{id}")
    Detail get(@PathVariable Long id) {
        return employees.get(id);
    }

    @GetMapping("/{id}/history")
    List<AuditEntry> history(@PathVariable Long id) {
        return employees.history(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITERS)
    Detail create(@Valid @RequestBody Request req) {
        return employees.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITERS)
    Detail update(@PathVariable Long id, @Valid @RequestBody Request req) {
        return employees.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITERS)
    void delete(@PathVariable Long id) {
        employees.delete(id);
    }

    // ---- affectations

    @PostMapping("/{id}/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITERS)
    List<AssignmentView> transfer(@PathVariable Long id, @Valid @RequestBody TransferRequest req) {
        return employees.transfer(id, req);
    }

    // ---- هيئة التكوين

    @PostMapping("/{id}/teachings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITERS)
    TeachingView addTeaching(@PathVariable Long id, @Valid @RequestBody TeachingRequest req) {
        return employees.addTeaching(id, req);
    }

    @DeleteMapping("/{id}/teachings/{teachingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITERS)
    void removeTeaching(@PathVariable Long id, @PathVariable Long teachingId) {
        employees.removeTeaching(id, teachingId);
    }

    // ---- المكونون المستضافون

    @PostMapping("/{id}/guest-contracts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(WRITERS)
    GuestContractView addGuestContract(@PathVariable Long id, @Valid @RequestBody GuestContractRequest req) {
        return employees.addGuestContract(id, req);
    }

    @PutMapping("/{id}/guest-contracts/{contractId}")
    @PreAuthorize(WRITERS)
    GuestContractView updateGuestContract(@PathVariable Long id, @PathVariable Long contractId,
                                          @Valid @RequestBody GuestContractRequest req) {
        return employees.updateGuestContract(id, contractId, req);
    }

    @DeleteMapping("/{id}/guest-contracts/{contractId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(WRITERS)
    void removeGuestContract(@PathVariable Long id, @PathVariable Long contractId) {
        employees.removeGuestContract(id, contractId);
    }
}
