package ma.entraidenationale.cef.training.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.common.BusinessRuleException;
import ma.entraidenationale.cef.common.ConflictException;
import ma.entraidenationale.cef.common.NotFoundException;
import ma.entraidenationale.cef.security.CurrentUser;
import ma.entraidenationale.cef.security.ScopeSql;
import ma.entraidenationale.cef.security.UserScope;
import ma.entraidenationale.cef.training.api.TrainingDtos.CenterRef;
import ma.entraidenationale.cef.training.api.TrainingDtos.FiliereDetail;
import ma.entraidenationale.cef.training.api.TrainingDtos.FiliereRequest;
import ma.entraidenationale.cef.training.api.TrainingDtos.FiliereSummary;
import ma.entraidenationale.cef.training.api.TrainingDtos.ModuleView;
import ma.entraidenationale.cef.training.domain.CenterFiliereRepository;
import ma.entraidenationale.cef.training.domain.Filiere;
import ma.entraidenationale.cef.training.domain.FiliereRepository;
import ma.entraidenationale.cef.training.domain.Program;
import ma.entraidenationale.cef.training.domain.ProgramRepository;
import ma.entraidenationale.cef.training.domain.TrainingModule;
import ma.entraidenationale.cef.training.domain.TrainingModuleRepository;

/**
 * Catalogue des filières (الشعب). Lecture ouverte à tous les profils ; création / modification réservées à
 * l'ADMIN (les autres profils passent par une demande de filière, étape ultérieure).
 */
@Service
@RequiredArgsConstructor
public class FiliereService {

    private final FiliereRepository filieres;
    private final ProgramRepository programs;
    private final TrainingModuleRepository modules;
    private final CenterFiliereRepository centerFilieres;
    private final CurrentUser currentUser;
    private final NamedParameterJdbcTemplate jdbc;

    // ------------------------------------------------------------------ lecture

    @Transactional(readOnly = true)
    public Page<FiliereSummary> list(String q, Boolean active, Long programId, Pageable pageable) {
        String query = q == null || q.isBlank() ? null : q.trim();
        Page<Filiere> page = filieres.search(query, active, programId, pageable);
        List<Long> ids = page.getContent().stream().map(Filiere::getId).toList();
        Totals totals = loadTotals(ids);
        return page.map(f -> toSummary(f, totals));
    }

    @Transactional(readOnly = true)
    public FiliereDetail get(Long id) {
        Filiere f = find(id);
        Totals totals = loadTotals(List.of(id));
        UserScope scope = currentUser.scope();
        List<CenterRef> centers = centerFilieres.findByIdFiliereId(id).stream()
                .map(cf -> cf.getCenter())
                .filter(c -> scope.seesCenter(c.getId(), c.getProvincialDirectorate().getId(),
                        c.getProvincialDirectorate().getRegionalDirectorate().getId()))
                .map(c -> new CenterRef(c.getId(), c.getCode(), c.getNameAr(), c.getProvincialDirectorate().getId()))
                .sorted((a, b) -> a.code().compareTo(b.code()))
                .toList();
        List<ModuleView> moduleViews = modules.findByFiliereIdOrderByPosition(id).stream()
                .map(FiliereService::toView).toList();
        return new FiliereDetail(toSummary(f, totals), moduleViews, centers,
                f.getCreatedAt(), f.getUpdatedAt(), f.getCreatedBy(), f.getUpdatedBy());
    }

    // ------------------------------------------------------------------ écriture (ADMIN)

    @Transactional
    public FiliereDetail create(FiliereRequest req) {
        String code = req.code().trim().toUpperCase();
        if (filieres.existsByCode(code)) {
            throw new ConflictException("filiere.code_exists", "Ce code de filière existe déjà");
        }
        Filiere f = new Filiere();
        f.setCode(code);
        f.setProgram(resolveProgram(req.programId()));
        apply(f, req);
        return get(filieres.saveAndFlush(f).getId());
    }

    @Transactional
    public FiliereDetail update(Long id, FiliereRequest req) {
        Filiere f = find(id);
        String code = req.code().trim().toUpperCase();
        if (!code.equals(f.getCode()) && filieres.existsByCode(code)) {
            throw new ConflictException("filiere.code_exists", "Ce code de filière existe déjà");
        }
        f.setCode(code);
        if (req.programId() != null) {
            f.setProgram(resolveProgram(req.programId()));
        }
        apply(f, req);
        filieres.saveAndFlush(f);
        return get(id);
    }

    @Transactional
    public FiliereDetail setActive(Long id, boolean active) {
        Filiere f = find(id);
        f.setActive(active);
        filieres.saveAndFlush(f);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        Filiere f = find(id);
        Map<String, Long> usage = usageOf(id);
        if (!usage.isEmpty()) {
            throw new ConflictException("filiere.in_use",
                    "Suppression impossible : la filière est utilisée " + usage + ". Désactivez-la plutôt.");
        }
        modules.deleteAll(modules.findByFiliereIdOrderByPosition(id));
        filieres.delete(f);
    }

