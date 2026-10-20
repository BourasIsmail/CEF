package ma.entraidenationale.cef.organization.service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.common.BusinessRuleException;
import ma.entraidenationale.cef.common.ConflictException;
import ma.entraidenationale.cef.common.NotFoundException;
import ma.entraidenationale.cef.organization.api.CenterDtos.AssociationLink;
import ma.entraidenationale.cef.organization.api.CenterDtos.AssociationView;
import ma.entraidenationale.cef.organization.api.CenterDtos.Detail;
import ma.entraidenationale.cef.organization.api.CenterDtos.Filter;
import ma.entraidenationale.cef.organization.api.CenterDtos.FloorArea;
import ma.entraidenationale.cef.organization.api.CenterDtos.PartnerRef;
import ma.entraidenationale.cef.organization.api.CenterDtos.Request;
import ma.entraidenationale.cef.organization.api.CenterDtos.SpaceSelection;
import ma.entraidenationale.cef.organization.api.CenterDtos.SpaceView;
import ma.entraidenationale.cef.organization.api.CenterDtos.Summary;
import ma.entraidenationale.cef.organization.domain.Association;
import ma.entraidenationale.cef.organization.domain.AssociationRepository;
import ma.entraidenationale.cef.organization.domain.BuildingType;
import ma.entraidenationale.cef.organization.domain.Center;
import ma.entraidenationale.cef.organization.domain.CenterAssociation;
import ma.entraidenationale.cef.organization.domain.CenterFloorArea;
import ma.entraidenationale.cef.organization.domain.CenterRepository;
import ma.entraidenationale.cef.organization.domain.CenterSpace;
import ma.entraidenationale.cef.organization.domain.Commune;
import ma.entraidenationale.cef.organization.domain.CommuneRepository;
import ma.entraidenationale.cef.organization.domain.LandStatus;
import ma.entraidenationale.cef.organization.domain.PartnerType;
import ma.entraidenationale.cef.organization.domain.PartnerTypeRepository;
import ma.entraidenationale.cef.organization.domain.PartnershipMeasure;
import ma.entraidenationale.cef.organization.domain.ProvincialDirectorate;
import ma.entraidenationale.cef.organization.domain.ProvincialDirectorateRepository;
import ma.entraidenationale.cef.organization.domain.SpaceType;
import ma.entraidenationale.cef.organization.domain.SpaceTypeRepository;
import ma.entraidenationale.cef.security.AppRole;
import ma.entraidenationale.cef.security.AppUserPrincipal;
import ma.entraidenationale.cef.security.CurrentUser;
import ma.entraidenationale.cef.security.UserScope;

