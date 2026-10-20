package ma.entraidenationale.cef.enrollment.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

import ma.entraidenationale.cef.enrollment.domain.ExamResult;
import ma.entraidenationale.cef.enrollment.domain.ExamStatus;

/**
 * Calcul du résultat d'une session d'examen (classe pure, sans accès base).
 *
 * <ul>
 *   <li>moyenne des modules = Σ(note × coefficient) / Σ coefficients, sur TOUS les modules de la filière ;</li>
 *   <li>moyenne générale = (moyenne des modules + note de l'examen final) / 2 ;</li>
 *   <li>réussite si la moyenne générale arrondie à 2 décimales atteint le seuil (10 par défaut, modifiable),
 *       ou par décision exceptionnelle justifiée ;</li>
 *   <li>un module ou l'examen final « absent » donne l'issue ABSENT (jamais « échec ») ;</li>
 *   <li>tant que toutes les notes ne sont pas saisies, l'issue est INCOMPLETE et aucune moyenne n'est produite.</li>
 * </ul>
 * Les calculs intermédiaires ne sont pas arrondis : seul le résultat l'est (HALF_UP, 2 décimales).
 */
public final class ExamCalculator {

    private static final MathContext PRECISE = new MathContext(20, RoundingMode.HALF_UP);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private ExamCalculator() {
    }

    /** Un module de la filière avec son coefficient et ce qui a été saisi (NOT_TAKEN / null si rien). */
    public record ModuleInput(Long moduleId, BigDecimal coefficient, ExamStatus status, BigDecimal grade) {
    }

    public record Outcome(BigDecimal modulesAverage, BigDecimal overallAverage, ExamResult result, String mention) {

        public Boolean passed() {
            return switch (result) {
                case PASSED -> Boolean.TRUE;
                case FAILED -> Boolean.FALSE;
                default -> null;
            };
        }
    }

    public static Outcome compute(List<ModuleInput> modules, ExamStatus finalStatus, BigDecimal finalGrade,
                                  BigDecimal threshold, boolean exceptionalPass) {
        boolean anyAbsent = finalStatus == ExamStatus.ABSENT || modules.stream().anyMatch(m -> m.status() == ExamStatus.ABSENT);
        if (anyAbsent) {
            return new Outcome(null, null, ExamResult.ABSENT, null);
        }
        boolean modulesComplete = !modules.isEmpty() && modules.stream()
                .allMatch(m -> m.status() == ExamStatus.TAKEN && m.grade() != null);
        boolean finalComplete = finalStatus == ExamStatus.TAKEN && finalGrade != null;
        if (!modulesComplete || !finalComplete) {
            return new Outcome(null, null, ExamResult.INCOMPLETE, null);
        }

        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal coefficients = BigDecimal.ZERO;
        for (ModuleInput m : modules) {
            weighted = weighted.add(m.grade().multiply(m.coefficient()));
            coefficients = coefficients.add(m.coefficient());
        }
        BigDecimal modulesAverage = weighted.divide(coefficients, PRECISE);
        BigDecimal overall = modulesAverage.add(finalGrade).divide(TWO, PRECISE);

        BigDecimal overallRounded = overall.setScale(2, RoundingMode.HALF_UP);
        boolean reached = overallRounded.compareTo(threshold) >= 0;
        ExamResult result = reached || exceptionalPass ? ExamResult.PASSED : ExamResult.FAILED;
        // la mention ne récompense qu'une vraie réussite, pas une décision exceptionnelle
        String mention = reached ? MentionScale.of(overallRounded) : null;
        return new Outcome(modulesAverage.setScale(2, RoundingMode.HALF_UP), overallRounded, result, mention);
    }

    /** Indique si une décision exceptionnelle est applicable (moyenne complète mais sous le seuil). */
    public static boolean canBeExceptional(Outcome withoutException) {
        return withoutException.result() == ExamResult.FAILED;
    }
}
