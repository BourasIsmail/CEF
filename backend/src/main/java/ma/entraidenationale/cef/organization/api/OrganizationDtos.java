package ma.entraidenationale.cef.organization.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contrats d'API : régions, provinces, communes, associations, référentiels. */
public final class OrganizationDtos {

    private OrganizationDtos() {
    }

    public record Region(Long id, String code, String nameAr, String nameFr) {
    }

    public record Province(Long id, Long regionId, String code, String nameAr, String nameFr) {
    }

    public record Commune(Long id, Long provinceId, String nameAr, String nameFr) {
    }

    public record ProvinceRequest(
            @NotNull Long regionId,
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 200) String nameAr,
            @NotBlank @Size(max = 200) String nameFr) {
    }

    public record CommuneRequest(
            @NotNull Long provinceId,
            @NotBlank @Size(max = 200) String nameAr,
            @NotBlank @Size(max = 200) String nameFr) {
    }

    public record AssociationView(Long id, String nameAr, String nameFr) {
    }

    public record AssociationRequest(
            @NotBlank @Size(max = 200) String nameAr,
            @Size(max = 200) String nameFr) {
    }

    public record SpaceTypeView(Long id, String code, String nameAr, String nameFr, boolean requiresSpecification) {
    }

    public record PartnerTypeView(Long id, String code, String nameAr, String nameFr, boolean requiresSpecification) {
    }

    /** موضوع رهن الإشارة. */
    public record LandProviderView(Long id, String code, String nameAr, String nameFr, boolean requiresSpecification) {
    }
}