/**
 * Gestion des centres. Règles de périmètre :
 * <ul>
 *   <li>un utilisateur ne voit que les centres de son périmètre (un centre hors périmètre = 404) ;</li>
 *   <li>un compte provincial crée et modifie uniquement dans SA province, qu'il ne choisit pas ;</li>
 *   <li>un compte régional est en lecture seule.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CenterService {

    private final CenterRepository centers;
    private final CommuneRepository communes;
    private final ProvincialDirectorateRepository provinces;
    private final SpaceTypeRepository spaceTypes;
    private final PartnerTypeRepository partnerTypes;
    private final AssociationRepository associations;
    private final CurrentUser currentUser;
    private final NamedParameterJdbcTemplate jdbc;

    // =====================================================================================
    // Lecture
    // =====================================================================================

    @Transactional(readOnly = true)
    public Page<Summary> list(Filter filter, Pageable pageable) {
        UserScope scope = currentUser.scope();
        Set<Long> filiereCenters = filter.filiereId() == null ? null : centerIdsOfFiliere(filter.filiereId());
        Page<Center> page = centers.findAll(CenterSpecifications.of(filter, scope, filiereCenters), pageable);

        List<Long> ids = page.getContent().stream().map(Center::getId).toList();
        Counts counts = loadCounts(ids);
        return page.map(c -> new Summary(
                c.getId(), c.getCode(), c.getNameAr(), c.getNameFr(),
                c.getProvincialDirectorate().getRegionalDirectorate().getId(),
                c.getProvincialDirectorate().getId(), c.getProvincialDirectorate().getNameAr(),
                c.getCommune().getId(), c.getCommune().getNameAr(),
                c.getAreaType(), c.getLandStatus(), c.getManagementMode(), c.isActive(),
                counts.filieres.getOrDefault(c.getId(), 0L),
                counts.beneficiaries.getOrDefault(c.getId(), 0L),
                counts.staff.getOrDefault(c.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public Detail get(Long id) {
        return toDetail(requireVisible(id));
    }

    // =====================================================================================
    // Écriture
    // =====================================================================================

    @Transactional
    public Detail create(Request req) {
        AppUserPrincipal user = currentUser.require();
        ProvincialDirectorate province = provinceForCreation(user, req.provincialDirectorateId());
        Center c = new Center();
        c.setProvincialDirectorate(province);
        c.setCommune(communeInProvince(req.communeId(), province));
        c.setCode(resolveNewCode(req.code(), province));
        applyScalars(c, req);
        centers.saveAndFlush(c); // l'identifiant est nécessaire aux lignes filles
        applyChildren(c, req);
        return toDetail(centers.saveAndFlush(c));
    }

    @Transactional
    public Detail update(Long id, Request req) {
        Center c = requireManaged(id);
        if (req.provincialDirectorateId() != null
                && !req.provincialDirectorateId().equals(c.getProvincialDirectorate().getId())) {
            throw rule("center.province_immutable", "La province d'un centre ne peut pas être modifiée");
        }
        c.setCommune(communeInProvince(req.communeId(), c.getProvincialDirectorate()));
        if (currentUser.scope().isNational() && req.code() != null && !req.code().isBlank()
                && !req.code().trim().equals(c.getCode())) {
            if (centers.existsByCode(req.code().trim())) {
                throw new ConflictException("center.code_exists", "Ce code de centre existe déjà");
            }
            c.setCode(req.code().trim());
        }
        applyScalars(c, req);
        applyChildren(c, req);
        return toDetail(centers.saveAndFlush(c));
    }

    @Transactional
    public Detail setActive(Long id, boolean active) {
        Center c = requireManaged(id);
        c.setActive(active);
        return toDetail(centers.saveAndFlush(c));
    }

    @Transactional
    public void delete(Long id) {
        Center c = requireManaged(id);
        Map<String, Long> usage = usageOf(c.getId());
        long total = usage.values().stream().mapToLong(Long::longValue).sum();
        if (total > 0) {
            throw new ConflictException("center.in_use",
                    "Suppression impossible : le centre est utilisé " + usage + ". Désactivez-le plutôt.");
        }
        centers.delete(c);
    }

    // =====================================================================================
    // Chargement et contrôle de périmètre
    // =====================================================================================

    /** Charge un centre visible par l'utilisateur ; hors périmètre ⇒ 404 (on ne révèle pas son existence). */
    public Center requireVisible(Long id) {
        Center c = centers.findWithOrganizationById(id).orElseThrow(() -> NotFoundException.of("Centre", id));
        ProvincialDirectorate p = c.getProvincialDirectorate();
        if (!currentUser.scope().seesCenter(c.getId(), p.getId(), p.getRegionalDirectorate().getId())) {
            throw NotFoundException.of("Centre", id);
        }
        return c;
    }

    /** Centre visible ET modifiable (ADMIN, ou PROVINCIAL de sa province). */
    public Center requireManaged(Long id) {
        Center c = requireVisible(id);
        if (!currentUser.scope().managesProvince(c.getProvincialDirectorate().getId())) {
            throw new AccessDeniedException("Modification non autorisée sur ce centre");
        }
        return c;
    }

    private ProvincialDirectorate provinceForCreation(AppUserPrincipal user, Long requestedProvinceId) {
        if (user.role() == AppRole.PROVINCIAL) {
            if (requestedProvinceId != null && !requestedProvinceId.equals(user.provinceId())) {
                throw new AccessDeniedException("Un compte provincial ne peut créer des centres que dans sa province");
            }
            return provinces.findById(user.provinceId()).orElseThrow(() -> NotFoundException.of("Province", user.provinceId()));
        }
        if (user.role() == AppRole.ADMIN) {
            if (requestedProvinceId == null) {
                throw rule("center.province_required", "La direction provinciale est obligatoire");
            }
            return provinces.findById(requestedProvinceId).orElseThrow(() -> rule("center.province_unknown", "Province inconnue"));
        }
        throw new AccessDeniedException("Création de centre non autorisée pour ce profil");
    }

    private Commune communeInProvince(Long communeId, ProvincialDirectorate province) {
        Commune commune = communes.findById(communeId).orElseThrow(() -> rule("center.commune_unknown", "Commune inconnue"));
        if (!commune.getProvincialDirectorate().getId().equals(province.getId())) {
            throw rule("center.commune_not_in_province", "La commune n'appartient pas à la province du centre");
        }
        return commune;
    }

    /** Code fourni (unique) ou généré : {code province}-{nn}. */
    private String resolveNewCode(String requested, ProvincialDirectorate province) {
        if (requested != null && !requested.isBlank()) {
            String code = requested.trim();
            if (centers.existsByCode(code)) {
                throw new ConflictException("center.code_exists", "Ce code de centre existe déjà");
            }
            return code;
        }
        long n = centers.countByProvincialDirectorateId(province.getId()) + 1;
        String code;
        do {
            code = province.getCode() + "-" + String.format("%02d", n++);
        } while (centers.existsByCode(code));
        return code;
    }

    // =====================================================================================
    // Application de la requête sur l'entité
    // =====================================================================================

    private void applyScalars(Center c, Request req) {
        c.setNameAr(req.nameAr().trim());
        c.setNameFr(blankToNull(req.nameFr()));
        c.setAreaType(req.areaType());
        c.setAddress(blankToNull(req.address()));
        c.setManagementMode(req.managementMode());

        c.setLandStatus(req.landStatus());
        String owner = blankToNull(req.landOwnerText());
        if ((req.landStatus() == LandStatus.PROVIDED_TO_EN || req.landStatus() == LandStatus.OTHER_SECTOR) && owner == null) {
            throw rule("center.land_owner_required", "Le propriétaire / secteur doit être précisé pour cette situation foncière");
        }
        c.setLandOwnerText(owner);
        if (req.active() != null) {
            c.setActive(req.active());
        }
    }

    private void applyChildren(Center c, Request req) {
        applyBuilding(c, req);
        applySpaces(c, req);
        applyAssociations(c, req);
    }

    /**
     * Étages : « autre » impose le nombre d'étages ET la surface de chacun ; les types fixes (1, 2, 3 étages)
     * acceptent des surfaces pour les étages concernés uniquement.
     */
    private void applyBuilding(Center c, Request req) {
        List<FloorArea> floors = req.floorAreas() == null ? List.of() : req.floorAreas();
        BuildingType type = req.buildingType();

        if (type == null) {
            if (!floors.isEmpty() || req.floorsCount() != null) {
                throw rule("center.building_type_required", "Le type de bâtiment est requis pour renseigner les étages");
            }
            c.setBuildingType(null);
            c.setFloorsCount(null);
        } else {
            int expected;
            if (type == BuildingType.OTHER) {
                if (req.floorsCount() == null) {
                    throw rule("center.floors_count_required", "Le nombre d'étages est obligatoire pour « autre »");
                }
                expected = req.floorsCount();
            } else {
                expected = type.fixedFloors();
                if (req.floorsCount() != null && req.floorsCount() != expected) {
                    throw rule("center.floors_count_mismatch", "Le nombre d'étages ne correspond pas au type de bâtiment");
                }
            }
            Set<Short> numbers = new HashSet<>();
            for (FloorArea f : floors) {
                if (f.floorNumber() > expected) {
                    throw rule("center.floor_out_of_range", "Étage " + f.floorNumber() + " hors du bâtiment (" + expected + " étage(s))");
                }
                if (!numbers.add(f.floorNumber())) {
                    throw rule("center.duplicate_floor", "Étage " + f.floorNumber() + " renseigné deux fois");
                }
            }
            if (type == BuildingType.OTHER && numbers.size() != expected) {
                throw rule("center.floor_areas_incomplete", "La surface de chacun des " + expected + " étages est obligatoire");
            }
            c.setBuildingType(type);
            c.setFloorsCount(type == BuildingType.OTHER ? req.floorsCount() : null);
        }

        // Mise à jour différentielle des lignes d'étage
        Map<Short, FloorArea> wanted = floors.stream().collect(Collectors.toMap(FloorArea::floorNumber, Function.identity()));
        c.getFloorAreas().removeIf(existing -> !wanted.containsKey(existing.getFloorNumber()));
        Map<Short, CenterFloorArea> existing = c.getFloorAreas().stream()
                .collect(Collectors.toMap(CenterFloorArea::getFloorNumber, Function.identity()));
        wanted.forEach((number, f) -> {
            CenterFloorArea row = existing.get(number);
            if (row == null) {
                row = new CenterFloorArea();
                row.setCenter(c);
                row.setFloorNumber(number);
                c.getFloorAreas().add(row);
            }
            row.setAreaM2(f.areaM2());
        });
    }

    private void applySpaces(Center c, Request req) {
        List<SpaceSelection> selected = req.spaces() == null ? List.of() : req.spaces();
        Set<Long> ids = new HashSet<>();
        for (SpaceSelection s : selected) {
            if (!ids.add(s.spaceTypeId())) {
                throw rule("center.duplicate_space", "Espace sélectionné deux fois");
            }
        }
        Map<Long, SpaceType> types = spaceTypes.findAllById(ids).stream()
                .collect(Collectors.toMap(SpaceType::getId, Function.identity()));
        for (SpaceSelection s : selected) {
            SpaceType type = types.get(s.spaceTypeId());
            if (type == null) {
                throw rule("center.space_unknown", "Type d'espace inconnu : " + s.spaceTypeId());
            }
            boolean hasSpec = blankToNull(s.specification()) != null;
            if (type.isRequiresSpecification() && !hasSpec) {
                throw rule("center.space_specification_required", "Précision obligatoire pour : " + type.getNameFr());
            }
            if (!type.isRequiresSpecification() && hasSpec) {
                throw rule("center.space_specification_forbidden", "Pas de précision pour : " + type.getNameFr());
            }
        }

        c.getSpaces().removeIf(existing -> !ids.contains(existing.getSpaceType().getId()));
        Map<Long, CenterSpace> existing = c.getSpaces().stream()
                .collect(Collectors.toMap(s -> s.getSpaceType().getId(), Function.identity()));
        for (SpaceSelection s : selected) {
            CenterSpace row = existing.get(s.spaceTypeId());
            if (row == null) {
                row = new CenterSpace();
                row.setCenter(c);
                row.setSpaceType(types.get(s.spaceTypeId()));
                row.getId().setCenterId(c.getId());
                row.getId().setSpaceTypeId(s.spaceTypeId());
                c.getSpaces().add(row);
            }
            row.setSpecification(blankToNull(s.specification()));
        }
    }

    private void applyAssociations(Center c, Request req) {
        List<AssociationLink> links = req.associations() == null ? List.of() : req.associations();
        Map<Long, CenterAssociation> existing = c.getAssociations().stream()
                .collect(Collectors.toMap(CenterAssociation::getId, Function.identity()));

        Set<Long> keptIds = new HashSet<>();
        for (AssociationLink link : links) {
            if (link.id() != null) {
                if (!existing.containsKey(link.id())) {
                    throw rule("center.association_link_not_found", "Ligne d'association inconnue pour ce centre : " + link.id());
                }
                keptIds.add(link.id());
            }
        }
        c.getAssociations().removeIf(a -> !keptIds.contains(a.getId()));

        Set<Long> allPartnerIds = new HashSet<>();
        links.forEach(l -> allPartnerIds.addAll(l.partnerTypeIds() == null ? Set.of() : l.partnerTypeIds()));
        Map<Long, PartnerType> partnersById = partnerTypes.findAllById(allPartnerIds).stream()
                .collect(Collectors.toMap(PartnerType::getId, Function.identity()));

        for (AssociationLink link : links) {
            Association association = associations.findById(link.associationId())
                    .orElseThrow(() -> rule("center.association_unknown", "Association inconnue : " + link.associationId()));
            if (link.measureTaken() == PartnershipMeasure.OTHER && blankToNull(link.measureOtherText()) == null) {
                throw rule("center.measure_text_required", "Précisez la mesure « autres »");
            }
            Set<Long> partnerIds = link.partnerTypeIds() == null ? Set.of() : link.partnerTypeIds();
            for (Long pid : partnerIds) {
                if (!partnersById.containsKey(pid)) {
                    throw rule("center.partner_type_unknown", "Type de partenaire inconnu : " + pid);
                }
            }
            CenterAssociation row = link.id() != null ? existing.get(link.id()) : new CenterAssociation();
            if (link.id() == null) {
                row.setCenter(c);
                c.getAssociations().add(row);
            }
            row.setAssociation(association);
            row.setAgreementSubject(blankToNull(link.agreementSubject()));
            row.setAgreementDate(link.agreementDate());
            row.setAgreementReference(blankToNull(link.agreementReference()));
            row.setPartnershipEvaluation(link.partnershipEvaluation());
            row.setMeasureTaken(link.measureTaken());
            row.setMeasureOtherText(link.measureTaken() == PartnershipMeasure.OTHER ? blankToNull(link.measureOtherText()) : null);
            row.getPartnerTypes().clear();
            partnerIds.forEach(pid -> row.getPartnerTypes().add(partnersById.get(pid)));
        }
    }

    // =====================================================================================
    // Projection
    // =====================================================================================

    private Detail toDetail(Center c) {
        ProvincialDirectorate p = c.getProvincialDirectorate();
        List<FloorArea> floors = c.getFloorAreas().stream()
                .map(f -> new FloorArea(f.getFloorNumber(), f.getAreaM2())).toList();
        BigDecimal total = floors.stream().map(FloorArea::areaM2).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<SpaceView> spaces = c.getSpaces().stream()
                .sorted((a, b) -> Short.compare(a.getSpaceType().getPosition(), b.getSpaceType().getPosition()))
                .map(s -> new SpaceView(s.getSpaceType().getId(), s.getSpaceType().getCode(), s.getSpaceType().getNameAr(),
                        s.getSpaceType().getNameFr(), s.getSpecification())).toList();
        List<AssociationView> links = c.getAssociations().stream().map(a -> new AssociationView(
                a.getId(), a.getAssociation().getId(), a.getAssociation().getNameAr(), a.getAssociation().getNameFr(),
                a.getAgreementSubject(), a.getAgreementDate(), a.getAgreementReference(),
                a.getPartnershipEvaluation(), a.getMeasureTaken(), a.getMeasureOtherText(),
                a.getPartnerTypes().stream().sorted((x, y) -> Short.compare(x.getPosition(), y.getPosition()))
                        .map(t -> new PartnerRef(t.getId(), t.getCode(), t.getNameAr(), t.getNameFr())).toList())).toList();
        Counts counts = loadCounts(List.of(c.getId()));
        return new Detail(
                c.getId(), c.getCode(), c.getNameAr(), c.getNameFr(),
                p.getRegionalDirectorate().getId(), p.getRegionalDirectorate().getNameAr(),
                p.getId(), p.getNameAr(),
                c.getCommune().getId(), c.getCommune().getNameAr(),
                c.getAreaType(), c.getAddress(),
                c.getBuildingType(), c.getFloorsCount(), floors, total,
                c.getLandStatus(), c.getLandOwnerText(), c.getManagementMode(),
                spaces, spaces.size(), links, links.size(),
                counts.filieres.getOrDefault(c.getId(), 0L), counts.beneficiaries.getOrDefault(c.getId(), 0L),
                counts.staff.getOrDefault(c.getId(), 0L),
                c.isActive(), c.getCreatedAt(), c.getUpdatedAt(), c.getCreatedBy(), c.getUpdatedBy());
    }

    // =====================================================================================
    // Requêtes SQL d'agrégation (tables des modules suivants : pas d'entités ici)
    // =====================================================================================

    private record Counts(Map<Long, Long> filieres, Map<Long, Long> beneficiaries, Map<Long, Long> staff) {
    }

    private Counts loadCounts(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return new Counts(Map.of(), Map.of(), Map.of());
        }
        return new Counts(
                countBy("SELECT center_id, COUNT(*) FROM center_filiere WHERE center_id IN (:ids) GROUP BY center_id", ids),
                countBy("SELECT center_id, COUNT(*) FROM beneficiary WHERE center_id IN (:ids) GROUP BY center_id", ids),
                countBy("SELECT center_id, COUNT(*) FROM employee WHERE center_id IN (:ids) GROUP BY center_id", ids));
    }

    private Map<Long, Long> countBy(String sql, Collection<Long> ids) {
        Map<Long, Long> result = new HashMap<>();
        jdbc.query(sql, new MapSqlParameterSource("ids", ids),
                rs -> {
                    result.put(rs.getLong(1), rs.getLong(2));
                });
        return result;
    }

    private Set<Long> centerIdsOfFiliere(Long filiereId) {
        return new HashSet<>(jdbc.queryForList("SELECT center_id FROM center_filiere WHERE filiere_id = :f",
                new MapSqlParameterSource("f", filiereId), Long.class));
    }

    private Map<String, Long> usageOf(Long centerId) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", centerId);
        Map<String, Long> usage = new java.util.LinkedHashMap<>();
        usage.put("bénéficiaires", jdbc.queryForObject("SELECT COUNT(*) FROM beneficiary WHERE center_id = :id", p, Long.class));
        usage.put("employés", jdbc.queryForObject("SELECT COUNT(*) FROM employee WHERE center_id = :id", p, Long.class));
        usage.put("filières", jdbc.queryForObject("SELECT COUNT(*) FROM center_filiere WHERE center_id = :id", p, Long.class));
        usage.put("demandes", jdbc.queryForObject("SELECT COUNT(*) FROM filiere_request WHERE center_id = :id", p, Long.class));
        usage.values().removeIf(v -> v == 0L);
        return usage;
    }

    // =====================================================================================

    private static BusinessRuleException rule(String code, String message) {
        return new BusinessRuleException(code, message);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
