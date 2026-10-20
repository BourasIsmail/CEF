package ma.entraidenationale.cef.enrollment.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.audit.AuditService;
import ma.entraidenationale.cef.common.BusinessRuleException;
import ma.entraidenationale.cef.common.ConflictException;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.Bulletin;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.DiplomaView;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.GradeInput;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.GradeView;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.ModuleInfo;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.SessionRequest;
import ma.entraidenationale.cef.enrollment.api.EnrollmentDtos.SessionView;
import ma.entraidenationale.cef.enrollment.domain.Enrollment;
import ma.entraidenationale.cef.enrollment.domain.EnrollmentRepository;
import ma.entraidenationale.cef.enrollment.domain.EnrollmentStatus;
import ma.entraidenationale.cef.enrollment.domain.ExamResult;
import ma.entraidenationale.cef.enrollment.domain.ExamSession;
import ma.entraidenationale.cef.enrollment.domain.ExamSessionRepository;
import ma.entraidenationale.cef.enrollment.domain.ExamStatus;
import ma.entraidenationale.cef.enrollment.domain.ModuleGrade;
import ma.entraidenationale.cef.enrollment.domain.SessionType;
import ma.entraidenationale.cef.enrollment.service.ExamCalculator.ModuleInput;
import ma.entraidenationale.cef.enrollment.service.ExamCalculator.Outcome;
import ma.entraidenationale.cef.security.CurrentUser;
import ma.entraidenationale.cef.training.domain.TrainingModule;
import ma.entraidenationale.cef.training.domain.TrainingModuleRepository;

/**
 * Examens et bulletin (بيان النقط). Le client envoie les notes ; le serveur calcule moyennes, issue, mention,
 * puis déduit le statut de l'inscription (le rattrapage reprend TOUS les examens et fait foi s'il existe).
 */
@Service
@RequiredArgsConstructor
public class ExamService {

    private static final String AUDIT_TYPE = "EXAM_SESSION";
    private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("10.00");

    private final EnrollmentService enrollmentService;
    private final EnrollmentRepository enrollments;
    private final ExamSessionRepository examSessions;
    private final TrainingModuleRepository modules;
    private final AuditService audit;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public Bulletin bulletin(Long enrollmentId) {
        return toBulletin(enrollmentService.requireVisible(enrollmentId));
    }

    /** Crée ou remplace la saisie d'une session. */
    @Transactional
    public Bulletin saveSession(Long enrollmentId, SessionType type, SessionRequest req) {
        Enrollment e = enrollmentService.requireManaged(enrollmentId);
        if (e.getStatus() == EnrollmentStatus.DEPARTED) {
            throw rule("exam.enrollment_departed", "Aucune session pour un bénéficiaire parti");
        }
        if (type == SessionType.MAKEUP) {
            ExamSession normal = e.session(SessionType.NORMAL);
            if (normal == null || !makeupAllowedAfter(ExamSessionResult.of(normal))) {
                throw rule("exam.makeup_not_allowed", "Le rattrapage n'est possible qu'après une session normale échouée ou absente");
            }
        }

        List<TrainingModule> filiereModules = modules.findByFiliereIdOrderByPosition(e.getFiliere().getId());
        Map<Long, TrainingModule> moduleById = filiereModules.stream().collect(Collectors.toMap(TrainingModule::getId, Function.identity()));
        Map<Long, GradeInput> inputs = validateInputs(req, moduleById);

        ExamSession s = e.session(type);
        Map<String, Object> before = null;
        boolean isNew = s == null;
        if (isNew) {
            s = new ExamSession();
            s.setEnrollment(e);
            s.setSessionType(type);
        } else {
            before = snapshot(s);
        }

        // --- application de la saisie
        s.setExamNumber(blankToNull(req.examNumber()));
        s.setFinalExamStatus(req.finalExamStatus());
        s.setFinalExamGrade(req.finalExamGrade());
        BigDecimal threshold = req.passThreshold() == null ? DEFAULT_THRESHOLD : req.passThreshold();
        s.setPassThreshold(threshold);
        applyGrades(s, filiereModules, inputs);

        // --- calcul serveur (d'abord sans décision exceptionnelle, pour savoir si elle est applicable)
        List<ModuleInput> calcInputs = filiereModules.stream().map(m -> {
            GradeInput g = inputs.get(m.getId());
            return new ModuleInput(m.getId(), m.getCoefficient(), g == null ? ExamStatus.NOT_TAKEN : g.status(), g == null ? null : g.grade());
        }).toList();
        Outcome plain = ExamCalculator.compute(calcInputs, req.finalExamStatus(), req.finalExamGrade(), threshold, false);

        boolean exceptional = Boolean.TRUE.equals(req.exceptionalPass());
        if (exceptional) {
            if (blankToNull(req.exceptionalPassReason()) == null) {
                throw rule("exam.exceptional_reason_required", "La décision exceptionnelle doit être justifiée");
            }
            if (!ExamCalculator.canBeExceptional(plain)) {
                throw rule("exam.exceptional_not_applicable",
                        "Une réussite exceptionnelle ne s'applique qu'à une session complète sous le seuil de réussite");
            }
        }
        Outcome outcome = exceptional
                ? ExamCalculator.compute(calcInputs, req.finalExamStatus(), req.finalExamGrade(), threshold, true)
                : plain;

        s.setExceptionalPass(exceptional);
        s.setExceptionalPassReason(exceptional ? req.exceptionalPassReason().trim() : null);
        s.setExceptionalPassBy(exceptional ? currentUser.usernameOrSystem() : null);
        s.setModulesAverage(outcome.modulesAverage());
        s.setOverallAverage(outcome.overallAverage());
        s.setPassed(outcome.passed());
        s.setMention(outcome.mention());

        if (isNew) {
            // persist explicite : un « save » sur l'inscription (déjà gérée) fusionnerait une copie et laisserait ce
            // objet sans identifiant
            s = examSessions.saveAndFlush(s);
            e.getSessions().add(s);
        }
        return persist(e, s, isNew, before);
    }

