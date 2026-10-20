package ma.entraidenationale.cef.enrollment.domain;

/**
 * Cycle de vie d'une inscription.
 * <ul>
 *   <li>ENROLLED  : inscrit ; reste inscrit tant que le rattrapage est possible ;</li>
 *   <li>DEPARTED  : parti après l'inscription et avant l'examen final (date et motif) ;</li>
 *   <li>GRADUATED : diplômé ; FAILED : recalé ;</li>
 *   <li>ABSENT    : absent aux examens (compté à part, jamais parmi les recalés).</li>
 * </ul>
 */
public enum EnrollmentStatus { ENROLLED, DEPARTED, GRADUATED, FAILED, ABSENT }
