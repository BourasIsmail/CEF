package ma.entraidenationale.cef.organization.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import ma.entraidenationale.cef.organization.api.CenterDtos.Filter;
import ma.entraidenationale.cef.organization.domain.Center;
import ma.entraidenationale.cef.organization.domain.CenterAssociation;
import ma.entraidenationale.cef.security.UserScope;

/** Requêtes dynamiques de la liste des centres : périmètre de l'utilisateur + filtres. */
final class CenterSpecifications {

    private static final char ESCAPE = '\\';

    private CenterSpecifications() {
    }

    /**
     * @param filiereCenterIds centres auxquels la filière demandée est assignée ({@code null} = pas de filtre)
     */
    static Specification<Center> of(Filter f, UserScope scope, Collection<Long> filiereCenterIds) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();

            Path<Long> provinceId = root.get("provincialDirectorate").get("id");
            Path<Long> regionId = root.get("provincialDirectorate").get("regionalDirectorate").get("id");

            // --- périmètre : toujours appliqué, jamais déductible du client
            if (!scope.isNational()) {
                if (scope.centerId() != null) {
                    p.add(cb.equal(root.get("id"), scope.centerId()));
                } else if (scope.provinceId() != null) {
                    p.add(cb.equal(provinceId, scope.provinceId()));
                } else if (scope.regionId() != null) {
                    p.add(cb.equal(regionId, scope.regionId()));
                } else {
                    p.add(cb.disjunction());
                }
            }

            // --- filtres
            if (f.regionId() != null) {
                p.add(cb.equal(regionId, f.regionId()));
            }
            if (f.provinceId() != null) {
                p.add(cb.equal(provinceId, f.provinceId()));
            }
            if (f.communeId() != null) {
                p.add(cb.equal(root.get("commune").get("id"), f.communeId()));
            }
            if (f.areaType() != null) {
                p.add(cb.equal(root.get("areaType"), f.areaType()));
            }
            if (f.managementMode() != null) {
                p.add(cb.equal(root.get("managementMode"), f.managementMode()));
            }
            if (f.landStatus() != null) {
                p.add(cb.equal(root.get("landStatus"), f.landStatus()));
            }
            if (f.active() != null) {
                p.add(cb.equal(root.get("active"), f.active()));
            }
            if (filiereCenterIds != null) {
                p.add(filiereCenterIds.isEmpty() ? cb.disjunction() : root.get("id").in(filiereCenterIds));
            }
            if (f.search() != null && !f.search().isBlank()) {
                String pattern = "%" + escapeLike(f.search().trim()) + "%";
                Subquery<Long> byAssociation = query.subquery(Long.class);
                Root<CenterAssociation> ca = byAssociation.from(CenterAssociation.class);
                byAssociation.select(ca.get("id")).where(
                        cb.equal(ca.get("center"), root),
                        cb.or(cb.like(ca.get("association").get("nameAr"), pattern, ESCAPE),
                              cb.like(ca.get("association").get("nameFr"), pattern, ESCAPE)));
                p.add(cb.or(
                        cb.like(root.get("code"), pattern, ESCAPE),
                        cb.like(root.get("nameAr"), pattern, ESCAPE),
                        cb.like(root.get("nameFr"), pattern, ESCAPE),
                        cb.like(root.get("commune").get("nameAr"), pattern, ESCAPE),
                        cb.exists(byAssociation)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