    @Transactional
    public Bulletin deleteSession(Long enrollmentId, SessionType type) {
        Enrollment e = enrollmentService.requireManaged(enrollmentId);
        ExamSession s = e.session(type);
        if (s == null) {
            throw new ma.entraidenationale.cef.common.NotFoundException("Aucune session " + type + " pour cette inscription");
        }
        if (type == SessionType.NORMAL && e.session(SessionType.MAKEUP) != null) {
            throw rule("exam.makeup_exists", "Supprimez d'abord la session de rattrapage");
        }
        audit.deleted(AUDIT_TYPE, s.getId(), EnrollmentService.ROOT_TYPE, e.getBeneficiary().getId(), snapshot(s));
        e.getSessions().remove(s);
        updateStatus(e);
        enrollments.saveAndFlush(e);
        return toBulletin(e);
    }

    // ------------------------------------------------------------------ validation / application

    private Map<Long, GradeInput> validateInputs(SessionRequest req, Map<Long, TrainingModule> moduleById) {
        if (req.finalExamStatus() == ExamStatus.TAKEN && req.finalExamGrade() == null) {
            throw rule("exam.grade_required", "La note de l'examen final est obligatoire lorsqu'il est passé");
        }
        if (req.finalExamStatus() != ExamStatus.TAKEN && req.finalExamGrade() != null) {
            throw rule("exam.grade_forbidden", "Pas de note pour un examen final absent ou non passé");
        }
        Map<Long, GradeInput> inputs = new LinkedHashMap<>();
        for (GradeInput g : req.grades() == null ? List.<GradeInput>of() : req.grades()) {
            if (!moduleById.containsKey(g.moduleId())) {
                throw rule("exam.module_unknown", "Module étranger à la filière de l'inscription : " + g.moduleId());
            }
            if (inputs.put(g.moduleId(), g) != null) {
                throw rule("exam.duplicate_module", "Module saisi deux fois : " + g.moduleId());
            }
            if (g.status() == ExamStatus.TAKEN && g.grade() == null) {
                throw rule("exam.grade_required", "Note obligatoire pour le module " + g.moduleId());
            }
            if (g.status() != ExamStatus.TAKEN && g.grade() != null) {
                throw rule("exam.grade_forbidden", "Pas de note pour un module absent ou non passé : " + g.moduleId());
            }
        }
        return inputs;
    }

    /** Mise à jour différentielle des notes ; un module absent de la saisie est considéré « non saisi ». */
    private void applyGrades(ExamSession s, List<TrainingModule> filiereModules, Map<Long, GradeInput> inputs) {
        s.getGrades().removeIf(g -> !inputs.containsKey(g.getModule().getId()));
        Map<Long, ModuleGrade> existing = s.getGrades().stream().collect(Collectors.toMap(g -> g.getModule().getId(), Function.identity()));
        for (TrainingModule m : filiereModules) {
            GradeInput in = inputs.get(m.getId());
            if (in == null) {
                continue;
            }
            ModuleGrade g = existing.get(m.getId());
            if (g == null) {
                g = new ModuleGrade();
                g.setExamSession(s);
                g.setModule(m);
                s.getGrades().add(g);
            }
            g.setExamStatus(in.status());
            g.setGrade(in.grade());
        }
    }

    private Bulletin persist(Enrollment e, ExamSession s, boolean isNew, Map<String, Object> before) {
        updateStatus(e);
        enrollments.saveAndFlush(e);
        Long root = e.getBeneficiary().getId();
        if (isNew) {
            audit.created(AUDIT_TYPE, s.getId(), EnrollmentService.ROOT_TYPE, root, snapshot(s));
        } else {
            audit.updated(AUDIT_TYPE, s.getId(), EnrollmentService.ROOT_TYPE, root, before, snapshot(s));
        }
        return toBulletin(e);
    }

