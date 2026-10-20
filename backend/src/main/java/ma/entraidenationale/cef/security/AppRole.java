package ma.entraidenationale.cef.security;

/**
 * Profils applicatifs. La première version utilise ADMIN, REGIONAL et PROVINCIAL ; les trois autres sont
 * des extensions prévues (déjà acceptées par la base).
 */
public enum AppRole {
    ADMIN, REGIONAL, PROVINCIAL, CENTER_MANAGER, HR_OFFICER, READ_ONLY;

    public String authority() {
        return "ROLE_" + name();
    }
}
