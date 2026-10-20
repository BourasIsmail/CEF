package ma.entraidenationale.cef.filiererequest.domain;

/**
 * Circuit : DRAFT → SUBMITTED → REGIONAL_VALIDATED → APPROVED.
 * Depuis SUBMITTED ou REGIONAL_VALIDATED, une demande peut être renvoyée (RETURNED, à corriger puis re-soumettre)
 * ou rejetée (REJECTED, définitif). Le dépôt peut être retiré tant qu'aucune décision n'est prise (SUBMITTED → DRAFT).
 */
public enum RequestStatus { DRAFT, SUBMITTED, RETURNED, REGIONAL_VALIDATED, APPROVED, REJECTED }
