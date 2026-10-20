package ma.entraidenationale.cef.common;

/** Ressource introuvable, ou hors du périmètre de l'utilisateur (même réponse : on ne révèle pas son existence). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String what, Object id) {
        return new NotFoundException(what + " introuvable : " + id);
    }
}
