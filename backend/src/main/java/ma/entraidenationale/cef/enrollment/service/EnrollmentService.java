package ma.entraidenationale.cef.enrollment.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.audit.AuditService;
import ma.entraidenationale.cef.beneficiary.domain.Beneficiary;
import ma.entraidenationale.cef.common.BusinessRuleException;
import ma.entraidenationale.cef.common.ConflictException;
import ma.entraidenationale.cef.common.NotFoundException;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.DepartRequest;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.DiplomaView;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.EnrollmentRequest;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.EnrollmentView;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.SessionSummary;
import ma.entraidenationale.cef.enrollment.domain.Diploma;
import ma.entraidenationale.cef.enrollment.domain.Enrollment;
import ma.entraidenationale.cef.enrollment.domain.EnrollmentRepository;
import ma.entraidenationale.cef.enrollment.domain.EnrollmentStatus;
import ma.entraidenationale.cef.enrollment.domain.ExamResult;
import ma.entraidenationale.cef.enrollment.domain.ExamSession;
import ma.entraidenationale.cef.enrollment.domain.SessionType;
import ma.entraidenationale.cef.organization.service.CenterService;
import ma.entraidenationale.cef.training.domain.CenterFiliere;
import ma.entraidenationale.cef.training.domain.CenterFiliereRepository;
import ma.entraidenationale.cef.training.domain.Filiere;
import ma.entraidenationale.cef.training.domain.FiliereRepository;
import ma.entraidenationale.cef.training.domain.TrainingSeason;
import ma.entraidenationale.cef.training.domain.TrainingSeasonRepository;

