package ma.entraidenationale.cef.auth;

import ma.entraidenationale.cef.common.BusinessRuleException;

/** Règles de mot de passe : 12 à 64 caractères, au moins une lettre et un chiffre, ne contient pas l'identifiant. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 64; // bcrypt ignore / refuse au-delà de 72 octets

    private PasswordPolicy() {
    }

    public static void check(String password, String username) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw weak("Le mot de passe doit comporter entre " + MIN_LENGTH + " et " + MAX_LENGTH + " caractères");
        }
        if (password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit)) {
            throw weak("Le mot de passe doit contenir au moins une lettre et un chiffre");
        }
        if (username != null && !username.isBlank() && password.toLowerCase().contains(username.toLowerCase())) {
            throw weak("Le mot de passe ne doit pas contenir l'identifiant");
        }
    }

    private static BusinessRuleException weak(String message) {
        return new BusinessRuleException("password.weak", message);
    }
}
