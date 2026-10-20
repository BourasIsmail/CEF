package ma.entraidenationale.cef.organization.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.organization.api.OrganizationDtos.Commune;
import ma.entraidenationale.cef.organization.api.OrganizationDtos.CommuneRequest;
import ma.entraidenationale.cef.organization.api.OrganizationDtos.PartnerTypeView;
import ma.entraidenationale.cef.organization.api.OrganizationDtos.Province;
import ma.entraidenationale.cef.organization.api.OrganizationDtos.ProvinceRequest;
import ma.entraidenationale.cef.organization.api.OrganizationDtos.Region;
import ma.entraidenationale.cef.organization.api.OrganizationDtos.SpaceTypeView;
import ma.entraidenationale.cef.organization.service.OrganizationService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Organisation", description = "Régions, provinces, communes et référentiels (lecture filtrée par périmètre)")
class OrganizationController {

    private final OrganizationService organization;

    @GetMapping("/regions")
    List<Region> regions() {
        return organization.regions();
    }

    @GetMapping("/provinces")
    List<Province> provinces(@RequestParam(required = false) Long regionId) {
        return organization.provinces(regionId);
    }

    @GetMapping("/communes")
    List<Commune> communes(@RequestParam Long provinceId) {
        return organization.communes(provinceId);
    }

    @GetMapping("/space-types")
    List<SpaceTypeView> spaceTypes() {
        return organization.spaceTypes();
    }

    @GetMapping("/partner-types")
    List<PartnerTypeView> partnerTypes() {
        return organization.partnerTypes();
    }

    @PostMapping("/provinces")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    Province createProvince(@Valid @RequestBody ProvinceRequest req) {
        return organization.createProvince(req);
    }

    @PutMapping("/provinces/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    Province updateProvince(@PathVariable Long id, @Valid @RequestBody ProvinceRequest req) {
        return organization.updateProvince(id, req);
    }

    @PostMapping("/communes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    Commune createCommune(@Valid @RequestBody CommuneRequest req) {
        return organization.createCommune(req);
    }

    @PutMapping("/communes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    Commune updateCommune(@PathVariable Long id, @Valid @RequestBody CommuneRequest req) {
        return organization.updateCommune(id, req);
    }
}
