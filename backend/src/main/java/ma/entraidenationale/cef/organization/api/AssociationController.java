package ma.entraidenationale.cef.organization.api;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
import ma.entraidenationale.cef.common.PageResponse;
import ma.entraidenationale.cef.organization.api.OrganizationDtos.AssociationRequest;
import ma.entraidenationale.cef.organization.api.OrganizationDtos.AssociationView;
import ma.entraidenationale.cef.organization.service.AssociationService;

@RestController
@RequestMapping("/api/associations")
@RequiredArgsConstructor
@Tag(name = "Associations")
class AssociationController {

    private final AssociationService associations;

    @GetMapping
    PageResponse<AssociationView> search(@RequestParam(required = false) String q,
                                         @PageableDefault(size = 20, sort = "nameAr") Pageable pageable) {
        return PageResponse.of(associations.search(q, pageable), a -> a);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','PROVINCIAL')")
    AssociationView create(@Valid @RequestBody AssociationRequest req) {
        return associations.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PROVINCIAL')")
    AssociationView update(@PathVariable Long id, @Valid @RequestBody AssociationRequest req) {
        return associations.update(id, req);
    }
}
