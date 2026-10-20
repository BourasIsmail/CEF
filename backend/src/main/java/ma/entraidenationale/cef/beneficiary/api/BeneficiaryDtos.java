package ma.entraidenationale.cef.beneficiary.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import ma.entraidenationale.cef.beneficiary.domain.DisabilityType;
import ma.entraidenationale.cef.beneficiary.domain.EducationLevel;
import ma.entraidenationale.cef.beneficiary.domain.FamilyStatus;
import ma.entraidenationale.cef.beneficiary.domain.Gender;
import ma.entraidenationale.cef.beneficiary.domain.IdDocumentType;
import ma.entraidenationale.cef.beneficiary.domain.MigrationStatus;
import ma.entraidenationale.cef.beneficiary.domain.StatCategory;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.EnrollmentRequest;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.EnrollmentView;
import ma.entraidenationale.cef.enrollment.domain.EnrollmentStatus;

/** Contrats d'API des bénéficiaires. */
public final class BeneficiaryDtos {

    private BeneficiaryDtos() {
    }

    /**
     * Création / modification d'une fiche. Le centre est fixé à la création (non modifiable ensuite).
     * {@code rankNumber} et {@code registrationNumber} sont attribués automatiquement s'ils sont absents.
     * Situation sociale à choix multiple : {@code vulnerable}, {@code disabilityType}, {@code migrationStatus},
     * {@code otherSocialSituation} (renseigné = la personne appartient à la catégorie).
     * {@code enrollment} (création uniquement) inscrit aussitôt le bénéficiaire à une filière.
     */
    public record Request(
            @NotNull Long centerId,
            @Positive Integer rankNumber,
            @Size(max = 30) String registrationNumber,
            @NotNull LocalDate registrationDate,
            @NotNull Gender gender,
            @NotBlank @Size(max = 200) String fullNameAr,
            @Size(max = 200) String fullNameFr,
            @NotNull @Past LocalDate birthDate,
            @Size(max = 150) String birthPlace,
            IdDocumentType idDocumentType,
            @Size(max = 50) String idDocumentNumber,
            @Size(max = 300) String address,
            @Size(max = 30) String phone,
            EducationLevel educationLevel,
            FamilyStatus familyStatus,
            @Size(max = 255) String familyStatusOtherText,
            String additionalInfo,
            Boolean vulnerable,
            DisabilityType disabilityType,
            @Size(max = 255) String disabilityOtherText,
            MigrationStatus migrationStatus,
            @Size(max = 255) String migrationOtherText,
            @Size(max = 100) String originCountry,
            @Size(max = 255) String otherSocialSituation,
            @Valid EnrollmentRequest enrollment) {
    }

    /** Filtres de la liste. {@code seasonId} vide = saison courante (filtre aussi la filière et le statut). */
    public record Filter(
            String search, Long regionId, Long provinceId, Long centerId, Long filiereId, Long seasonId,
            EnrollmentStatus status, StatCategory category, Gender gender, Boolean vulnerable,
            Boolean withoutEnrollment) {
    }

    public record Summary(
            Long id, Long centerId, String centerNameAr, int rankNumber, String registrationNumber,
            LocalDate registrationDate, Gender gender, String fullNameAr, String fullNameFr, int age,
            StatCategory category, boolean vulnerable,
            Long enrollmentId, Long filiereId, String filiereNameAr, Long seasonId, EnrollmentStatus status) {
    }

    public record Detail(
            Long id, Long centerId, String centerNameAr, Long provincialDirectorateId, Long regionalDirectorateId,
            int rankNumber, String registrationNumber, LocalDate registrationDate, Gender gender,
            String fullNameAr, String fullNameFr, LocalDate birthDate, int age, String birthPlace,
            IdDocumentType idDocumentType, String idDocumentNumber, String address, String phone,
            EducationLevel educationLevel, FamilyStatus familyStatus, String familyStatusOtherText, String additionalInfo,
            boolean vulnerable, DisabilityType disabilityType, String disabilityOtherText,
            MigrationStatus migrationStatus, String migrationOtherText, String originCountry,
            String otherSocialSituation, StatCategory category,
            List<EnrollmentView> enrollments,
            Instant createdAt, Instant updatedAt, String createdBy, String updatedBy) {
    }
}