/**
 * Inscriptions et cycle de vie : ENROLLED → DEPARTED (avec date et motif) ou → GRADUATED / FAILED / ABSENT
 * (ces trois états sont déduits des sessions d'examen, voir {@link ExamService}).
 * Périmètre : on n'accède à une inscription que si son centre est visible ; on ne la modifie que si la
 * province du centre est gérée par l'utilisateur.
 */
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    public static final String AUDIT_TYPE = "ENROLLMENT";
    public static final String ROOT_TYPE = "BENEFICIARY";

    private final EnrollmentRepository enrollments;
    private final FiliereRepository filieres;
    private final TrainingSeasonRepository seasons;
    private final CenterFiliereRepository centerFilieres;
    private final CenterService centers;
    private final AuditService audit;
    private final NamedParameterJdbcTemplate jdbc;

    // ------------------------------------------------------------------ chargement + périmètre

    public Enrollment requireVisible(Long id) {
        Enrollment e = enrollments.findWithContextById(id).orElseThrow(() -> NotFoundException.of("Inscription", id));
        centers.requireVisible(e.getCenter().getId());
        return e;
    }

    public Enrollment requireManaged(Long id) {
        Enrollment e = requireVisible(id);
        centers.requireManaged(e.getCenter().getId());
        return e;
    }

    // ------------------------------------------------------------------ lecture

    @Transactional(readOnly = true)
    public List<EnrollmentView> viewsOf(Long beneficiaryId) {
        return enrollments.findByBeneficiaryIdOrderBySeasonStartDateDesc(beneficiaryId).stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public EnrollmentView view(Long id) {
        return toView(requireVisible(id));
    }

    // ------------------------------------------------------------------ écriture

    /** Inscrit le bénéficiaire (centre de la fiche) à une filière assignée à ce centre. */
    @Transactional
    public EnrollmentView enroll(Beneficiary b, EnrollmentRequest req) {
        Filiere filiere = filieres.findById(req.filiereId())
                .orElseThrow(() -> new BusinessRuleException("enrollment.filiere_unknown", "Filière inconnue"));
        if (!filiere.isActive()) {
            throw new BusinessRuleException("enrollment.filiere_inactive", "La filière est désactivée");
        }
        CenterFiliere.Key key = new CenterFiliere.Key();
        key.setCenterId(b.getCenter().getId());
        key.setFiliereId(filiere.getId());
        if (!centerFilieres.existsById(key)) {
            throw new BusinessRuleException("enrollment.filiere_not_in_center", "Cette filière n'est pas proposée dans le centre du bénéficiaire");
        }
        TrainingSeason season = req.seasonId() == null
                ? seasons.findByCurrentTrue().orElseThrow(() -> new BusinessRuleException("enrollment.no_current_season", "Aucune saison courante n'est définie"))
                : seasons.findById(req.seasonId()).orElseThrow(() -> new BusinessRuleException("enrollment.season_unknown", "Saison inconnue"));
        if (enrollments.existsByBeneficiaryIdAndSeasonId(b.getId(), season.getId())) {
            throw new ConflictException("enrollment.exists", "Le bénéficiaire est déjà inscrit pour la saison " + season.getLabel());
        }
        checkDates(req.startDate(), req.examDate());

        Enrollment e = new Enrollment();
        e.setBeneficiary(b);
        e.setCenter(b.getCenter());
        e.setFiliere(filiere);
        e.setSeason(season);
        e.setStartDate(req.startDate());
        e.setExamDate(req.examDate());
        e = enrollments.saveAndFlush(e);
        audit.created(AUDIT_TYPE, e.getId(), ROOT_TYPE, b.getId(), snapshot(e));
        return toView(e);
    }

    @Transactional
    public EnrollmentView update(Long id, EnrollmentRequest req) {
        Enrollment e = requireManaged(id);
        if (e.getStatus() == EnrollmentStatus.DEPARTED) {
            throw new BusinessRuleException("enrollment.departed", "L'inscription est clôturée par un départ");
        }
        Map<String, Object> before = snapshot(e);
        if (!req.filiereId().equals(e.getFiliere().getId())) {
            if (!e.getSessions().isEmpty()) {
                throw new BusinessRuleException("enrollment.has_results", "La filière ne peut plus changer : des résultats existent");
            }
            Filiere filiere = filieres.findById(req.filiereId())
                    .orElseThrow(() -> new BusinessRuleException("enrollment.filiere_unknown", "Filière inconnue"));
            CenterFiliere.Key key = new CenterFiliere.Key();
            key.setCenterId(e.getCenter().getId());
            key.setFiliereId(filiere.getId());
            if (!filiere.isActive() || !centerFilieres.existsById(key)) {
                throw new BusinessRuleException("enrollment.filiere_not_in_center", "Cette filière n'est pas proposée dans le centre du bénéficiaire");
            }
            e.setFiliere(filiere);
        }
        checkDates(req.startDate(), req.examDate());
        e.setStartDate(req.startDate());
        e.setExamDate(req.examDate());
        enrollments.saveAndFlush(e);
        audit.updated(AUDIT_TYPE, e.getId(), ROOT_TYPE, e.getBeneficiary().getId(), before, snapshot(e));
        return toView(e);
    }

    /** Départ après l'inscription et avant l'examen final : date et motif obligatoires. */
    @Transactional
    public EnrollmentView depart(Long id, DepartRequest req) {
        Enrollment e = requireManaged(id);
        if (e.getStatus() != EnrollmentStatus.ENROLLED) {
            throw new BusinessRuleException("enrollment.not_enrolled", "Seule une inscription en cours peut être marquée « parti »");
        }
        if (!e.getSessions().isEmpty()) {
            throw new BusinessRuleException("enrollment.has_results", "Départ impossible : des résultats d'examen existent");
        }
        if (req.departureDate().isBefore(e.getStartDate())) {
            throw new BusinessRuleException("enrollment.departure_before_start", "La date de départ précède le début de la formation");
        }
        Map<String, Object> before = snapshot(e);
        e.setStatus(EnrollmentStatus.DEPARTED);
        e.setDepartureDate(req.departureDate());
        e.setDepartureReason(req.reason().trim());
        enrollments.saveAndFlush(e);
        audit.updated(AUDIT_TYPE, e.getId(), ROOT_TYPE, e.getBeneficiary().getId(), before, snapshot(e));
        return toView(e);
    }

    /** Annule un départ saisi par erreur. */
    @Transactional
    public EnrollmentView reinstate(Long id) {
        Enrollment e = requireManaged(id);
        if (e.getStatus() != EnrollmentStatus.DEPARTED) {
            throw new BusinessRuleException("enrollment.not_departed", "L'inscription n'est pas à l'état « parti »");
        }
        Map<String, Object> before = snapshot(e);
        e.setStatus(EnrollmentStatus.ENROLLED);
        e.setDepartureDate(null);
        e.setDepartureReason(null);
        enrollments.saveAndFlush(e);
        audit.updated(AUDIT_TYPE, e.getId(), ROOT_TYPE, e.getBeneficiary().getId(), before, snapshot(e));
        return toView(e);
    }

    /**
     * Clôture sans rattrapage : après une session normale échouée (→ FAILED) ou absente (→ ABSENT), quand le
     * bénéficiaire ne passera pas le rattrapage.
     */
    @Transactional
    public EnrollmentView finalizeWithoutMakeup(Long id) {
        Enrollment e = requireManaged(id);
        ExamSession normal = e.session(SessionType.NORMAL);
        if (e.getStatus() != EnrollmentStatus.ENROLLED || normal == null || e.session(SessionType.MAKEUP) != null) {
            throw new BusinessRuleException("enrollment.cannot_finalize", "Clôture impossible dans l'état actuel de l'inscription");
        }
        ExamResult result = ExamSessionResult.of(normal);
        EnrollmentStatus target = switch (result) {
            case FAILED -> EnrollmentStatus.FAILED;
            case ABSENT -> EnrollmentStatus.ABSENT;
            default -> throw new BusinessRuleException("enrollment.cannot_finalize",
                    "Clôture possible uniquement après une session normale échouée ou absente");
        };
        Map<String, Object> before = snapshot(e);
        e.setStatus(target);
        enrollments.saveAndFlush(e);
        audit.updated(AUDIT_TYPE, e.getId(), ROOT_TYPE, e.getBeneficiary().getId(), before, snapshot(e));
        return toView(e);
    }

    @Transactional
    public void delete(Long id) {
        Enrollment e = requireManaged(id);
        ensureDeletable(e);
        audit.deleted(AUDIT_TYPE, e.getId(), ROOT_TYPE, e.getBeneficiary().getId(), snapshot(e));
        enrollments.delete(e);
    }

    /** Supprime toutes les inscriptions d'un bénéficiaire (suppression de sa fiche) ; refusé si résultats ou diplôme. */
    @Transactional
    public void deleteAllOf(Beneficiary b) {
        for (Enrollment e : enrollments.findByBeneficiaryIdOrderBySeasonStartDateDesc(b.getId())) {
            ensureDeletable(e);
            audit.deleted(AUDIT_TYPE, e.getId(), ROOT_TYPE, b.getId(), snapshot(e));
            enrollments.delete(e);
        }
        enrollments.flush();
    }

    /** Nombre d'éléments qui empêchent la suppression d'une fiche (résultats d'examen, diplômes). */
    @Transactional(readOnly = true)
    public long resultsCountOf(Long beneficiaryId) {
        MapSqlParameterSource p = new MapSqlParameterSource("b", beneficiaryId);
        Long sessions = jdbc.queryForObject("""
                SELECT COUNT(*) FROM exam_session s JOIN enrollment e ON e.id = s.enrollment_id WHERE e.beneficiary_id = :b""", p, Long.class);
        Long diplomas = jdbc.queryForObject("""
                SELECT COUNT(*) FROM diploma d JOIN enrollment e ON e.id = d.enrollment_id WHERE e.beneficiary_id = :b""", p, Long.class);
        return sessions + diplomas;
    }

    // ------------------------------------------------------------------ statut déduit des sessions

    /** Statut d'une inscription (hors départ) d'après ses sessions : la session de rattrapage fait foi si elle existe. */
    public static EnrollmentStatus deriveStatus(Enrollment e) {
        ExamSession makeup = e.session(SessionType.MAKEUP);
        ExamSession normal = e.session(SessionType.NORMAL);
        if (makeup != null) {
            return switch (ExamSessionResult.of(makeup)) {
                case PASSED -> EnrollmentStatus.GRADUATED;
                case FAILED -> EnrollmentStatus.FAILED;
                case ABSENT -> EnrollmentStatus.ABSENT;
                case INCOMPLETE -> EnrollmentStatus.ENROLLED;
            };
        }
        if (normal != null && ExamSessionResult.of(normal) == ExamResult.PASSED) {
            return EnrollmentStatus.GRADUATED;
        }
        return EnrollmentStatus.ENROLLED; // échec ou absence en session normale : le rattrapage reste possible
    }

    // ------------------------------------------------------------------ projection / audit

    EnrollmentView toView(Enrollment e) {
        Filiere f = e.getFiliere();
        List<SessionSummary> sessions = e.getSessions().stream()
                .map(s -> new SessionSummary(s.getSessionType(), ExamSessionResult.of(s), s.getOverallAverage(), s.getMention(), s.getPassed()))
                .toList();
        Diploma d = e.getDiploma();
        DiplomaView diploma = d == null ? null : new DiplomaView(d.getId(), d.getSerialNumber(), d.getReceivedDate(), d.getAdditionalInfo());
        return new EnrollmentView(e.getId(), e.getBeneficiary().getId(),
                e.getSeason().getId(), e.getSeason().getLabel(), e.getCenter().getId(), e.getCenter().getNameAr(),
                f.getId(), f.getCode(), f.getNameAr(), f.getNameFr(),
                e.getStartDate(), e.getExamDate(), e.getStatus(), e.getDepartureDate(), e.getDepartureReason(),
                sessions, diploma, e.getCreatedAt(), e.getUpdatedAt());
    }

    public static Map<String, Object> snapshot(Enrollment e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("filiere", e.getFiliere().getCode());
        m.put("season", e.getSeason().getLabel());
        m.put("startDate", String.valueOf(e.getStartDate()));
        m.put("examDate", e.getExamDate() == null ? null : e.getExamDate().toString());
        m.put("status", e.getStatus().name());
        m.put("departureDate", e.getDepartureDate() == null ? null : e.getDepartureDate().toString());
        m.put("departureReason", e.getDepartureReason());
        return m;
    }

    private static void checkDates(LocalDate start, LocalDate exam) {
        if (exam != null && exam.isBefore(start)) {
            throw new BusinessRuleException("enrollment.exam_before_start", "La date d'examen précède le début de la formation");
        }
    }

    private static void ensureDeletable(Enrollment e) {
        if (!e.getSessions().isEmpty() || e.getDiploma() != null) {
            throw new ConflictException("enrollment.has_results", "Suppression impossible : l'inscription a des résultats ou un diplôme");
        }
    }
}