    /** Recalcule le statut d'après les sessions ; refuse de quitter « diplômé » tant qu'un diplôme existe. */
    private void updateStatus(Enrollment e) {
        EnrollmentStatus target = EnrollmentService.deriveStatus(e);
        if (target == e.getStatus()) {
            return;
        }
        if (e.getStatus() == EnrollmentStatus.GRADUATED && e.getDiploma() != null) {
            throw new ConflictException("exam.diploma_issued", "Un diplôme a été émis : supprimez-le avant de modifier le résultat");
        }
        Map<String, Object> before = EnrollmentService.snapshot(e);
        e.setStatus(target);
        audit.updated(EnrollmentService.AUDIT_TYPE, e.getId(), EnrollmentService.ROOT_TYPE, e.getBeneficiary().getId(),
                before, EnrollmentService.snapshot(e));
    }

    // ------------------------------------------------------------------ projection

    private static boolean makeupAllowedAfter(ExamResult normalResult) {
        return normalResult == ExamResult.FAILED || normalResult == ExamResult.ABSENT;
    }

    private Bulletin toBulletin(Enrollment e) {
        List<TrainingModule> filiereModules = modules.findByFiliereIdOrderByPosition(e.getFiliere().getId());
        List<ModuleInfo> moduleInfos = filiereModules.stream().map(m -> new ModuleInfo(m.getId(), m.getNameAr(), m.getNameFr(),
                m.getCoefficient(), m.getDurationHours(), m.getPosition())).toList();

        List<SessionView> sessions = new ArrayList<>();
        e.getSessions().stream().sorted(Comparator.comparing(ExamSession::getSessionType)).forEach(s -> {
            Map<Long, ModuleGrade> byModule = s.getGrades().stream().collect(Collectors.toMap(g -> g.getModule().getId(), Function.identity()));
            List<GradeView> grades = filiereModules.stream().map(m -> {
                ModuleGrade g = byModule.get(m.getId());
                return new GradeView(m.getId(), m.getNameAr(), m.getCoefficient(),
                        g == null ? ExamStatus.NOT_TAKEN : g.getExamStatus(), g == null ? null : g.getGrade());
            }).toList();
            sessions.add(new SessionView(s.getSessionType(), s.getExamNumber(), grades, s.getFinalExamStatus(),
                    s.getFinalExamGrade(), s.getModulesAverage(), s.getOverallAverage(), s.getPassThreshold(),
                    s.isExceptionalPass(), s.getExceptionalPassReason(), s.getExceptionalPassBy(),
                    ExamSessionResult.of(s), s.getPassed(), s.getMention(), s.getUpdatedAt(), s.getUpdatedBy()));
        });

        ExamSession normal = e.session(SessionType.NORMAL);
        boolean makeupAllowed = e.getStatus() != EnrollmentStatus.DEPARTED && e.getDiploma() == null
                && normal != null && makeupAllowedAfter(ExamSessionResult.of(normal));
        var d = e.getDiploma();
        DiplomaView diploma = d == null ? null : new DiplomaView(d.getId(), d.getSerialNumber(), d.getReceivedDate(), d.getAdditionalInfo());
        var b = e.getBeneficiary();
        return new Bulletin(e.getId(), b.getId(), b.getFullNameAr(), b.getFullNameFr(), b.getRegistrationNumber(),
                e.getSeason().getLabel(), e.getCenter().getNameAr(), e.getCenter().getNameFr(),
                e.getFiliere().getNameAr(), e.getFiliere().getNameFr(),
                e.getStatus(), e.getExamDate(), moduleInfos, sessions, makeupAllowed, diploma);
    }

    private static Map<String, Object> snapshot(ExamSession s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionType", s.getSessionType().name());
        m.put("examNumber", s.getExamNumber());
        m.put("finalExamStatus", s.getFinalExamStatus().name());
        m.put("finalExamGrade", s.getFinalExamGrade() == null ? null : s.getFinalExamGrade().toPlainString());
        m.put("modulesAverage", s.getModulesAverage() == null ? null : s.getModulesAverage().toPlainString());
        m.put("overallAverage", s.getOverallAverage() == null ? null : s.getOverallAverage().toPlainString());
        m.put("passThreshold", s.getPassThreshold().toPlainString());
        m.put("exceptionalPass", s.isExceptionalPass());
        m.put("exceptionalPassReason", s.getExceptionalPassReason());
        m.put("passed", s.getPassed());
        m.put("mention", s.getMention());
        Set<String> seen = new HashSet<>();
        s.getGrades().stream().sorted(Comparator.comparing(g -> g.getModule().getPosition())).forEach(g -> {
            String key = "module." + g.getModule().getPosition();
            if (seen.add(key)) {
                m.put(key, g.getExamStatus() == ExamStatus.TAKEN ? g.getGrade().toPlainString() : g.getExamStatus().name());
            }
        });
        return m;
    }

    private static BusinessRuleException rule(String code, String message) {
        return new BusinessRuleException(code, message);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
