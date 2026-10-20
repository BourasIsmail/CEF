package ma.entraidenationale.cef.organization.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.common.BusinessRuleException;
import ma.entraidenationale.cef.common.ConflictException;
import ma.entraidenationale.cef.common.NotFoundException;
import ma.entraidenationale.cef.organization.api.OrganizationDtos.Commune;
import ma.entraidenationale.cef.organization.api.OrganizationDtos.CommuneRequest;
import ma.entraidenationale.cef.organization.api.OrganizationDtos.PartnerTypeView;
import ma.entraidenationale.cef.organization.api.OrganizationDtos.Province;
import ma.entraidenationale.cef.organization.api.OrganizationDtos.ProvinceRequest;
import ma.entraidenationale.cef.organization.api.OrganizationDtos.Region;
import ma.entraidenationale.cef.organization.api.OrganizationDtos.SpaceTypeView;
import ma.entraidenationale.cef.organization.domain.CommuneRepository;
import ma.entraidenationale.cef.organization.domain.PartnerTypeRepository;
import ma.entraidenationale.cef.organization.domain.ProvincialDirectorate;
import ma.entraidenationale.cef.organization.domain.ProvincialDirectorateRepository;
import ma.entraidenationale.cef.organization.domain.RegionalDirectorateRepository;
import ma.entraidenationale.cef.organization.domain.SpaceTypeRepository;
import ma.entraidenationale.cef.security.CurrentUser;
import ma.entraidenationale.cef.security.UserScope;

/**
 * Hiérarchie organisationnelle (régions, provinces, communes) et référentiels. Lecture filtrée par périmètre ;
 * création / modification des provinces et communes réservées à l'ADMIN (les 12 régions sont figées).
 */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final RegionalDirectorateRepository regions;
    private final ProvincialDirectorateRepository provinces;
    private final CommuneRepository communes;
    private final SpaceTypeRepository spaceTypes;
    private final PartnerTypeRepository partnerTypes;
    private final CurrentUser currentUser;

    // ------------------------------------------------------------------ lecture (filtrée par périmètre)

    @Transactional(readOnly = true)
    public List<Region> regions() {
        UserScope scope = currentUser.scope();
        return regions.findAll().stream()
                .filter(r -> scope.seesRegion(r.getId()))
                .sorted((a, b) -> a.getCode().compareTo(b.getCode()))
                .map(r -> new Region(r.getId(), r.getCode(), r.getNameAr(), r.getNameFr()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Province> provinces(Long regionId) {
        UserScope scope = currentUser.scope();
        List<ProvincialDirectorate> all = regionId == null
                ? provinces.findAll()
                : provinces.findByRegionalDirectorateIdOrderByCode(regionId);
        return all.stream()
                .filter(p -> scope.seesProvince(p.getId(), p.getRegionalDirectorate().getId()))
                .sorted((a, b) -> a.getCode().compareTo(b.getCode()))
                .map(OrganizationService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Commune> communes(Long provinceId) {
        ProvincialDirectorate province = visibleProvince(provinceId);
        return communes.findByProvincialDirectorateIdOrderByNameAr(province.getId()).stream()
                .map(c -> new Commune(c.getId(), province.getId(), c.getNameAr(), c.getNameFr())).toList();
    }

    @Transactional(readOnly = true)
    public List<SpaceTypeView> spaceTypes() {
        return spaceTypes.findAllByOrderByPosition().stream()
                .map(s -> new SpaceTypeView(s.getId(), s.getCode(), s.getNameAr(), s.getNameFr(), s.isRequiresSpecification()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PartnerTypeView> partnerTypes() {
        return partnerTypes.findByActiveTrueOrderByPosition().stream()
                .map(t -> new PartnerTypeView(t.getId(), t.getCode(), t.getNameAr(), t.getNameFr())).toList();
    }

    // ------------------------------------------------------------------ administration (ADMIN)

    @Transactional
    public Province createProvince(ProvinceRequest req) {
        var region = regions.findById(req.regionId())
                .orElseThrow(() -> new BusinessRuleException("province.region_unknown", "Région inconnue"));
        if (provinces.existsByCode(req.code().trim())) {
            throw new ConflictException("province.code_exists", "Ce code de province existe déjà");
        }
        var p = new ProvincialDirectorate();
        p.setRegionalDirectorate(region);
        p.setCode(req.code().trim());
        p.setNameAr(req.nameAr().trim());
        p.setNameFr(req.nameFr().trim());
        return toDto(provinces.saveAndFlush(p));
    }

    @Transactional
    public Province updateProvince(Long id, ProvinceRequest req) {
        var p = provinces.findById(id).orElseThrow(() -> NotFoundException.of("Province", id));
        if (!p.getRegionalDirectorate().getId().equals(req.regionId())) {
            throw new BusinessRuleException("province.region_immutable", "La région d'une province ne peut pas être modifiée");
        }
        String code = req.code().trim();
        if (!code.equals(p.getCode()) && provinces.existsByCode(code)) {
            throw new ConflictException("province.code_exists", "Ce code de province existe déjà");
        }
        p.setCode(code);
        p.setNameAr(req.nameAr().trim());
        p.setNameFr(req.nameFr().trim());
        return toDto(provinces.saveAndFlush(p));
    }

    @Transactional
    public Commune createCommune(CommuneRequest req) {
        var province = provinces.findById(req.provinceId())
                .orElseThrow(() -> new BusinessRuleException("commune.province_unknown", "Province inconnue"));
        if (communes.existsByProvincialDirectorateIdAndNameAr(province.getId(), req.nameAr().trim())) {
            throw new ConflictException("commune.exists", "Cette commune existe déjà dans la province");
        }
        var c = new ma.entraidenationale.cef.organization.domain.Commune();
        c.setProvincialDirectorate(province);
        c.setNameAr(req.nameAr().trim());
        c.setNameFr(req.nameFr().trim());
        c = communes.saveAndFlush(c);
        return new Commune(c.getId(), province.getId(), c.getNameAr(), c.getNameFr());
    }

    @Transactional
    public Commune updateCommune(Long id, CommuneRequest req) {
        var c = communes.findById(id).orElseThrow(() -> NotFoundException.of("Commune", id));
        if (!c.getProvincialDirectorate().getId().equals(req.provinceId())) {
            throw new BusinessRuleException("commune.province_immutable", "La province d'une commune ne peut pas être modifiée");
        }
        String name = req.nameAr().trim();
        if (!name.equals(c.getNameAr())
                && communes.existsByProvincialDirectorateIdAndNameAr(c.getProvincialDirectorate().getId(), name)) {
            throw new ConflictException("commune.exists", "Cette commune existe déjà dans la province");
        }
        c.setNameAr(name);
        c.setNameFr(req.nameFr().trim());
        c = communes.saveAndFlush(c);
        return new Commune(c.getId(), c.getProvincialDirectorate().getId(), c.getNameAr(), c.getNameFr());
    }

    // ------------------------------------------------------------------

    private ProvincialDirectorate visibleProvince(Long provinceId) {
        ProvincialDirectorate p = provinces.findById(provinceId)
                .orElseThrow(() -> NotFoundException.of("Province", provinceId));
        if (!currentUser.scope().seesProvince(p.getId(), p.getRegionalDirectorate().getId())) {
            throw NotFoundException.of("Province", provinceId);
        }
        return p;
    }

    private static Province toDto(ProvincialDirectorate p) {
        return new Province(p.getId(), p.getRegionalDirectorate().getId(), p.getCode(), p.getNameAr(), p.getNameFr());
    }
}
