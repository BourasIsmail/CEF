package ma.entraidenationale.cef.security;

/**
 * Périmètre de données d'un utilisateur : tout (ADMIN), une région, une province ou un centre.
 * Règle : le niveau le plus fin renseigné détermine ce qui est visible ; un utilisateur non administrateur
 * sans aucun rattachement ne voit rien.
 */
public record UserScope(AppRole role, Long regionId, Long provinceId, Long centerId) {

    public boolean isNational() {
        return role == AppRole.ADMIN;
    }

    /** La région est visible : c'est la sienne (ou l'une des régions couvertes). */
    public boolean seesRegion(long regionId) {
        return isNational() || (this.regionId != null && this.regionId == regionId);
    }

    /** La province est visible : la sienne, ou une province de sa région pour un profil régional. */
    public boolean seesProvince(long provinceId, long provinceRegionId) {
        if (isNational()) {
            return true;
        }
        if (this.provinceId != null) {
            return this.provinceId == provinceId;
        }
        return this.regionId != null && this.regionId == provinceRegionId;
    }

    /** Le centre est dans le périmètre. */
    public boolean seesCenter(long centerId, long centerProvinceId, long centerRegionId) {
        if (isNational()) {
            return true;
        }
        if (this.centerId != null) {
            return this.centerId == centerId;
        }
        if (this.provinceId != null) {
            return this.provinceId == centerProvinceId;
        }
        return this.regionId != null && this.regionId == centerRegionId;
    }

    /** Peut-on créer / modifier des données dans cette province ? (ADMIN partout, PROVINCIAL dans la sienne.) */
    public boolean managesProvince(long provinceId) {
        return isNational() || (role == AppRole.PROVINCIAL && this.provinceId != null && this.provinceId == provinceId);
    }
}
