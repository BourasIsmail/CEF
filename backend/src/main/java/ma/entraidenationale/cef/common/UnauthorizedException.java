package ma.entraidenationale.cef.common;

/** Authentification refusée ou expirée. HTTP 401. */
public class UnauthorizedException extends RuntimeException {

    private final String code;

    public UnauthorizedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
