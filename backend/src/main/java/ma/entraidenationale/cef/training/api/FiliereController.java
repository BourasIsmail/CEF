package ma.entraidenationale.cef.training.api;

import java.util.List;

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
import ma.entraidenationale.cef.training.api.TrainingDtos.FiliereDetail;
import ma.entraidenationale.cef.training.api.TrainingDtos.FiliereRequest;
import ma.entraidenationale.cef.training.api.TrainingDtos.FiliereSummary;
import ma.entraidenationale.cef.training.api.TrainingDtos.ModuleRequest;
import ma.entraidenationale.cef.training.api.TrainingDtos.ModuleView;
import ma.entraidenationale.cef.training.api.TrainingDtos.ReorderRequest;
import ma.entraidenationale.cef.training.api.TrainingDtos.FiliereCentersRequest;
import ma.entraidenationale.cef.training.service.CenterFiliereService;
import ma.entraidenationale.cef.training.service.FiliereService;
import ma.entraidenationale.cef.training.service.ModuleService;

@RestController
@RequestMapping("/api/filieres")
@RequiredArgsConstructor
@Tag(name = "Filières et modules", description = "Catalogue des filières (lecture : tous ; écriture : ADMIN)")
class FiliereController {

    private final FiliereService filieres;
    private final ModuleService modules;
    private final CenterFiliereService centerFilieres;

    @GetMapping
    PageResponse<FiliereSummary> list(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) Boolean active,
                                      @RequestParam(required = false) Long programId,
                                      @PageableDefault(size = 50, sort = "nameAr") Pageable pageable) {
        return PageResponse.of(filieres.list(q, active, programId, pageable), f -> f);
    }

    @GetMapping("/{id}")
    FiliereDetail get(@PathVariable Long id) {
        return filieres.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    FiliereDetail create(@Valid @RequestBody FiliereRequest req) {
        return filieres.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    FiliereDetail update(@PathVariable Long id, @Valid @RequestBody FiliereRequest req) {
        return filieres.update(id, req);
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    FiliereDetail setActive(@PathVariable Long id, @RequestParam boolean value) {
        return filieres.setActive(id, value);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    void delete(@PathVariable Long id) {
        filieres.delete(id);
    }

    /** Remplace l'ensemble des centres auxquels la filière est assignée. */
    @PutMapping("/{id}/centers")
    @PreAuthorize("hasRole('ADMIN')")
    FiliereDetail assignCenters(@PathVariable Long id, @Valid @RequestBody FiliereCentersRequest req) {
        centerFilieres.replaceCenters(id, req.centerIds());
        return filieres.get(id);
    }

    // ------------------------------------------------------------------ modules

    @GetMapping("/{id}/modules")
    List<ModuleView> modules(@PathVariable Long id) {
        return modules.list(id);
    }

    @PostMapping("/{id}/modules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    ModuleView addModule(@PathVariable Long id, @Valid @RequestBody ModuleRequest req) {
        return modules.add(id, req);
    }

    @PutMapping("/{id}/modules/{moduleId}")
    @PreAuthorize("hasRole('ADMIN')")
    ModuleView updateModule(@PathVariable Long id, @PathVariable Long moduleId, @Valid @RequestBody ModuleRequest req) {
        return modules.update(id, moduleId, req);
    }

    @DeleteMapping("/{id}/modules/{moduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    void deleteModule(@PathVariable Long id, @PathVariable Long moduleId) {
        modules.delete(id, moduleId);
    }

    @PutMapping("/{id}/modules/order")
    @PreAuthorize("hasRole('ADMIN')")
    List<ModuleView> reorder(@PathVariable Long id, @Valid @RequestBody ReorderRequest req) {
        return modules.reorder(id, req.moduleIds());
    }
}
