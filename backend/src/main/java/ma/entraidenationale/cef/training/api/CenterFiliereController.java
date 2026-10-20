package ma.entraidenationale.cef.training.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.training.api.TrainingDtos.CenterFiliereView;
import ma.entraidenationale.cef.training.api.TrainingDtos.CenterFilieresRequest;
import ma.entraidenationale.cef.training.service.CenterFiliereService;

@RestController
@RequestMapping("/api/centers/{centerId}/filieres")
@RequiredArgsConstructor
@Tag(name = "Filières des centres")
class CenterFiliereController {

    private final CenterFiliereService service;

    @GetMapping
    List<CenterFiliereView> list(@PathVariable Long centerId) {
        return service.list(centerId);
    }

    /** Remplace l'ensemble des filières du centre. */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    List<CenterFiliereView> replace(@PathVariable Long centerId, @Valid @RequestBody CenterFilieresRequest req) {
        return service.replace(centerId, req.filiereIds());
    }

    @PutMapping("/{filiereId}")
    @PreAuthorize("hasRole('ADMIN')")
    List<CenterFiliereView> assign(@PathVariable Long centerId, @PathVariable Long filiereId) {
        return service.assign(centerId, filiereId);
    }

    @DeleteMapping("/{filiereId}")
    @PreAuthorize("hasRole('ADMIN')")
    List<CenterFiliereView> unassign(@PathVariable Long centerId, @PathVariable Long filiereId) {
        return service.unassign(centerId, filiereId);
    }
}
