package ma.entraidenationale.cef.hr.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.audit.AuditEntry;
import ma.entraidenationale.cef.audit.AuditService;
import ma.entraidenationale.cef.common.BusinessRuleException;
import ma.entraidenationale.cef.common.ConflictException;
import ma.entraidenationale.cef.common.NotFoundException;
import ma.entraidenationale.cef.common.PageResponse;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.AssignmentView;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.ExtraRequest;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.ExtraView;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.Detail;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.Filter;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.GuestContractRequest;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.GuestContractView;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.Request;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.StaffSummary;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.Summary;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.TeachingRequest;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.TeachingView;
import ma.entraidenationale.cef.hr.api.EmployeeDtos.TransferRequest;
import ma.entraidenationale.cef.hr.domain.ActivityStatus;
import ma.entraidenationale.cef.hr.domain.AdministrativeStatus;
import ma.entraidenationale.cef.hr.domain.Employee;
import ma.entraidenationale.cef.hr.domain.EmployeeRepository;
import ma.entraidenationale.cef.hr.domain.EmployeeType;
import ma.entraidenationale.cef.organization.domain.Center;
import ma.entraidenationale.cef.organization.domain.ProvincialDirectorate;
import ma.entraidenationale.cef.organization.domain.ProvincialDirectorateRepository;
import ma.entraidenationale.cef.organization.service.CenterService;
import ma.entraidenationale.cef.security.AppRole;
import ma.entraidenationale.cef.security.AppUserPrincipal;
import ma.entraidenationale.cef.security.CurrentUser;
import ma.entraidenationale.cef.security.UserScope;

/**
 * Fiches des agents (ressources humaines). Périmètre : un agent est visible si sa direction provinciale (et son
 * centre pour un compte de centre) l'est, sinon 404 ; création / modification réservées à l'ADMIN et au compte
 * provincial de la province de l'agent. Fiche, affectations, enseignement et prestations sont tracés dans le
 * journal d'audit, la fiche agent en étant la racine.
 */
@Service
@RequiredArgsConstructor
public class EmployeeService {

    public static final String AUDIT_TYPE = "EMPLOYEE";
    private static final String ASSIGNMENT = "EMPLOYEE_ASSIGNMENT";
    private static final String TEACHING = "EMPLOYEE_TEACHING";
    private static final String GUEST_CONTRACT = "EMPLOYEE_GUEST_CONTRACT";

    private final EmployeeRepository employees;
    private final EmployeeQuery query;
    private final CenterService centers;
    private final ProvincialDirectorateRepository provinces;
    private final NamedParameterJdbcTemplate jdbc;
    private final CurrentUser currentUser;
    private final AuditService audit;

    // ------------------------------------------------------------------ lecture

    @Transactional(readOnly = true)
    public PageResponse<Summary> list(Filter filter, Pageable pageable) {
        return query.search(filter, pageable);
    }

    @Transactional(readOnly = true)
    public StaffSummary summary(Long regionId, Long provinceId, Long centerId) {
        return query.summary(regionId, provinceId, centerId);
    }

