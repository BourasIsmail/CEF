package ma.entraidenationale.cef.training.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.entraidenationale.cef.common.BusinessRuleException;
import ma.entraidenationale.cef.common.ConflictException;
import ma.entraidenationale.cef.organization.domain.Center;
import ma.entraidenationale.cef.organization.service.CenterService;
import ma.entraidenationale.cef.training.api.TrainingDtos.CenterFiliereView;
import ma.entraidenationale.cef.training.domain.CenterFiliere;
import ma.entraidenationale.cef.training.domain.CenterFiliereRepository;
import ma.entraidenationale.cef.training.domain.Filiere;
import ma.entraidenationale.cef.training.domain.FiliereRepository;

/**
 * Filières assignées à un centre. La lecture suit le périmètre du centre ; l'assignation est réservée à l'ADMIN
 * (elle résulte normalement de l'approbation d'une demande de filière).
 */
@Service
@RequiredArgsConstructor
public class CenterFiliereService {

    private final CenterService centers;
    private final FiliereRepository filieres;
    private final CenterFiliereRepository centerFilieres;
    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<CenterFiliereView> list(Long centerId) {
        centers.requireVisible(centerId);
        return centerFilieres.findByIdCenterId(centerId).stream()
                .map(cf -> view(cf))
                .sorted((a, b) -> a.code().compareTo(b.code()))
                .toList();
    }

    @Transactional
    public List<CenterFiliereView> assign(Long centerId, Long filiereId) {
        Center center = centers.requireVisible(centerId);
        add(center, requireActive(filiereId));
        centerFilieres.flush();
        return list(centerId);
    }

    @Transactional
    public List<CenterFiliereView> unassign(Long centerId, Long filiereId) {
        centers.requireVisible(centerId);
        remove(centerId, filiereId);
        return list(centerId);
    }

    /** Remplace l'ensemble des filières du centre (ajouts et retraits en une opération). */
    @Transactional
    public List<CenterFiliereView> replace(Long centerId, List<Long> filiereIds) {
        Center center = centers.requireVisible(centerId);
        Set<Long> wanted = new HashSet<>(filiereIds);
        Set<Long> current = centerFilieres.findByIdCenterId(centerId).stream()
                .map(cf -> cf.getId().getFiliereId()).collect(Collectors.toSet());
        for (Long id : wanted) {
            if (!current.contains(id)) {
                add(center, requireActive(id));
            }
        }
        for (Long id : current) {
            if (!wanted.contains(id)) {
                remove(centerId, id);
            }
        }
        centerFilieres.flush();
        return list(centerId);
    }

    // ------------------------------------------------------------------

    private Filiere requireActive(Long filiereId) {
        Filiere f = filieres.findById(filiereId)
                .orElseThrow(() -> new BusinessRuleException("center_filiere.filiere_unknown", "Filière inconnue : " + filiereId));
        if (!f.isActive()) {
            throw new BusinessRuleException("center_filiere.filiere_inactive", "La filière « " + f.getCode() + " » est désactivée");
        }
        return f;
    }

    private void add(Center center, Filiere filiere) {
        CenterFiliere.Key key = new CenterFiliere.Key();
        key.setCenterId(center.getId());
        key.setFiliereId(filiere.getId());
        if (centerFilieres.existsById(key)) {
            return; // idempotent
        }
        CenterFiliere cf = new CenterFiliere();
        cf.setCenter(center);
        cf.setFiliere(filiere);
        cf.getId().setCenterId(center.getId());
        cf.getId().setFiliereId(filiere.getId());
        centerFilieres.save(cf);
    }

    private void remove(Long centerId, Long filiereId) {
        Long enrollments = jdbc.queryForObject(
                "SELECT COUNT(*) FROM enrollment WHERE center_id = :c AND filiere_id = :f",
                new MapSqlParameterSource("c", centerId).addValue("f", filiereId), Long.class);
        if (enrollments > 0) {
            throw new ConflictException("center_filiere.has_enrollments",
                    "Retrait impossible : " + enrollments + " inscription(s) existent pour cette filière dans ce centre");
        }
        CenterFiliere.Key key = new CenterFiliere.Key();
        key.setCenterId(centerId);
        key.setFiliereId(filiereId);
        centerFilieres.deleteById(key);
    }

    private static CenterFiliereView view(CenterFiliere cf) {
        Filiere f = cf.getFiliere();
        return new CenterFiliereView(f.getId(), f.getCode(), f.getNameAr(), f.getNameFr(), f.getDurationMonths(),
                f.isActive(), cf.getAssignedAt());
    }
}
