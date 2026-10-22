package ma.entraidenationale.cef.organization.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ma.entraidenationale.cef.organization.domain.AreaType;
import ma.entraidenationale.cef.organization.domain.BuildingType;
import ma.entraidenationale.cef.organization.domain.LandStatus;
import ma.entraidenationale.cef.organization.domain.ManagementMode;
import ma.entraidenationale.cef.organization.domain.PartnershipEvaluation;
import ma.entraidenationale.cef.organization.domain.PartnershipMeasure;

/** Contrats d'API des centres (requêtes et réponses). */
public final class CenterDtos {

    private CenterDtos() {
    }

    // ---------------------------------------------------------------- requêtes

    /** Surface d'un étage (m²). */
    public record FloorArea(
            @NotNull @Min(1) @Max(20) Short floorNumber,
            @NotNull @DecimalMin(value = "0.01", message = "La surface doit être positive") BigDecimal areaM2) {
    }

    /** Un espace présent dans le centre ; {@code specification} uniquement pour « فضاء آخر ». */
    public record SpaceSelection(@NotNull Long spaceTypeId, @Size(max = 200) String specification) {
    }

    /** Association partenaire du centre ; {@code id} renseigné = mise à jour d'une ligne existante. */
    public record AssociationLink(
            Long id,
            @NotNull Long associationId,
            @Size(max = 300) String agreementSubject,
            LocalDate agreementDate,
            @Size(max = 100) String agreementReference,
            PartnershipEvaluation partnershipEvaluation,
            PartnershipMeasure measureTaken,
            @Size(max = 255) String measureOtherText,
            Set<Long> partnerTypeIds,
            @Size(max = 255) String partnerSpecification) {
    }

    /**
     * Création / modification d'un centre (remplacement complet des étages, espaces et associations).
     * {@code provincialDirectorateId} n'est lu que pour un ADMIN : un compte provincial crée toujours
     * dans sa propre province.
     */
    public record Request(
            Long provincialDirectorateId,
            @NotNull Long communeId,
            @Size(max = 30) String code,
            @NotBlank @Size(max = 200) String nameAr,
            @Size(max = 200) String nameFr,
            @NotNull AreaType areaType,
            @Size(max = 300) String address,
            BuildingType buildingType,
            @Min(1) @Max(20) Short floorsCount,
            @Valid List<FloorArea> floorAreas,
            LandStatus landStatus,
            Long landProviderId,
            @Size(max = 255) String landOwnerText,
            ManagementMode managementMode,
            @Valid List<SpaceSelection> spaces,
            @Valid List<AssociationLink> associations,
            Boolean active) {
    }

    /** Filtres de la liste des centres. */
    public record Filter(String search, Long regionId, Long provinceId, Long communeId, AreaType areaType,
                         ManagementMode managementMode, LandStatus landStatus, Long filiereId, Boolean active) {
    }

    // ---------------------------------------------------------------- réponses

    public record Summary(
            Long id, String code, String nameAr, String nameFr,
            Long regionalDirectorateId, Long provincialDirectorateId, String provincialDirectorateNameAr,
            Long communeId, String communeNameAr,
            AreaType areaType, LandStatus landStatus, ManagementMode managementMode, boolean active,
            long filieresCount, long beneficiariesCount, long staffCount) {
    }

    public record SpaceView(Long spaceTypeId, String code, String nameAr, String nameFr, String specification) {
    }

    public record PartnerRef(Long id, String code, String nameAr, String nameFr) {
    }

    public record AssociationView(
            Long id, Long associationId, String associationNameAr, String associationNameFr,
            String agreementSubject, LocalDate agreementDate, String agreementReference,
            PartnershipEvaluation partnershipEvaluation, PartnershipMeasure measureTaken, String measureOtherText,
            String partnerSpecification, List<PartnerRef> partners) {
    }

    public record Detail(
            Long id, String code, String nameAr, String nameFr,
            Long regionalDirectorateId, String regionalDirectorateNameAr,
            Long provincialDirectorateId, String provincialDirectorateNameAr,
            Long communeId, String communeNameAr,
            AreaType areaType, String address,
            BuildingType buildingType, Short floorsCount, List<FloorArea> floorAreas, BigDecimal totalAreaM2,
            LandStatus landStatus, Long landProviderId, String landProviderNameAr, String landProviderNameFr,
            String landOwnerText, ManagementMode managementMode,
            List<SpaceView> spaces, int spacesCount,
            List<AssociationView> associations, int associationsCount,
            long filieresCount, long beneficiariesCount, long staffCount,
            boolean active, Instant createdAt, Instant updatedAt, String createdBy, String updatedBy) {
    }
}