    // ------------------------------------------------------------------

    Filiere find(Long id) {
        return filieres.findById(id).orElseThrow(() -> NotFoundException.of("Filière", id));
    }

    private void apply(Filiere f, FiliereRequest req) {
        f.setNameAr(req.nameAr().trim());
        f.setNameFr(req.nameFr().trim());
        f.setDurationMonths(req.durationMonths().shortValue());
        if (req.active() != null) {
            f.setActive(req.active());
        }
    }

    private Program resolveProgram(Long programId) {
        if (programId == null) {
            return programs.findByCode(Program.DEFAULT_CODE)
                    .orElseThrow(() -> new IllegalStateException("Programme par défaut absent : " + Program.DEFAULT_CODE));
        }
        return programs.findById(programId)
                .orElseThrow(() -> new BusinessRuleException("filiere.program_unknown", "Programme inconnu"));
    }

    static ModuleView toView(TrainingModule m) {
        return new ModuleView(m.getId(), m.getNameAr(), m.getNameFr(), m.getCoefficient(), m.getDurationHours(), m.getPosition());
    }

    // ------------------------------------------------------------------ agrégats (SQL)

    private record Totals(Map<Long, int[]> modules, Map<Long, BigDecimal> coefficients, Map<Long, Long> centers) {
    }

    /** Nombre de modules, volume horaire, somme des coefficients et centres du périmètre, en 2 requêtes. */
    private Totals loadTotals(List<Long> ids) {
        Map<Long, int[]> mod = new HashMap<>();
        Map<Long, BigDecimal> coef = new HashMap<>();
        Map<Long, Long> centers = new HashMap<>();
        if (ids.isEmpty()) {
            return new Totals(mod, coef, centers);
        }
        jdbc.query("""
                SELECT filiere_id, COUNT(*), COALESCE(SUM(duration_hours), 0), COALESCE(SUM(coefficient), 0)
                FROM module WHERE filiere_id IN (:ids) GROUP BY filiere_id
                """, new MapSqlParameterSource("ids", ids), rs -> {
            mod.put(rs.getLong(1), new int[] {rs.getInt(2), rs.getInt(3)});
            coef.put(rs.getLong(1), rs.getBigDecimal(4));
        });

        MapSqlParameterSource p = new MapSqlParameterSource("ids", ids);
        String scope = ScopeSql.centerPredicate(currentUser.scope(), p, "c", "p");
        jdbc.query("""
                SELECT cf.filiere_id, COUNT(*)
                FROM center_filiere cf
                JOIN center c ON c.id = cf.center_id
                JOIN provincial_directorate p ON p.id = c.provincial_directorate_id
                WHERE cf.filiere_id IN (:ids)""" + scope + " GROUP BY cf.filiere_id", p,
                rs -> {
                    centers.put(rs.getLong(1), rs.getLong(2));
                });
        return new Totals(mod, coef, centers);
    }

    private FiliereSummary toSummary(Filiere f, Totals t) {
        int[] m = t.modules().getOrDefault(f.getId(), new int[] {0, 0});
        return new FiliereSummary(f.getId(), f.getProgram().getId(), f.getProgram().getCode(), f.getCode(),
                f.getNameAr(), f.getNameFr(), f.getDurationMonths(), f.isActive(),
                m[0], t.coefficients().getOrDefault(f.getId(), BigDecimal.ZERO), m[1],
                t.centers().getOrDefault(f.getId(), 0L));
    }

    private Map<String, Long> usageOf(Long filiereId) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", filiereId);
        Map<String, Long> usage = new java.util.LinkedHashMap<>();
        usage.put("inscriptions", jdbc.queryForObject("SELECT COUNT(*) FROM enrollment WHERE filiere_id = :id", p, Long.class));
        usage.put("centres", jdbc.queryForObject("SELECT COUNT(*) FROM center_filiere WHERE filiere_id = :id", p, Long.class));
        usage.put("demandes", jdbc.queryForObject(
                "SELECT COUNT(*) FROM filiere_request WHERE target_filiere_id = :id OR resulting_filiere_id = :id", p, Long.class));
        usage.put("enseignements", jdbc.queryForObject("SELECT COUNT(*) FROM employee_teaching WHERE filiere_id = :id", p, Long.class));
        usage.put("contrats", jdbc.queryForObject("SELECT COUNT(*) FROM employee_guest_contract WHERE filiere_id = :id", p, Long.class));
        usage.values().removeIf(v -> v == 0L);
        return usage;
    }
}
