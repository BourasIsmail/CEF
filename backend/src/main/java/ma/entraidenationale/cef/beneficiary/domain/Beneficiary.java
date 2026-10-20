package ma.entraidenationale.cef.beneficiary.domain;

import java.time.LocalDate;
import java.time.Period;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ma.entraidenationale.cef.common.AuditedEntity;
import ma.entraidenationale.cef.organization.domain.Center;

/** المستفيد. L'âge n'est jamais stocké ; la catégorie statistique est calculée (voir {@link StatCategory}). */
@Entity
@Table(name = "beneficiary")
@Getter
@Setter
@NoArgsConstructor
public class Beneficiary extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "center_id", nullable = false, updatable = false)
    private Center center;

    @Column(name = "rank_number", nullable = false)
    private int rankNumber;

    @Column(name = "registration_number", nullable = false, length = 30)
    private String registrationNumber;

    @Column(name = "registration_date", nullable = false)
    private LocalDate registrationDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 1)
    private Gender gender;

    @Column(name = "full_name_ar", nullable = false, length = 200)
    private String fullNameAr;

    @Column(name = "full_name_fr", length = 200)
    private String fullNameFr;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "birth_place", length = 150)
    private String birthPlace;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id_document_type", length = 30)
    private IdDocumentType idDocumentType;

    @Column(name = "id_document_number", length = 50)
    private String idDocumentNumber;

    @Column(length = 300)
    private String address;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "education_level", length = 40)
    private EducationLevel educationLevel;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "family_status", length = 10)
    private FamilyStatus familyStatus;

    @Column(name = "family_status_other_text", length = 255)
    private String familyStatusOtherText;

    @Column(name = "additional_info", columnDefinition = "TEXT")
    private String additionalInfo;

    // ---- situation sociale (multi-sélection)

    @Column(name = "is_vulnerable", nullable = false)
    private boolean vulnerable;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "disability_type", length = 20)
    private DisabilityType disabilityType;

    @Column(name = "disability_other_text", length = 255)
    private String disabilityOtherText;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "migration_status", length = 40)
    private MigrationStatus migrationStatus;

    @Column(name = "migration_other_text", length = 255)
    private String migrationOtherText;

    @Column(name = "origin_country", length = 100)
    private String originCountry;

    @Column(name = "other_social_situation_text", length = 255)
    private String otherSocialSituationText;

    public int ageAt(LocalDate date) {
        return Period.between(birthDate, date).getYears();
    }

    public StatCategory statCategory() {
        return StatCategory.of(migrationStatus, disabilityType, otherSocialSituationText);
    }
}
