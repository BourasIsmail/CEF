package ma.entraidenationale.cef.common;

/** Conflit avec l'état actuel des données (doublon, ressource encore utilisée...). HTTP 409. */
public class ConflictException extends RuntimeException {

    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
