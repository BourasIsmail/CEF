package ma.entraidenationale.cef.common;

/** Paramètre de requête invalide (tri inconnu, valeur hors plage...). HTTP 400. */
public class BadRequestException extends RuntimeException {

    private final String code;

    public BadRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
