package ma.entraidenationale.cef.security;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * Prédicat SQL de périmètre pour les requêtes d'agrégation (SQL natif). À utiliser SYSTÉMATIQUEMENT dans
 * toute requête qui lit des données d'un centre, afin qu'aucune ligne hors périmètre ne soit comptée.
 */
public final class ScopeSql {

    private ScopeSql() {
    }

    /**
     * @param centerAlias   alias de la table {@code center}
     * @param provinceAlias alias de la table {@code provincial_directorate}
     * @return fragment commençant par « AND », à ajouter à une clause WHERE existante
     */
    public static String centerPredicate(UserScope scope, MapSqlParameterSource params, String centerAlias, String provinceAlias) {
        if (scope.isNational()) {
            return "";
        }
        if (scope.centerId() != null) {
            params.addValue("scopeCenterId", scope.centerId());
            return " AND " + centerAlias + ".id = :scopeCenterId";
        }
        if (scope.provinceId() != null) {
            params.addValue("scopeProvinceId", scope.provinceId());
            return " AND " + centerAlias + ".provincial_directorate_id = :scopeProvinceId";
        }
        if (scope.regionId() != null) {
            params.addValue("scopeRegionId", scope.regionId());
            return " AND " + provinceAlias + ".regional_directorate_id = :scopeRegionId";
        }
        return " AND 1 = 0";
    }
}
