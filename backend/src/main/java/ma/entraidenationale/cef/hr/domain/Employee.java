package ma.entraidenationale.cef.hr.domain;

import java.time.LocalDate;

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
import ma.entraidenationale.cef.beneficiary.domain.EducationLevel;
import ma.entraidenationale.cef.beneficiary.domain.Gender;
import ma.entraidenationale.cef.beneficiary.domain.IdDocumentType;
import ma.entraidenationale.cef.common.AuditedEntity;
import ma.entraidenationale.cef.organization.domain.Center;
import ma.entraidenationale.cef.organization.domain.ProvincialDirectorate;

/** المستخدم / المؤطر : agent travaillant dans un centre ou à la direction provinciale. */
@Entity
@Table(name = "employee")
@Getter
@Setter
@NoArgsConstructor
public class Employee extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provincial_directorate_id", nullable = false, updatable = false)
    private ProvincialDirectorate provincialDirectorate;

    /** Vide = cadre de la direction provinciale. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "center_id")
    private Center center;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "employee_type", nullable = false, length = 30)
    private EmployeeType employeeType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "administrative_status", nullable = false, length = 30)
    private AdministrativeStatus administrativeStatus;

    @Column(name = "other_sector_text", length = 255)
    private String otherSectorText;

    @Column(name = "delegating_body", length = 255)
    private String delegatingBody;

    @Column(name = "full_name_ar", nullable = false, length = 200)
    private String fullNameAr;

    @Column(name = "full_name_fr", length = 200)
    private String fullNameFr;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 1)
    private Gender gender;

    @Column(name = "birth_date")
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

    @Column(length = 200)
    private String email;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "education_level", length = 40)
    private EducationLevel educationLevel;

    @Column(name = "diploma_title", length = 200)
    private String diplomaTitle;

    @Column(length = 200)
    private String specialty;

    @Column(name = "diploma_date")
    private LocalDate diplomaDate;

    @Column(name = "experience_description", length = 500)
    private String experienceDescription;

    @Column(name = "other_qualifications", columnDefinition = "TEXT")
    private String otherQualifications;

    // ---- معطيات حول مستخدمين التعاون الوطني (agents de l'Entraide Nationale uniquement)

    @Column(name = "en_grade", length = 100)
    private String enGrade;

    @Column(name = "en_scale", length = 20)
    private String enScale;

    @Column(name = "rank_number", length = 30)
    private String rankNumber;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "activity_status", nullable = false, length = 15)
    private ActivityStatus activityStatus = ActivityStatus.ACTIVE;
}
