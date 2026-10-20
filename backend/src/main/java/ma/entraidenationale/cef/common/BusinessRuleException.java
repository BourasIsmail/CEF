package ma.entraidenationale.cef.common;

/**
 * Règle métier non respectée (requête bien formée mais incohérente). HTTP 422.
 * Le {@code code} est stable : le frontend s'en sert pour afficher le message en arabe ou en français.
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
