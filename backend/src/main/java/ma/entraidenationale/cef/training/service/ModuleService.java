package ma.entraidenationale.cef.training.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.common.BusinessRuleException;
import ma.entraidenationale.cef.common.ConflictException;
import ma.entraidenationale.cef.common.NotFoundException;
import ma.entraidenationale.cef.training.api.TrainingDtos.ModuleRequest;
import ma.entraidenationale.cef.training.api.TrainingDtos.ModuleView;
import ma.entraidenationale.cef.training.domain.TrainingModule;
import ma.entraidenationale.cef.training.domain.TrainingModuleRepository;

/** Modules d'une filière : coefficient, volume horaire, ordre. Écriture réservée à l'ADMIN (contrôleur). */
@Service
@RequiredArgsConstructor
public class ModuleService {

    private final FiliereService filieres;
    private final TrainingModuleRepository modules;
    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<ModuleView> list(Long filiereId) {
        filieres.find(filiereId);
        return modules.findByFiliereIdOrderByPosition(filiereId).stream().map(FiliereService::toView).toList();
    }

    /** Ajoute le module en dernière position. */
    @Transactional
    public ModuleView add(Long filiereId, ModuleRequest req) {
        var filiere = filieres.find(filiereId);
        int next = modules.findByFiliereIdOrderByPosition(filiereId).stream()
                .mapToInt(TrainingModule::getPosition).max().orElse(0) + 1;
        TrainingModule m = new TrainingModule();
        m.setFiliere(filiere);
        m.setPosition((short) next);
        apply(m, req);
        return FiliereService.toView(modules.saveAndFlush(m));
    }

    @Transactional
    public ModuleView update(Long filiereId, Long moduleId, ModuleRequest req) {
        TrainingModule m = owned(filiereId, moduleId);
        apply(m, req);
        return FiliereService.toView(modules.saveAndFlush(m));
    }

    /** Supprime un module ; refusé s'il a déjà des notes ou des enseignants rattachés. Les positions sont recompactées. */
    @Transactional
    public void delete(Long filiereId, Long moduleId) {
        TrainingModule m = owned(filiereId, moduleId);
        MapSqlParameterSource p = new MapSqlParameterSource("id", moduleId);
        Long grades = jdbc.queryForObject("SELECT COUNT(*) FROM module_grade WHERE module_id = :id", p, Long.class);
        Long teaching = jdbc.queryForObject("SELECT COUNT(*) FROM employee_teaching WHERE module_id = :id", p, Long.class);
        if (grades > 0 || teaching > 0) {
            throw new ConflictException("module.in_use",
                    "Suppression impossible : le module a " + grades + " note(s) et " + teaching + " enseignant(s) rattaché(s)");
        }
        modules.delete(m);
        modules.flush();
        List<Long> remaining = modules.findByFiliereIdOrderByPosition(filiereId).stream().map(TrainingModule::getId).toList();
        renumber(filiereId, remaining);
    }

    /** Applique un nouvel ordre : la liste doit contenir exactement les modules de la filière. */
    @Transactional
    public List<ModuleView> reorder(Long filiereId, List<Long> orderedIds) {
        filieres.find(filiereId);
        Set<Long> current = modules.findByFiliereIdOrderByPosition(filiereId).stream()
                .map(TrainingModule::getId).collect(Collectors.toSet());
        if (new HashSet<>(orderedIds).size() != orderedIds.size() || !current.equals(new HashSet<>(orderedIds))) {
            throw new BusinessRuleException("module.reorder_mismatch",
                    "La liste doit contenir exactement une fois chaque module de la filière");
        }
        renumber(filiereId, orderedIds);
        return list(filiereId);
    }

    // ------------------------------------------------------------------

    /** (filiere_id, position) est unique : on passe par des positions temporaires pour permuter sans collision. */
    private void renumber(Long filiereId, List<Long> orderedIds) {
        Map<Long, TrainingModule> byId = modules.findByFiliereIdOrderByPosition(filiereId).stream()
                .collect(Collectors.toMap(TrainingModule::getId, Function.identity()));
        List<TrainingModule> ordered = new ArrayList<>();
        orderedIds.forEach(id -> ordered.add(byId.get(id)));
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setPosition((short) (1000 + i));
        }
        modules.flush();
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setPosition((short) (i + 1));
        }
        modules.flush();
    }

    private TrainingModule owned(Long filiereId, Long moduleId) {
        TrainingModule m = modules.findById(moduleId).orElseThrow(() -> NotFoundException.of("Module", moduleId));
        if (!m.getFiliere().getId().equals(filiereId)) {
            throw NotFoundException.of("Module", moduleId);
        }
        return m;
    }

    private static void apply(TrainingModule m, ModuleRequest req) {
        m.setNameAr(req.nameAr().trim());
        m.setNameFr(req.nameFr() == null || req.nameFr().isBlank() ? null : req.nameFr().trim());
        m.setCoefficient(req.coefficient());
        m.setDurationHours(req.durationHours().shortValue());
    }
}
