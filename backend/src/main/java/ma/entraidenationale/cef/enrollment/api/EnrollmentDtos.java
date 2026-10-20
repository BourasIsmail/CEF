package ma.entraidenationale.cef.enrollment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ma.entraidenationale.cef.enrollment.domain.EnrollmentStatus;
import ma.entraidenationale.cef.enrollment.domain.ExamResult;
import ma.entraidenationale.cef.enrollment.domain.ExamStatus;
import ma.entraidenationale.cef.enrollment.domain.SessionType;

/** Contrats d'API : inscriptions, examens (bulletin), diplôme. */
public final class EnrollmentDtos {

    private EnrollmentDtos() {
    }

    // ------------------------------------------------------------------ requêtes

    /** Inscription du bénéficiaire (dans son propre centre). {@code seasonId} vide = saison courante. */
    public record EnrollmentRequest(
            @NotNull Long filiereId,
            Long seasonId,
            @NotNull LocalDate startDate,
            LocalDate examDate) {
    }

    /** Départ avant l'examen final : date et motif obligatoires. */
    public record DepartRequest(
            @NotNull LocalDate departureDate,
            @NotBlank @Size(max = 500) String reason) {
    }

    public record GradeInput(
            @NotNull Long moduleId,
            @NotNull ExamStatus status,
            @DecimalMin("0") @DecimalMax("20") BigDecimal grade) {
    }

    /**
     * Saisie d'une session (remplace la saisie précédente). Les moyennes, l'issue et la mention sont calculées
     * par le serveur. {@code passThreshold} vide = 10.
     */
    public record SessionRequest(
            @Size(max = 30) String examNumber,
            @NotNull ExamStatus finalExamStatus,
            @DecimalMin("0") @DecimalMax("20") BigDecimal finalExamGrade,
            @DecimalMin("0") @DecimalMax("20") BigDecimal passThreshold,
            Boolean exceptionalPass,
            @Size(max = 500) String exceptionalPassReason,
            List<@Valid GradeInput> grades) {
    }

    public record DiplomaRequest(
            @NotBlank @Size(max = 40) String serialNumber,
            LocalDate receivedDate,
            @Size(max = 500) String additionalInfo) {
    }

    // ------------------------------------------------------------------ réponses

    public record SessionSummary(SessionType type, ExamResult result, BigDecimal overallAverage, String mention, Boolean passed) {
    }

    public record DiplomaView(Long id, String serialNumber, LocalDate receivedDate, String additionalInfo) {
    }

    public record EnrollmentView(
            Long id, Long beneficiaryId,
            Long seasonId, String seasonLabel, Long centerId, String centerNameAr,
            Long filiereId, String filiereCode, String filiereNameAr, String filiereNameFr,
            LocalDate startDate, LocalDate examDate,
            EnrollmentStatus status, LocalDate departureDate, String departureReason,
            List<SessionSummary> sessions, DiplomaView diploma,
            Instant createdAt, Instant updatedAt) {
    }

    public record ModuleInfo(Long id, String nameAr, String nameFr, BigDecimal coefficient, int durationHours, int position) {
    }

    /** Note d'un module dans une session ({@code status} NOT_TAKEN et {@code grade} null si rien n'est saisi). */
    public record GradeView(Long moduleId, String nameAr, BigDecimal coefficient, ExamStatus status, BigDecimal grade) {
    }

    public record SessionView(
            SessionType type, String examNumber,
            List<GradeView> grades, ExamStatus finalExamStatus, BigDecimal finalExamGrade,
            BigDecimal modulesAverage, BigDecimal overallAverage, BigDecimal passThreshold,
            boolean exceptionalPass, String exceptionalPassReason, String exceptionalPassBy,
            ExamResult result, Boolean passed, String mention, Instant updatedAt, String updatedBy) {
    }

    /** بيان النقط : toutes les sessions d'une inscription, avec les modules de la filière. */
    public record Bulletin(
            Long enrollmentId, Long beneficiaryId, String beneficiaryNameAr, String beneficiaryNameFr,
            String registrationNumber, String seasonLabel, String centerNameAr, String centerNameFr,
            String filiereNameAr, String filiereNameFr,
            EnrollmentStatus status, LocalDate examDate,
            List<ModuleInfo> modules, List<SessionView> sessions,
            /** Le rattrapage peut être saisi : session normale échouée ou absente, inscription non clôturée. */
            boolean makeupAllowed, DiplomaView diploma) {
    }
}