    @Transactional(readOnly = true)
    public Detail get(Long id) {
        return toDetail(requireVisible(id));
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> history(Long id) {
        requireVisible(id);
        return audit.historyOfRoot(AUDIT_TYPE, id, 500);
    }

    // ------------------------------------------------------------------ fiche

    @Transactional
    public Detail create(Request req) {
        Center center = req.centerId() == null ? null : centers.requireManaged(req.centerId());
        ProvincialDirectorate province = center != null
                ? checkRequestedProvince(center.getProvincialDirectorate(), req.provincialDirectorateId())
                : provinceWithoutCenter(req.provincialDirectorateId());
        checkCenterRule(req.employeeType(), center);

        Employee e = new Employee();
        e.setProvincialDirectorate(province);
        e.setCenter(center);
        apply(e, req);
        e = employees.saveAndFlush(e);
        audit.created(AUDIT_TYPE, e.getId(), AUDIT_TYPE, e.getId(), snapshot(e));

        LocalDate from = e.getHireDate() != null ? e.getHireDate() : LocalDate.now();
        insertAssignment(e, from, center, null, null);
        replaceExtras(e.getId(), req.extras());
        return toDetail(e);
    }

    @Transactional
    public Detail update(Long id, Request req) {
        Employee e = requireManaged(id);
        Long currentCenter = e.getCenter() == null ? null : e.getCenter().getId();
        if (!java.util.Objects.equals(req.centerId(), currentCenter)) {
            throw new BusinessRuleException("employee.use_transfer",
                    "Le centre d'un agent se change par une mutation (POST /transfers) afin de conserver l'historique");
        }
        if (req.provincialDirectorateId() != null && !req.provincialDirectorateId().equals(e.getProvincialDirectorate().getId())) {
            throw new BusinessRuleException("employee.province_immutable", "La direction provinciale d'un agent ne peut pas être modifiée");
        }
        checkCenterRule(req.employeeType(), e.getCenter());
        if (req.employeeType() != EmployeeType.GUEST_TRAINER && e.getEmployeeType() == EmployeeType.GUEST_TRAINER
                && count("SELECT COUNT(*) FROM employee_guest_contract WHERE employee_id = :id", id) > 0) {
            throw new ConflictException("employee.has_guest_contracts",
                    "Cet agent a des prestations de formateur invité : supprimez-les avant de changer sa صفة");
        }
        Map<String, Object> before = snapshot(e);
        apply(e, req);
        employees.saveAndFlush(e);
        audit.updated(AUDIT_TYPE, e.getId(), AUDIT_TYPE, e.getId(), before, snapshot(e));
        replaceExtras(e.getId(), req.extras());
        return toDetail(e);
    }

    @Transactional
    public void delete(Long id) {
        Employee e = requireManaged(id);
        audit.deleted(AUDIT_TYPE, e.getId(), AUDIT_TYPE, e.getId(), snapshot(e));
        employees.delete(e); // affectations, enseignement et prestations partent en cascade
    }

    // ------------------------------------------------------------------ affectations

    /** Mutation : l'affectation en cours est close la veille, une nouvelle est ouverte, la fiche suit. */
    @Transactional
    public List<AssignmentView> transfer(Long id, TransferRequest req) {
        Employee e = requireManaged(id);
        Center center = req.centerId() == null ? null : centers.requireManaged(req.centerId());
        if (center != null && !center.getProvincialDirectorate().getId().equals(e.getProvincialDirectorate().getId())) {
            throw new BusinessRuleException("employee.transfer_other_province", "La mutation doit rester dans la province de l'agent");
        }
        checkCenterRule(e.getEmployeeType(), center);
        Long previous = e.getCenter() == null ? null : e.getCenter().getId();
        if (java.util.Objects.equals(previous, req.centerId())) {
            throw new BusinessRuleException("employee.transfer_same_center", "L'agent est déjà affecté à ce centre");
        }
        LocalDate lastStart = jdbc.queryForObject("SELECT MAX(from_date) FROM employee_assignment WHERE employee_id = :id",
                new MapSqlParameterSource("id", id), LocalDate.class);
        if (lastStart != null && !req.fromDate().isAfter(lastStart)) {
            throw new BusinessRuleException("employee.transfer_date",
                    "La date d'effet doit être postérieure au début de l'affectation précédente (" + lastStart + ")");
        }
        Map<String, Object> before = snapshot(e);
        jdbc.update("UPDATE employee_assignment SET to_date = :end WHERE employee_id = :id AND to_date IS NULL",
                new MapSqlParameterSource("end", req.fromDate().minusDays(1)).addValue("id", id));
        e.setCenter(center);
        employees.saveAndFlush(e);
        insertAssignment(e, req.fromDate(), center, blankToNull(req.positionTitle()), blankToNull(req.decisionReference()));
        audit.updated(AUDIT_TYPE, e.getId(), AUDIT_TYPE, e.getId(), before, snapshot(e));
        return assignmentsOf(id);
    }

    private void insertAssignment(Employee e, LocalDate from, Center center, String position, String decision) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO employee_assignment (employee_id, from_date, provincial_directorate_id, center_id, position_title, decision_reference)
                VALUES (:emp, :from, :province, :center, :position, :decision)""",
                new MapSqlParameterSource("emp", e.getId()).addValue("from", from)
                        .addValue("province", e.getProvincialDirectorate().getId())
                        .addValue("center", center == null ? null : center.getId())
                        .addValue("position", position).addValue("decision", decision), keys);
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("fromDate", from.toString());
        snap.put("center", center == null ? null : center.getCode());
        snap.put("positionTitle", position);
        snap.put("decisionReference", decision);
        audit.created(ASSIGNMENT, keys.getKey().longValue(), AUDIT_TYPE, e.getId(), snap);
    }

    // ------------------------------------------------------------------ هيئة التكوين

    @Transactional
    public TeachingView addTeaching(Long id, TeachingRequest req) {
        Employee e = requireManaged(id);
        if (e.getEmployeeType() != EmployeeType.PEDAGOGICAL && e.getEmployeeType() != EmployeeType.GUEST_TRAINER) {
            throw new BusinessRuleException("employee.not_a_trainer", "Seuls les cadres pédagogiques et les formateurs invités enseignent");
        }
        requireSeason(req.seasonId());
        requireFiliereAtCenter(e, req.filiereId());
        if (req.moduleId() != null) {
            Integer inFiliere = jdbc.queryForObject("SELECT COUNT(*) FROM module WHERE id = :m AND filiere_id = :f",
                    new MapSqlParameterSource("m", req.moduleId()).addValue("f", req.filiereId()), Integer.class);
            if (inFiliere == 0) {
                throw new BusinessRuleException("teaching.module_not_in_filiere", "Ce module n'appartient pas à la filière");
            }
        }
        MapSqlParameterSource p = new MapSqlParameterSource("emp", id).addValue("season", req.seasonId())
                .addValue("filiere", req.filiereId()).addValue("module", req.moduleId());
        boolean wholeExists = count("""
                SELECT COUNT(*) FROM employee_teaching
                WHERE employee_id = :emp AND season_id = :season AND filiere_id = :filiere AND module_id IS NULL""", p) > 0;
        boolean modulesExist = count("""
                SELECT COUNT(*) FROM employee_teaching
                WHERE employee_id = :emp AND season_id = :season AND filiere_id = :filiere AND module_id IS NOT NULL""", p) > 0;
        if (wholeExists) {
            throw new ConflictException("teaching.already_assigned", "L'agent enseigne déjà toute cette filière pour cette saison");
        }
        if (req.moduleId() == null && modulesExist) {
            throw new ConflictException("teaching.modules_exist",
                    "L'agent enseigne déjà des modules de cette filière : retirez-les avant d'affecter toute la filière");
        }
        if (req.moduleId() != null && count("""
                SELECT COUNT(*) FROM employee_teaching
                WHERE employee_id = :emp AND season_id = :season AND filiere_id = :filiere AND module_id = :module""", p) > 0) {
            throw new ConflictException("teaching.already_assigned", "Ce module est déjà affecté à l'agent pour cette saison");
        }
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("INSERT INTO employee_teaching (employee_id, season_id, filiere_id, module_id) VALUES (:emp, :season, :filiere, :module)",
                p, keys);
        long teachingId = keys.getKey().longValue();
        TeachingView view = teachingOf(teachingId);
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("season", view.seasonLabel());
        snap.put("filiere", view.filiereNameAr());
        snap.put("module", view.moduleNameAr());
        audit.created(TEACHING, teachingId, AUDIT_TYPE, id, snap);
        return view;
    }

    @Transactional
    public void removeTeaching(Long id, Long teachingId) {
        requireManaged(id);
        TeachingView view = teachingsOf(id).stream().filter(t -> t.id().equals(teachingId)).findFirst()
                .orElseThrow(() -> NotFoundException.of("Enseignement", teachingId));
        jdbc.update("DELETE FROM employee_teaching WHERE id = :id", new MapSqlParameterSource("id", teachingId));
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("season", view.seasonLabel());
        snap.put("filiere", view.filiereNameAr());
        snap.put("module", view.moduleNameAr());
        audit.deleted(TEACHING, teachingId, AUDIT_TYPE, id, snap);
    }

    // ------------------------------------------------------------------ المكونون المستضافون

    @Transactional
    public GuestContractView addGuestContract(Long id, GuestContractRequest req) {
        Employee e = requireGuestTrainer(id);
        checkContract(e, req);
        if (count("SELECT COUNT(*) FROM employee_guest_contract WHERE employee_id = :emp AND season_id = :s AND filiere_id = :f",
                new MapSqlParameterSource("emp", id).addValue("s", req.seasonId()).addValue("f", req.filiereId())) > 0) {
            throw new ConflictException("guest_contract.exists", "Une prestation existe déjà pour cette saison et cette filière");
        }
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO employee_guest_contract (employee_id, season_id, filiere_id, diploma_year, work_start_date,
                    hourly_rate_mad, months_per_year, hours_per_month, beneficiaries_count)
                VALUES (:emp, :season, :filiere, :diplomaYear, :start, :rate, :months, :hours, :count)""",
                contractParams(id, req), keys);
        long contractId = keys.getKey().longValue();
        GuestContractView view = contractOf(contractId);
        audit.created(GUEST_CONTRACT, contractId, AUDIT_TYPE, id, contractSnapshot(view));
        return view;
    }

    @Transactional
    public GuestContractView updateGuestContract(Long id, Long contractId, GuestContractRequest req) {
        Employee e = requireGuestTrainer(id);
        GuestContractView before = guestContractsOf(id).stream().filter(c -> c.id().equals(contractId)).findFirst()
                .orElseThrow(() -> NotFoundException.of("Prestation", contractId));
        checkContract(e, req);
        if (count("""
                SELECT COUNT(*) FROM employee_guest_contract
                WHERE employee_id = :emp AND season_id = :s AND filiere_id = :f AND id <> :cid""",
                new MapSqlParameterSource("emp", id).addValue("s", req.seasonId()).addValue("f", req.filiereId())
                        .addValue("cid", contractId)) > 0) {
            throw new ConflictException("guest_contract.exists", "Une prestation existe déjà pour cette saison et cette filière");
        }
        jdbc.update("""
                UPDATE employee_guest_contract SET season_id = :season, filiere_id = :filiere, diploma_year = :diplomaYear,
                    work_start_date = :start, hourly_rate_mad = :rate, months_per_year = :months,
                    hours_per_month = :hours, beneficiaries_count = :count
                WHERE id = :cid AND employee_id = :emp""", contractParams(id, req).addValue("cid", contractId));
        GuestContractView after = contractOf(contractId);
        audit.updated(GUEST_CONTRACT, contractId, AUDIT_TYPE, id, contractSnapshot(before), contractSnapshot(after));
        return after;
    }

    @Transactional
    public void removeGuestContract(Long id, Long contractId) {
        requireGuestTrainer(id);
        GuestContractView view = guestContractsOf(id).stream().filter(c -> c.id().equals(contractId)).findFirst()
                .orElseThrow(() -> NotFoundException.of("Prestation", contractId));
        jdbc.update("DELETE FROM employee_guest_contract WHERE id = :id", new MapSqlParameterSource("id", contractId));
        audit.deleted(GUEST_CONTRACT, contractId, AUDIT_TYPE, id, contractSnapshot(view));
    }

    private void checkContract(Employee e, GuestContractRequest req) {
        requireSeason(req.seasonId());
        requireFiliereAtCenter(e, req.filiereId());
    }

    private MapSqlParameterSource contractParams(Long employeeId, GuestContractRequest req) {
        return new MapSqlParameterSource("emp", employeeId).addValue("season", req.seasonId())
                .addValue("filiere", req.filiereId()).addValue("diplomaYear", req.diplomaYear())
                .addValue("start", req.workStartDate()).addValue("rate", req.hourlyRateMad())
                .addValue("months", req.monthsPerYear()).addValue("hours", req.hoursPerMonth())
                .addValue("count", req.beneficiariesCount());
    }

    // ------------------------------------------------------------------ périmètre

    private Employee requireVisible(Long id) {
        Employee e = employees.findWithOrganizationById(id).orElseThrow(() -> NotFoundException.of("Agent", id));
        ProvincialDirectorate p = e.getProvincialDirectorate();
        UserScope scope = currentUser.scope();
        boolean inProvince = scope.seesProvince(p.getId(), p.getRegionalDirectorate().getId());
        boolean inCenter = scope.centerId() == null || (e.getCenter() != null && e.getCenter().getId().equals(scope.centerId()));
        if (!inProvince || !inCenter) {
            throw NotFoundException.of("Agent", id);
        }
        return e;
    }

    private Employee requireManaged(Long id) {
        Employee e = requireVisible(id);
        if (!currentUser.scope().managesProvince(e.getProvincialDirectorate().getId())) {
            throw new AccessDeniedException("Modification non autorisée sur cet agent");
        }
        return e;
    }

    private Employee requireGuestTrainer(Long id) {
        Employee e = requireManaged(id);
        if (e.getEmployeeType() != EmployeeType.GUEST_TRAINER) {
            throw new BusinessRuleException("employee.not_guest_trainer", "Cette fiche n'est pas celle d'un formateur invité");
        }
        return e;
    }

    private ProvincialDirectorate checkRequestedProvince(ProvincialDirectorate fromCenter, Long requested) {
        if (requested != null && !requested.equals(fromCenter.getId())) {
            throw new BusinessRuleException("employee.center_not_in_province", "Le centre n'appartient pas à la direction provinciale indiquée");
        }
        return fromCenter;
    }

    /** Cadre sans centre : province du compte provincial, ou fournie par l'administrateur. */
    private ProvincialDirectorate provinceWithoutCenter(Long requested) {
        AppUserPrincipal user = currentUser.require();
        if (user.role() == AppRole.PROVINCIAL) {
            if (requested != null && !requested.equals(user.provinceId())) {
                throw new AccessDeniedException("Un compte provincial ne gère que les agents de sa province");
            }
            return provinces.findById(user.provinceId()).orElseThrow(() -> NotFoundException.of("Province", user.provinceId()));
        }
        if (user.role() == AppRole.ADMIN) {
            if (requested == null) {
                throw new BusinessRuleException("employee.province_required", "La direction provinciale est obligatoire pour un agent sans centre");
            }
            return provinces.findById(requested).orElseThrow(() -> new BusinessRuleException("employee.province_unknown", "Province inconnue"));
        }
        throw new AccessDeniedException("Création d'agent non autorisée pour ce profil");
    }

    private void requireSeason(Long seasonId) {
        if (count("SELECT COUNT(*) FROM training_season WHERE id = :id", seasonId) == 0) {
            throw new BusinessRuleException("season.unknown", "Saison inconnue");
        }
    }

    /** La filière doit exister et, pour un agent de centre, être proposée par ce centre. */
    private void requireFiliereAtCenter(Employee e, Long filiereId) {
        if (count("SELECT COUNT(*) FROM filiere WHERE id = :id", filiereId) == 0) {
            throw new BusinessRuleException("filiere.unknown", "Filière inconnue");
        }
        if (e.getCenter() != null && count("SELECT COUNT(*) FROM center_filiere WHERE center_id = :c AND filiere_id = :f",
                new MapSqlParameterSource("c", e.getCenter().getId()).addValue("f", filiereId)) == 0) {
            throw new BusinessRuleException("filiere.not_at_center", "Cette filière n'est pas proposée par le centre de l'agent");
        }
    }

    // ------------------------------------------------------------------ règles de la fiche

    /** Coordonnateur : jamais de centre ; administratif : facultatif ; les autres : un centre est obligatoire. */
    private static void checkCenterRule(EmployeeType type, Center center) {
        if (type == EmployeeType.PEDAGOGICAL_COORDINATOR && center != null) {
            throw new BusinessRuleException("employee.coordinator_no_center", "Le coordonnateur pédagogique relève de la direction provinciale : pas de centre");
        }
        if (center == null && type != EmployeeType.PEDAGOGICAL_COORDINATOR && type != EmployeeType.ADMINISTRATIVE) {
            throw new BusinessRuleException("employee.center_required", "Le centre est obligatoire pour cette صفة");
        }
    }

    private void apply(Employee e, Request req) {
        AdministrativeStatus status = req.administrativeStatus();
        e.setEmployeeType(req.employeeType());
        e.setAdministrativeStatus(status);
        e.setOtherSectorText(status == AdministrativeStatus.OTHER_SECTOR
                ? requiredText(req.otherSectorText(), "employee.other_sector_required", "Précisez le secteur d'origine") : null);
        e.setDelegatingBody(status == AdministrativeStatus.FOREIGN_VOLUNTEER
                ? requiredText(req.delegatingBody(), "employee.delegating_body_required", "Précisez la partie déléguante du volontaire") : null);

        // données propres aux agents de l'Entraide Nationale
        boolean en = status == AdministrativeStatus.ENTRAIDE_NATIONALE;
        String rank = en ? requiredText(req.rankNumber(), "employee.rank_required", "Le numéro d'ordre est obligatoire pour un agent de l'Entraide Nationale") : null;
        if (rank != null && (e.getId() == null ? employees.existsByRankNumber(rank) : employees.existsByRankNumberAndIdNot(rank, e.getId()))) {
            throw new ConflictException("employee.rank_exists", "Le numéro d'ordre " + rank + " est déjà attribué");
        }
        e.setRankNumber(rank);
        e.setEnGrade(en ? blankToNull(req.enGrade()) : null);
        e.setEnScale(en ? blankToNull(req.enScale()) : null);

        e.setHireDate(req.hireDate());
        e.setFullNameAr(req.fullNameAr().trim());
        e.setFullNameFr(blankToNull(req.fullNameFr()));
        e.setGender(req.gender());
        e.setBirthDate(req.birthDate());
        e.setBirthPlace(blankToNull(req.birthPlace()));
        e.setIdDocumentType(req.idDocumentType());
        e.setIdDocumentNumber(blankToNull(req.idDocumentNumber()));
        e.setAddress(blankToNull(req.address()));
        e.setPhone(blankToNull(req.phone()));
        e.setEmail(blankToNull(req.email()));
        e.setEducationLevel(req.educationLevel());
        e.setDiplomaTitle(blankToNull(req.diplomaTitle()));
        e.setSpecialty(blankToNull(req.specialty()));
        e.setDiplomaDate(req.diplomaDate());
        e.setExperienceDescription(blankToNull(req.experienceDescription()));
        e.setOtherQualifications(blankToNull(req.otherQualifications()));
        e.setActivityStatus(req.activityStatus() == null ? ActivityStatus.ACTIVE : req.activityStatus());
    }

    private static String requiredText(String text, String code, String message) {
        String value = blankToNull(text);
        if (value == null) {
            throw new BusinessRuleException(code, message);
        }
        return value;
    }

    // ------------------------------------------------------------------ projection / audit

    private Detail toDetail(Employee e) {
        ProvincialDirectorate p = e.getProvincialDirectorate();
        Center c = e.getCenter();
        return new Detail(e.getId(), p.getId(), p.getNameAr(), p.getRegionalDirectorate().getId(),
                c == null ? null : c.getId(), c == null ? null : c.getNameAr(),
                e.getEmployeeType(), e.getAdministrativeStatus(), e.getOtherSectorText(), e.getDelegatingBody(),
                e.getFullNameAr(), e.getFullNameFr(), e.getGender(), e.getBirthDate(), e.getBirthPlace(),
                e.getIdDocumentType(), e.getIdDocumentNumber(), e.getAddress(), e.getPhone(), e.getEmail(),
                e.getEducationLevel(), e.getDiplomaTitle(), e.getSpecialty(), e.getDiplomaDate(),
                e.getExperienceDescription(), e.getOtherQualifications(),
                e.getEnGrade(), e.getEnScale(), e.getRankNumber(), e.getHireDate(), e.getActivityStatus(),
                assignmentsOf(e.getId()), teachingsOf(e.getId()), guestContractsOf(e.getId()), extrasOf(e.getId()),
                e.getCreatedAt(), e.getUpdatedAt(), e.getCreatedBy(), e.getUpdatedBy());
    }

    private List<ExtraView> extrasOf(Long employeeId) {
        return jdbc.query("SELECT id, label, value FROM employee_extra WHERE employee_id = :id ORDER BY position, id",
                new MapSqlParameterSource("id", employeeId),
                (rs, i) -> new ExtraView(rs.getLong("id"), rs.getString("label"), rs.getString("value")));
    }

    /** Remplace la liste des informations libres ; {@code null} laisse la liste inchangée. */
    private void replaceExtras(Long employeeId, List<ExtraRequest> extras) {
        if (extras == null) {
            return;
        }
        jdbc.update("DELETE FROM employee_extra WHERE employee_id = :id", new MapSqlParameterSource("id", employeeId));
        int position = 0;
        for (ExtraRequest x : extras) {
            jdbc.update("INSERT INTO employee_extra (employee_id, label, value, position) VALUES (:emp, :label, :value, :pos)",
                    new MapSqlParameterSource("emp", employeeId).addValue("label", x.label().trim())
                            .addValue("value", x.value() == null || x.value().isBlank() ? null : x.value().trim())
                            .addValue("pos", position++));
        }
    }

    private List<AssignmentView> assignmentsOf(Long employeeId) {
        return jdbc.query("""
                SELECT a.id, a.from_date, a.to_date, a.provincial_directorate_id, a.center_id, c.name_ar, a.position_title,
                       a.decision_reference
                FROM employee_assignment a LEFT JOIN center c ON c.id = a.center_id
                WHERE a.employee_id = :id ORDER BY a.from_date DESC, a.id DESC""",
                new MapSqlParameterSource("id", employeeId),
                (rs, i) -> new AssignmentView(rs.getLong("id"), date(rs, "from_date"), date(rs, "to_date"),
                        rs.getLong("provincial_directorate_id"), (Long) rs.getObject("center_id"), rs.getString("name_ar"),
                        rs.getString("position_title"), rs.getString("decision_reference")));
    }

    private static final String TEACHING_SELECT = """
            SELECT t.id, t.employee_id, t.season_id, s.label AS season_label, t.filiere_id, f.name_ar AS filiere_name,
                   t.module_id, m.name_ar AS module_name
            FROM employee_teaching t
            JOIN training_season s ON s.id = t.season_id
            JOIN filiere f ON f.id = t.filiere_id
            LEFT JOIN module m ON m.id = t.module_id""";

    private List<TeachingView> teachingsOf(Long employeeId) {
        return jdbc.query(TEACHING_SELECT + " WHERE t.employee_id = :id ORDER BY s.start_date DESC, f.name_ar, m.position",
                new MapSqlParameterSource("id", employeeId), EmployeeService::teachingRow);
    }

    private TeachingView teachingOf(Long teachingId) {
        return jdbc.queryForObject(TEACHING_SELECT + " WHERE t.id = :id", new MapSqlParameterSource("id", teachingId),
                EmployeeService::teachingRow);
    }

    private static TeachingView teachingRow(ResultSet rs, int i) throws SQLException {
        return new TeachingView(rs.getLong("id"), rs.getLong("season_id"), rs.getString("season_label"),
                rs.getLong("filiere_id"), rs.getString("filiere_name"), (Long) rs.getObject("module_id"), rs.getString("module_name"));
    }

    private static final String CONTRACT_SELECT = """
            SELECT g.id, g.season_id, s.label AS season_label, g.filiere_id, f.name_ar AS filiere_name, g.diploma_year,
                   g.work_start_date, g.hourly_rate_mad, g.months_per_year, g.hours_per_month, g.beneficiaries_count
            FROM employee_guest_contract g
            JOIN training_season s ON s.id = g.season_id
            JOIN filiere f ON f.id = g.filiere_id""";

    private List<GuestContractView> guestContractsOf(Long employeeId) {
        return jdbc.query(CONTRACT_SELECT + " WHERE g.employee_id = :id ORDER BY s.start_date DESC, f.name_ar",
                new MapSqlParameterSource("id", employeeId), EmployeeService::contractRow);
    }

    private GuestContractView contractOf(Long contractId) {
        return jdbc.queryForObject(CONTRACT_SELECT + " WHERE g.id = :id", new MapSqlParameterSource("id", contractId),
                EmployeeService::contractRow);
    }

    private static GuestContractView contractRow(ResultSet rs, int i) throws SQLException {
        BigDecimal rate = rs.getBigDecimal("hourly_rate_mad");
        Integer months = intOrNull(rs, "months_per_year");
        BigDecimal hours = rs.getBigDecimal("hours_per_month");
        BigDecimal annual = rate == null || months == null || hours == null ? null
                : rate.multiply(hours).multiply(BigDecimal.valueOf(months)).setScale(2, RoundingMode.HALF_UP);
        return new GuestContractView(rs.getLong("id"), rs.getLong("season_id"), rs.getString("season_label"),
                rs.getLong("filiere_id"), rs.getString("filiere_name"), intOrNull(rs, "diploma_year"),
                date(rs, "work_start_date"), rate, months, hours, intOrNull(rs, "beneficiaries_count"), annual);
    }

    private static Map<String, Object> contractSnapshot(GuestContractView v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("season", v.seasonLabel());
        m.put("filiere", v.filiereNameAr());
        m.put("diplomaYear", v.diplomaYear());
        m.put("workStartDate", v.workStartDate() == null ? null : v.workStartDate().toString());
        m.put("hourlyRateMad", v.hourlyRateMad() == null ? null : v.hourlyRateMad().toPlainString());
        m.put("monthsPerYear", v.monthsPerYear());
        m.put("hoursPerMonth", v.hoursPerMonth() == null ? null : v.hoursPerMonth().toPlainString());
        m.put("beneficiariesCount", v.beneficiariesCount());
        return m;
    }

    private static Map<String, Object> snapshot(Employee e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("province", e.getProvincialDirectorate().getCode());
        m.put("center", e.getCenter() == null ? null : e.getCenter().getCode());
        m.put("employeeType", e.getEmployeeType().name());
        m.put("administrativeStatus", e.getAdministrativeStatus().name());
        m.put("otherSectorText", e.getOtherSectorText());
        m.put("delegatingBody", e.getDelegatingBody());
        m.put("fullNameAr", e.getFullNameAr());
        m.put("fullNameFr", e.getFullNameFr());
        m.put("gender", e.getGender().name());
        m.put("birthDate", e.getBirthDate() == null ? null : e.getBirthDate().toString());
        m.put("birthPlace", e.getBirthPlace());
        m.put("idDocumentType", e.getIdDocumentType() == null ? null : e.getIdDocumentType().name());
        m.put("idDocumentNumber", e.getIdDocumentNumber());
        m.put("address", e.getAddress());
        m.put("phone", e.getPhone());
        m.put("email", e.getEmail());
        m.put("educationLevel", e.getEducationLevel() == null ? null : e.getEducationLevel().name());
        m.put("diplomaTitle", e.getDiplomaTitle());
        m.put("specialty", e.getSpecialty());
        m.put("diplomaDate", e.getDiplomaDate() == null ? null : e.getDiplomaDate().toString());
        m.put("experienceDescription", e.getExperienceDescription());
        m.put("otherQualifications", e.getOtherQualifications());
        m.put("enGrade", e.getEnGrade());
        m.put("enScale", e.getEnScale());
        m.put("rankNumber", e.getRankNumber());
        m.put("hireDate", e.getHireDate() == null ? null : e.getHireDate().toString());
        m.put("activityStatus", e.getActivityStatus().name());
        return m;
    }

    // ------------------------------------------------------------------ utilitaires

    private long count(String sql, Long id) {
        return count(sql, new MapSqlParameterSource("id", id));
    }

    private long count(String sql, MapSqlParameterSource params) {
        return jdbc.queryForObject(sql, params, Long.class);
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        java.sql.Date d = rs.getDate(column);
        return d == null ? null : d.toLocalDate();
    }

    private static Integer intOrNull(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
